package com.lrenyi.template.flow.storage;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.model.PreRetryResult;
import com.lrenyi.template.flow.resource.ActiveLauncherLookup;
import com.lrenyi.template.flow.util.FlowLogHelper;
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
        if (!joiner().needMatched() || launcher == null) {
            return PreRetryResult.PROCEED_TO_REQUEUE;
        }
        Lock stripe = stripeFor(key);
        stripe.lock();
        try {
            FlowSlot<T> slot = slotByKey.get(key);
            if (slot == null || slot.isEmpty()) {
                return PreRetryResult.PROCEED_TO_REQUEUE;
            }
            for (FlowEntry<T> candidate : slot.entries()) {
                if (joiner().isMatched(candidate.getData(), entry.getData())) {
                    slot.remove(candidate);
                    savedEntryCount.decrement();
                    // candidate 的 storageLease 由 submitPairDataToConsumer 内部（partner.closeStorageLease）释放；
                    // entry 为重入条目，其 storageLease 在首次离库时已关闭，此处幂等调用为空操作。
                    finalizer().submitPairDataToConsumer(candidate, entry, launcher);
                    entry.closeStorageLease();
                    return PreRetryResult.HANDLED;
                }
            }
        } finally {
            stripe.unlock();
        }
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
    private final java.util.concurrent.atomic.AtomicBoolean completionDrainTriggered =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private final EvictionCoordinator evictionCoordinator;
    private final TemplateConfigProperties.Flow.PerJob perJob;
    private final String jobId;
    private final int maxPerKey;
    private final Clock clock;
    public BoundedTimedFlowStorage(TemplateConfigProperties.Flow flowConfig,
            FlowJoiner<T> joiner,
            ProgressTracker progressTracker,
            FlowFinalizer<T> finalizer,
            FlowEgressHandler<T> egressHandler,
            MeterRegistry meterRegistry,
            String jobId) {
        super(joiner, finalizer, progressTracker, meterRegistry, egressHandler);
        this.perJob = flowConfig.getLimits().getPerJob();
        this.maxPerKey = perJob.getKeyedCache().getEffectiveMultiValueMaxPerKey();
        this.jobId = jobId;
        this.clock = Clock.systemUTC();
        int evictionThreads = perJob.getEffectiveEvictionCoordinatorThreads(flowConfig.getLimits().getGlobal());
        long evictionScanIntervalMill = perJob.getEffectiveEvictionScanIntervalMill(flowConfig.getLimits().getGlobal());
        this.evictionCoordinator = new EvictionCoordinator(expiryIndex,
                                                           this,
                                                           "app-template-flow-eviction-" + jobId,
                                                           evictionThreads,
                                                           evictionScanIntervalMill
        );
        this.evictionCoordinator.start();
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
    
    /**
     * 生产完成时主动将剩余条目提交给消费者（completion drain）。
     * 仅对非匹配模式生效，保证幂等：多次调用仅执行一次。
     * 在匹配模式下，孤立条目仍由 TTL 驱逐以 TIMEOUT 被动离库。
     */
    @Override
    public void triggerCompletionDrain() {
        if (joiner().needMatched()) {
            return;
        }
        if (!completionDrainTriggered.compareAndSet(false, true)) {
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("[{}] Completion drain triggered, remaining slots={}", jobId, slotByKey.size());
        }
        for (String key : new ArrayList<>(slotByKey.keySet())) {
            drainSlotForCompletion(key);
        }
    }
    
    private void drainSlotForCompletion(String key) {
        FlowSlot<T> slot = slotByKey.get(key);
        if (slot == null || slot.isEmpty()) {
            return;
        }
        Lock stripe = stripeFor(key);
        stripe.lock();
        try {
            slot = slotByKey.get(key);
            if (slot == null) {
                return;
            }
            List<FlowEntry<T>> entries = slot.drainAll();
            if (!entries.isEmpty()) {
                slotByKey.remove(key);
            }
            for (FlowEntry<T> entry : entries) {
                savedEntryCount.decrement();
                entry.closeStorageLease();
                handleEgress(key, entry, EgressReason.SINGLE_CONSUMED, false);
            }
        } finally {
            stripe.unlock();
        }
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
                    entry.closeStorageLease();
                    handleEgress(key, entry, EgressReason.SHUTDOWN, true);
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
                log.trace("[{}] skip eviction for slot {}, pairing in progress",
                    FlowLogHelper.formatJobContext(jobId, null), slotId);
            }
            return;
        }
        List<FlowEntry<T>> expired = collectExpired(slot);
        // 驱逐先触发、配对后触发：在真正执行驱逐前再次检查，若已进入配对则中止本次驱逐并重新排队
        if (slot.isPairingInProgress()) {
            if (log.isTraceEnabled()) {
                log.trace("[{}] abort eviction for slot {}, pairing started before drain",
                    FlowLogHelper.formatJobContext(jobId, null), slotId);
            }
            requeueSlotExpiry(slot);
            return;
        }
        if (!joiner().needMatched()) {
            for (FlowEntry<T> entry : expired) {
                savedEntryCount.decrement();
                entry.closeStorageLease();
                handleEgress(slotId, entry, EgressReason.SINGLE_CONSUMED, true);
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
            log.debug("驱逐槽位 {} 全量配对, 共 {} 条 entry", key, n);
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
                x.closeStorageLease();
                savedEntryCount.decrement();
                savedEntryCount.decrement();
                finalizer().submitPairDataToConsumer(x, matched, launcher);
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
        for (FlowEntry<T> e : unmatched) {
            e.closeStorageLease();
            savedEntryCount.decrement();
            handleEgress(key, e, EgressReason.SINGLE_CONSUMED, true);
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
                log.trace("[{}] slot already queued for expiry, slotId={}",
                    FlowLogHelper.formatJobContext(jobId, null), slot.getSlotId());
            }
            return;
        }
        long nextCheckAt = slot.getEarliestExpireAt();
        slot.setNextCheckAt(nextCheckAt);
        slot.setQueuedForExpiry(true);
        expiryIndex.schedule(new SlotExpiryToken(slot.getSlotId(), toSystemTimeForToken(nextCheckAt)));
        if (log.isDebugEnabled()) {
            log.debug("[{}] scheduled expiry token, slotId={}, nextCheckAt={}, delayMs={}",
                      FlowLogHelper.formatJobContext(jobId, null),
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
            FlowEntry<T> parentEntry = findFirstMatchingParent(slot, incoming, joiner);
            if (parentEntry != null) {
                slot.remove(parentEntry);
                if (!perJob.isPairingMultiMatchEnabled()) {
                    slot.entries().forEach(entry -> {
                        entry.closeStorageLease();
                        handleEgress(key, entry, EgressReason.CLEARED_AFTER_PAIR_SUCCESS, true);
                        savedEntryCount.decrement();
                    });
                }
            } else {
                // 处理 overflow：若 slot 已满被淘汰的旧条目需正确核减计数并走被动出口
                Optional<FlowSlot.OverflowResult<T>> overflowResult = slot.append(incoming);
                overflowResult.ifPresent(or -> {
                    savedEntryCount.decrement();
                    or.entry().closeStorageLease();
                    handleEgress(key, or.entry(), or.reason(), true);
                });
                saved = true;
            }
        } finally {
            slot.setPairingInProgress(false);
        }
        return saved;
    }
    
    /**
     * 从 slot 中查找首个与 incoming 匹配的 parent，找到后立即提交配对并返回。
     * 使用 for 循环确保首次匹配后即退出，避免 forEach 无法 break 导致的多匹配重复提交。
     */
    private FlowEntry<T> findFirstMatchingParent(FlowSlot<T> slot,
            FlowEntry<T> incoming,
            FlowJoiner<T> joiner) {
        ActiveLauncherLookup launcherLookup = resourceRegistry().getLauncherLookup();
        if (launcherLookup == null) {
            return null;
        }
        FlowLauncher<Object> launcher = launcherLookup.getActiveLauncher(jobId);
        if (launcher == null) {
            return null;
        }
        for (FlowEntry<T> parent : slot.entries()) {
            if (!joiner.isMatched(parent.getData(), incoming.getData())) {
                continue;
            }
            finalizer().submitPairDataToConsumer(parent, incoming, launcher);
            incoming.closeStorageLease();
            savedEntryCount.decrement();
            return parent;
        }
        return null;
    }
    
    private boolean handleOverwriteModeLocked(String key, FlowSlot<T> slot, FlowEntry<T> entry) {
        // 单值模式：最新写入覆盖旧值，旧值以 REPLACE 原因离库
        boolean multiValue = perJob.getKeyedCache().isMultiValueEnabled();
        if (!multiValue && !slot.isEmpty()) {
            List<FlowEntry<T>> entries = slot.drainAll();
            if (!entries.isEmpty()) {
                entries.forEach(data -> {
                    savedEntryCount.decrement();
                    data.closeStorageLease();
                    handleEgress(key, data, EgressReason.REPLACE, true);
                });
            }
        }
        slot.append(entry);
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

