package com.lrenyi.template.flow.storage;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.internal.FlowEgressHandler;
import com.lrenyi.template.flow.internal.FlowFinalizer;
import com.lrenyi.template.flow.internal.FlowLauncher;
import com.lrenyi.template.flow.internal.MatchedPairProcessor;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.model.PreRetryResult;
import com.lrenyi.template.flow.resource.ActiveLauncherLookup;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 受控超时的有界存储实现，替代旧的基于 TTL/maximumSize 自动驱逐模型。
 *
 * @param <T> 存储的数据类型
 */
@Slf4j
public final class BoundedTimedFlowStorage<T> extends AbstractEgressFlowStorage<T> implements FlowStorage<T> {

    private static final int STRIPE_COUNT = 256;
    private static final Lock[] KEY_STRIPES = new Lock[STRIPE_COUNT];

    static {
        for (int i = 0; i < STRIPE_COUNT; i++) {
            KEY_STRIPES[i] = new ReentrantLock();
        }
    }

    @Override
    public PreRetryResult preRetry(String key,
            FlowEntry<T> entry,
            FlowLauncher<Object> launcher) {
        // Slot 级实现的 preRetry 当前不做额外匹配优化，直接让上层走 requeue 流程
        return PreRetryResult.PROCEED_TO_REQUEUE;
    }

    @Override
    public boolean tryRequeue(FlowEntry<T> entry) {
        // 重入时不再单独获取 globalStorage，由 FlowLauncher 在重入路径上统一控制
        return doDeposit(entry);
    }

    private final Map<String, FlowSlot<T>> slotByKey = new ConcurrentHashMap<>();
    private final DelayQueueExpiryIndex expiryIndex = new DelayQueueExpiryIndex();
    
    private final LongAdder savedEntryCount = new LongAdder();
    private final EvictionCoordinator evictionCoordinator;
    private final TemplateConfigProperties.Flow.PerJob perJob;
    private final String jobId;
    private final int maxPerKey;
    private final Clock clock;
    private final MatchedPairProcessor<T> matchedPairProcessor;

    public BoundedTimedFlowStorage(TemplateConfigProperties.Flow flowConfig,
            FlowJoiner<T> joiner,
            ProgressTracker progressTracker,
            FlowFinalizer<T> finalizer,
            FlowEgressHandler<T> egressHandler,
            FlowResourceRegistry resourceRegistry,
            MeterRegistry meterRegistry,
            String jobId) {
        super(joiner, finalizer, progressTracker, meterRegistry, egressHandler);
        this.perJob = flowConfig.getLimits().getPerJob();
        this.maxPerKey = perJob.getKeyedCache().getEffectiveMultiValueMaxPerKey();
        this.jobId = jobId;
        this.clock = Clock.systemUTC();
        this.matchedPairProcessor = new MatchedPairProcessor<>(joiner, egressHandler, meterRegistry, resourceRegistry);
        this.evictionCoordinator = new EvictionCoordinator(expiryIndex, this, "app-template-flow-eviction-" + jobId);
        this.evictionCoordinator.start();
        
        Gauge.builder(FlowMetricNames.LIMITS_STORAGE_USED, this, BoundedTimedFlowStorage::size)
             .tag(FlowMetricNames.TAG_JOB_ID, jobId)
             .tag(FlowMetricNames.TAG_STORAGE_TYPE, "bounded")
             .description("每 Job 存储当前 entry 数")
             .register(meterRegistry);
        Gauge.builder(FlowMetricNames.LIMITS_STORAGE_LIMIT, this::entryLimit)
             .tag(FlowMetricNames.TAG_JOB_ID, jobId)
             .tag(FlowMetricNames.TAG_STORAGE_TYPE, "bounded")
             .description("每 Job 存储 entry 上限")
             .register(meterRegistry);
    }

    private static Lock stripeFor(String key) {
        return KEY_STRIPES[(key.hashCode() & 0x7FFFFFFF) % STRIPE_COUNT];
    }

    @Override
    public boolean doDeposit(FlowEntry<T> entry) {
        String key = joiner().joinKey(entry.getData());
        Lock stripe = stripeFor(key);
        stripe.lock();
        try {
            Function<String, FlowSlot<T>> slotFunction = k -> {
                TemplateConfigProperties.Flow.KeyedCache keyedCache = perJob.getKeyedCache();
                TemplateConfigProperties.Flow.MultiValueOverflowPolicy policy;
                policy = keyedCache.getMultiValueOverflowPolicy();
                return new FlowSlot<>(key, maxPerKey, policy, clock.millis());
            };
            FlowSlot<T> slot = slotByKey.computeIfAbsent(key, slotFunction);

            boolean needMatched = joiner().needMatched();
            boolean deposited;
            if (needMatched) {
                deposited = handleMatchingMode(key, slot, entry);
            } else {
                deposited = handleOverwriteModeLocked(key, slot, entry);
            }
            if (!deposited) {
                return false;
            }
            savedEntryCount.increment();
            updateSlotExpiryMetadata(slot);
            enqueueSlotExpiryIfNeeded(slot);
            return true;
        } finally {
            stripe.unlock();
        }
    }

    @Override
    public long size() {
        return savedEntryCount.sum();
    }

    @Override
    public long maxCacheSize() {
        return perJob.getStorageCapacity();
    }
    
    @Override
    public long entryLimit() {
        return perJob.getStorageCapacity();
    }

    @Override
    public boolean supportsDeferredExpiry() {
        return true;
    }
    
    @Override
    public void shutdown() {
        evictionCoordinator.close();
        // 将剩余数据以 SHUTDOWN 原因离库
        for (Map.Entry<String, FlowSlot<T>> e : slotByKey.entrySet()) {
            String key = e.getKey();
            Lock stripe = stripeFor(key);
            stripe.lock();
            try {
                FlowSlot<T> slot = slotByKey.remove(key);
                if (slot == null) {
                    continue;
                }
                List<FlowEntry<T>> entries = new ArrayList<>();
                for (FlowEntry<T> entry : slot.entries()) {
                    entries.add(entry);
                }
                for (FlowEntry<T> entry : entries) {
                    savedEntryCount.decrement();
                    handleEgress(key, entry, EgressReason.SHUTDOWN, true);
                    resourceRegistry().releaseGlobalStorage(1);
                }
            } finally {
                stripe.unlock();
            }
        }
        expiryIndex.clear();
    }
    
    public void drainExpiredEntries(String slotId) {
        FlowSlot<T> slot = slotByKey.get(slotId);
        if (slot == null || slot.isEmpty()) {
            return;
        }
        if (slot.isPairingInProgress()) {
            if (log.isTraceEnabled()) {
                log.trace("[{}] skip eviction for slot {}, pairing in progress", jobId, slotId);
            }
            return;
        }
        List<FlowEntry<T>> expired = collectExpired(slot);
        // 驱逐先触发、配对后触发：在真正执行驱逐前再次检查，若已进入配对则中止本次驱逐并重新排队
        if (slot.isPairingInProgress()) {
            if (log.isTraceEnabled()) {
                log.trace("[{}] abort eviction for slot {}, pairing started before drain", jobId, slotId);
            }
            requeueSlotExpiry(slot);
            return;
        }
        if (!joiner().needMatched()) {
            for (FlowEntry<T> entry : expired) {
                savedEntryCount.decrement();
                resourceRegistry().releaseGlobalStorage(1);
                handleEgress(slotId, entry, EgressReason.TIMEOUT, true);
            }
            return;
        }
        processEvictedSlot(slotId, expired);
    }

    /**
     * 驱逐槽位全量配对：每条 entry 与其余所有 entry 尝试匹配；
     * 若有至少一对配对成功，则未匹配条目的重入标志重置为 -1。未匹配的再按 reason 走 handleEgress。
     */
    private void processEvictedSlot(String key, List<FlowEntry<T>> entries) {
        int n = entries.size();
        if (n == 0) {
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("驱逐槽位 {} 全量配对，reason={}, 共 {} 条 entry", key, EgressReason.TIMEOUT, n);
        }
        boolean[] processed = new boolean[n];
        List<FlowEntry<T>> unmatched = new ArrayList<>(n);
        boolean hasAnyPairSucceeded = false;
        ActiveLauncherLookup launcherLookup = resourceRegistry().getLauncherLookup();
        FlowLauncher<Object> launcher = null;
        FlowEntry<T> first = entries.getFirst();
        if (launcherLookup != null && first != null) {
            launcher = launcherLookup.getActiveLauncher(first.getJobId());
        }
        boolean multiMatchEnabled = perJob.isPairingMultiMatchEnabled();

        for (int i = 0; i < n; i++) {
            if (processed[i]) {
                continue;
            }
            FlowEntry<T> x = entries.get(i);
            FlowEntry<T> matched = null;
            int matchedIdx = -1;
            for (int j = 0; j < n; j++) {
                if (i == j || processed[j]) {
                    continue;
                }
                FlowEntry<T> y = entries.get(j);
                if (joiner().isMatched(x.getData(), y.getData())) {
                    matched = y;
                    matchedIdx = j;
                    break;
                }
            }
            processed[i] = true;
            if (matched != null && launcher != null) {
                processed[matchedIdx] = true;
                hasAnyPairSucceeded = true;
                resourceRegistry().releaseGlobalStorage(1);
                savedEntryCount.decrement();
                savedEntryCount.decrement();
                matchedPairProcessor.processMatchedPair(x, matched, launcher);
                if (!multiMatchEnabled) {
                    break;
                }
            } else {
                unmatched.add(x);
            }
        }
        if (hasAnyPairSucceeded && !multiMatchEnabled) {
            for (int i = 0; i < n; i++) {
                if (!processed[i]) {
                    FlowEntry<T> e = entries.get(i);
                    unmatched.add(e);
                }
            }
        }
        if (hasAnyPairSucceeded) {
            for (FlowEntry<T> e : unmatched) {
                e.resetRetryToIneligible();
            }
        }
        boolean skipRetry = hasAnyPairSucceeded;
        for (FlowEntry<T> e : unmatched) {
            resourceRegistry().releaseGlobalStorage(1);
            savedEntryCount.decrement();
            handleEgress(key, e, EgressReason.TIMEOUT, skipRetry);
        }
    }
    
    /** 按 key 计算过期点：以 slot 首次写入时间为起点 + TTL。 */
    private void updateSlotExpiryMetadata(FlowSlot<T> slot) {
        long at = slot.getEarliestStoredAtEpochMs();
        long timeoutMs = perJob.getKeyedCache().getEffectiveTimeoutMill();
        slot.setEarliestExpireAt(at + timeoutMs);
    }
    
    /**
     * 将 slot 重新加入过期队列（用于驱逐因「配对已开始」而中止时，稍后再次尝试驱逐）。
     */
    private void requeueSlotExpiry(FlowSlot<T> slot) {
        slot.setQueuedForExpiry(false);
        enqueueSlotExpiryIfNeeded(slot);
    }
    
    private void enqueueSlotExpiryIfNeeded(FlowSlot<T> slot) {
        if (slot.isQueuedForExpiry()) {
            if (log.isTraceEnabled()) {
                log.trace("[{}] slot already queued for expiry, slotId={}", jobId, slot.getSlotId());
            }
            return;
        }
        long nextCheckAt = slot.getEarliestExpireAt();
        slot.setNextCheckAt(nextCheckAt);
        slot.setQueuedForExpiry(true);
        expiryIndex.schedule(new SlotExpiryToken(slot.getSlotId(), toSystemTimeForToken(nextCheckAt)));
        if (log.isDebugEnabled()) {
            log.debug("[{}] scheduled expiry token, slotId={}, nextCheckAt={}, delayMs={}",
                      jobId,
                      slot.getSlotId(),
                      nextCheckAt,
                      nextCheckAt - clock.millis()
            );
        }
    }
    
    private boolean handleMatchingMode(String key, FlowSlot<T> slot, FlowEntry<T> incoming) {
        // 双流配对模式：优先尝试从当前槽位中找 partner，找不到再写入
        FlowJoiner<T> joiner = joiner();
        slot.setPairingInProgress(true);
        boolean saved = false;
        try {
            AtomicReference<FlowEntry<T>> parentEntry = getParentReference(slot, incoming, joiner);
            if (parentEntry.get() != null) {
                slot.remove(parentEntry.get());
                if (!perJob.isPairingMultiMatchEnabled()) {
                    slot.entries().forEach(entry -> {
                        handleEgress(key, entry, EgressReason.CLEARED_AFTER_PAIR_SUCCESS, true);
                        resourceRegistry().releaseGlobalStorage(1);
                        savedEntryCount.decrement();
                    });
                }
            } else {
                slot.append(incoming);
                saved = true;
            }
        } finally {
            slot.setPairingInProgress(false);
        }
        return saved;
    }
    
    private AtomicReference<FlowEntry<T>> getParentReference(FlowSlot<T> slot,
            FlowEntry<T> incoming,
            FlowJoiner<T> joiner) {
        Iterable<FlowEntry<T>> entries = slot.entries();
        AtomicReference<FlowEntry<T>> parentEntry = new AtomicReference<>();
        entries.forEach(parent -> {
            boolean matched = joiner.isMatched(parent.getData(), incoming.getData());
            if (!matched) {
                return;
            }
            FlowLauncher<Object> launcher = resourceRegistry().getLauncherLookup().getActiveLauncher(jobId);
            matchedPairProcessor.processMatchedPair(parent, incoming, launcher);
            resourceRegistry().releaseGlobalStorage(1);
            savedEntryCount.decrement();
            parentEntry.set(parent);
        });
        return parentEntry;
    }
    
    private boolean handleOverwriteModeLocked(String key, FlowSlot<T> slot, FlowEntry<T> entry) {
        // 单值模式：最新写入覆盖旧值，旧值以 REPLACE 原因离库
        boolean multiValue = perJob.getKeyedCache().isMultiValueEnabled();
        if (!multiValue && !slot.isEmpty()) {
            List<FlowEntry<T>> entries = slot.drainAll();
            if (!entries.isEmpty()) {
                entries.forEach(data -> {
                    handleEgress(key, data, EgressReason.REPLACE, true);
                    resourceRegistry().releaseGlobalStorage(1);
                });
            }
            slot.append(entry);
        }
        return true;
    }
    
    /** 按 key 判断超时：若 slot 的过期点已到，该 key 下所有 entry 视为已过期。 */
    private List<FlowEntry<T>> collectExpired(FlowSlot<T> slot) {
        List<FlowEntry<T>> result = new ArrayList<>();
        for (FlowEntry<T> entry : slot.entries()) {
            result.add(entry);
        }
        return result;
    }
    
    /**
     * 将「下次检查时间」转为系统时间再交给 SlotExpiryToken，使 DelayQueue 的 getDelay()（用 System.currentTimeMillis()）与等待一致，
     * 避免 clock 与系统时间不一致时 take() 只唤醒一次或 diff 一直大于 0。
     */
    private long toSystemTimeForToken(long nextCheckAtClock) {
        return System.currentTimeMillis() + (nextCheckAtClock - clock.millis());
    }
}

