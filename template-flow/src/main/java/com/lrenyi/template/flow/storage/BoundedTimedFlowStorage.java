package com.lrenyi.template.flow.storage;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.PairingStrategy;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.internal.FlowEgressHandler;
import com.lrenyi.template.flow.internal.FlowFinalizer;
import com.lrenyi.template.flow.internal.FlowLauncher;
import com.lrenyi.template.flow.internal.MatchedPairProcessor;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.resource.ActiveLauncherLookup;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
    public com.lrenyi.template.flow.model.PreRetryResult preRetry(String key,
            FlowEntry<T> entry,
            FlowLauncher<Object> launcher) {
        // Slot 级实现的 preRetry 当前不做额外匹配优化，直接让上层走 requeue 流程
        return com.lrenyi.template.flow.model.PreRetryResult.PROCEED_TO_REQUEUE;
    }
    
    @Override
    public boolean tryRequeue(FlowEntry<T> entry) {
        // 重入时不再单独获取 globalStorage，由 FlowLauncher 在重入路径上统一控制
        return doDeposit(entry);
    }
    
    private final Map<String, FlowSlot<T>> slotByKey = new ConcurrentHashMap<>();
    private final Map<Long, FlowSlot<T>> slotById = new ConcurrentHashMap<>();
    private final Map<Long, String> slotKeyById = new ConcurrentHashMap<>();
    private final DelayQueueExpiryIndex expiryIndex = new DelayQueueExpiryIndex();
    
    private final LongAdder usedEntryCount = new LongAdder();
    /** 超时离库累计数（便于调试） */
    private final LongAdder forcedExpiryCount = new LongAdder();
    
    private final AtomicLong slotIdGenerator = new AtomicLong(1L);
    private final AtomicLong entryIdGenerator = new AtomicLong(1L);
    
    private final EvictionCoordinator evictionCoordinator;
    private final Counter expiryForceCounter;
    private final Timer expiryDelayTimer;
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
        
        Gauge.builder(FlowMetricNames.LIMITS_STORAGE_USED, this, BoundedTimedFlowStorage::usedEntries)
             .tag(FlowMetricNames.TAG_JOB_ID, jobId)
             .tag(FlowMetricNames.TAG_STORAGE_TYPE, "bounded")
             .description("每 Job 存储当前 entry 数")
             .register(meterRegistry);
        Gauge.builder(FlowMetricNames.LIMITS_STORAGE_LIMIT, this::entryLimit)
             .tag(FlowMetricNames.TAG_JOB_ID, jobId)
             .tag(FlowMetricNames.TAG_STORAGE_TYPE, "bounded")
             .description("每 Job 存储 entry 上限")
             .register(meterRegistry);
        
        this.expiryForceCounter = Counter.builder(FlowMetricNames.STORAGE_EXPIRY_FORCE_TOTAL)
                                         .tag(FlowMetricNames.TAG_JOB_ID, jobId)
                                         .tag(FlowMetricNames.TAG_STORAGE_TYPE, "bounded")
                                         .register(meterRegistry);
        this.expiryDelayTimer = Timer.builder(FlowMetricNames.STORAGE_EXPIRY_DELAY_DURATION)
                                     .tag(FlowMetricNames.TAG_JOB_ID, jobId)
                                     .tag(FlowMetricNames.TAG_STORAGE_TYPE, "bounded")
                                     .register(meterRegistry);
    }
    
    private static Lock stripeFor(String key) {
        return KEY_STRIPES[(key.hashCode() & 0x7FFFFFFF) % STRIPE_COUNT];
    }
    
    @Override
    public boolean doDeposit(FlowEntry<T> entry) {
        long now = clock.millis();
        initializeEntryRuntime(entry, now);
        String key = joiner().joinKey(entry.getData());
        Lock stripe = stripeFor(key);
        List<Runnable> afterUnlock = new ArrayList<>(2);
        stripe.lock();
        try {
            Function<String, FlowSlot<T>> slotFunction = k -> {
                long sid = slotIdGenerator.getAndIncrement();
                TemplateConfigProperties.Flow.KeyedCache keyedCache = perJob.getKeyedCache();
                TemplateConfigProperties.Flow.MultiValueOverflowPolicy policy;
                policy = keyedCache.getMultiValueOverflowPolicy();
                FlowSlot<T> created = new FlowSlot<>(sid, maxPerKey, policy, now);
                slotById.put(sid, created);
                slotKeyById.put(sid, k);
                return created;
            };
            FlowSlot<T> slot = slotByKey.computeIfAbsent(key, slotFunction);
            
            boolean needMatched = joiner().needMatched();
            boolean deposited;
            if (needMatched) {
                deposited = handleMatchingModeLocked(key, slot, entry, afterUnlock);
            } else {
                deposited = handleOverwriteModeLocked(key, slot, entry, afterUnlock);
            }
            if (!deposited) {
                return false;
            }
            usedEntryCount.increment();
            updateSlotExpiryMetadata(slot);
            enqueueSlotExpiryIfNeeded(slot, now);
            return true;
        } finally {
            stripe.unlock();
            runAll(afterUnlock);
        }
    }
    
    @Override
    public long size() {
        return usedEntryCount.sum();
    }
    
    @Override
    public long maxCacheSize() {
        return perJob.getStorageCapacity();
    }
    
    @Override
    public long usedEntries() {
        return usedEntryCount.sum();
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
    public int drainRemainingToFinalizer() {
        expiryIndex.clear();
        int drained = 0;
        for (Map.Entry<String, FlowSlot<T>> e : slotByKey.entrySet()) {
            String key = e.getKey();
            Lock stripe = stripeFor(key);
            stripe.lock();
            try {
                FlowSlot<T> slot = slotByKey.remove(key);
                if (slot == null) {
                    continue;
                }
                long sid = slot.getSlotId();
                slotById.remove(sid);
                slotKeyById.remove(sid);
                List<FlowEntry<T>> entries = new ArrayList<>();
                for (FlowEntry<T> entry : slot.entries()) {
                    entries.add(entry);
                }
                for (FlowEntry<T> entry : entries) {
                    usedEntryCount.decrement();
                    handleEgress(key, entry, EgressReason.SINGLE_CONSUMED, false);
                    drained++;
                }
            } finally {
                stripe.unlock();
            }
        }
        return drained;
    }
    
    @Override
    public void shutdown() {
        expiryIndex.clear();
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
                long sid = slot.getSlotId();
                slotById.remove(sid);
                slotKeyById.remove(sid);
                List<FlowEntry<T>> entries = new ArrayList<>();
                for (FlowEntry<T> entry : slot.entries()) {
                    entries.add(entry);
                }
                for (FlowEntry<T> entry : entries) {
                    usedEntryCount.decrement();
                    handleEgress(key, entry, EgressReason.SHUTDOWN, true);
                }
            } finally {
                stripe.unlock();
            }
        }
        expiryIndex.clear();
    }
    
    public void onExpiryToken(SlotExpiryToken token) {
        long now = clock.millis();
        drainExpiredEntries(token.slotId(), token.version(), now);
    }
    
    void drainExpiredEntries(long slotId, int expectedVersion, long nowEpochMs) {
        FlowSlot<T> slot = slotById.get(slotId);
        if (slot == null) {
            return;
        }
        String key = slotKeyById.get(slotId);
        if (key == null) {
            return;
        }
        
        Lock stripe = stripeFor(key);
        List<FlowEntry<T>> expired = new ArrayList<>();
        long slotExpireAt = 0L;
        boolean noExpiredFound = false;
        
        stripe.lock();
        try {
            if (slotById.get(slotId) != slot) {
                return;
            }
            if (slot.getVersion() != expectedVersion) {
                return;
            }
            if (slot.isDraining()) {
                return;
            }
            
            expired = collectExpired(slot, nowEpochMs);
            if (!expired.isEmpty()) {
                slotExpireAt = slot.getEarliestExpireAt();
                slot.setDraining(true);
                removeEntriesForEgress(slot, expired);
                forcedExpiryCount.add(expired.size());
                expiryForceCounter.increment(expired.size());
            } else {
                noExpiredFound = true;
                requeueBasedOnEarliestFutureExpiry(slot);
            }
            
            // 清理空 slot；未空则必须重新入队下一次过期检查，并清除 draining 以便下次 token 可被处理
            if (slotIsEmpty(slot)) {
                if (noExpiredFound && !slot.isEmptyRecheckScheduled()) {
                    long defaultRecheckMs = Math.max(perJob.getKeyedCache().getEffectiveTimeoutMill(), 60_000L);
                    requeueSlotExpiry(slot, nowEpochMs + defaultRecheckMs);
                    slot.setEmptyRecheckScheduled(true);
                } else {
                    slotByKey.remove(key, slot);
                    slotById.remove(slotId, slot);
                    slotKeyById.remove(slotId);
                }
            } else {
                slot.setDraining(false);
                slot.setEmptyRecheckScheduled(false);
                updateSlotExpiryMetadata(slot);
                requeueBasedOnEarliestFutureExpiry(slot);
            }
        } finally {
            stripe.unlock();
        }
        
        if (!expired.isEmpty()) {
            recordExpiryDelay(nowEpochMs, slotExpireAt);
            submitExpiredEntriesWithPairing(key, expired, true);
        }
    }
    
    /**
     * 与原 Caffeine 行为一致：从缓存/存储驱逐出的数据先做槽位全量配对，再对未匹配条目走 handleEgress。
     * 若未开启配对（!needMatched）则直接逐条释放 global storage 并 handleEgress。
     */
    private void submitExpiredEntriesWithPairing(String key, List<FlowEntry<T>> entries, boolean defaultSkipRetry) {
        if (entries.isEmpty()) {
            return;
        }
        if (!joiner().needMatched()) {
            for (FlowEntry<T> entry : entries) {
                resourceRegistry().releaseGlobalStorage(1);
                handleEgress(key, entry, EgressReason.TIMEOUT, defaultSkipRetry);
            }
            return;
        }
        processEvictedSlot(key, entries);
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
                matchedPairProcessor.processMatchedPair(x, matched, launcher);
                if (!multiMatchEnabled) {
                    break;
                }
            } else {
                resourceRegistry().releaseGlobalStorage(1);
                unmatched.add(x);
            }
        }
        if (hasAnyPairSucceeded && !multiMatchEnabled) {
            for (int i = 0; i < n; i++) {
                if (!processed[i]) {
                    FlowEntry<T> e = entries.get(i);
                    resourceRegistry().releaseGlobalStorage(1);
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
            handleEgress(key, e, EgressReason.TIMEOUT, skipRetry);
        }
    }
    
    private void initializeEntryRuntime(FlowEntry<T> entry, long now) {
        long entryId = entryIdGenerator.getAndIncrement();
        entry.initRuntime(entryId, now, 0);
    }
    
    /** 按 key 计算过期点：以 slot 首次写入时间为起点 + TTL。 */
    private void updateSlotExpiryMetadata(FlowSlot<T> slot) {
        long at = slot.getEarliestStoredAtEpochMs();
        if (at <= 0L) {
            at = clock.millis();
            slot.setEarliestStoredAtEpochMs(at);
        }
        long timeoutMs = perJob.getKeyedCache().getEffectiveTimeoutMill();
        slot.setEarliestExpireAt(at + timeoutMs);
    }

    private void enqueueSlotExpiryIfNeeded(FlowSlot<T> slot, long now) {
        if (slot.isQueuedForExpiry()) {
            if (log.isTraceEnabled()) {
                log.trace("[{}] slot already queued for expiry, slotId={}", jobId, slot.getSlotId());
            }
            return;
        }
        long expireAt = slot.getEarliestExpireAt();
        if (expireAt <= 0) {
            log.warn("[{}] slot has no expiry (earliestExpireAt={}), slotId={}; check keyed-cache.cache-ttl-mill",
                     jobId, expireAt, slot.getSlotId());
            return;
        }
        long nextCheckAt = expireAt;
        slot.setVersion(slot.getVersion() + 1);
        slot.setNextCheckAt(nextCheckAt);
        slot.setQueuedForExpiry(true);
        expiryIndex.schedule(new SlotExpiryToken(slot.getSlotId(), toSystemTimeForToken(nextCheckAt), slot.getVersion()));
        if (log.isDebugEnabled()) {
            log.debug("[{}] scheduled expiry token, slotId={}, nextCheckAt={}, delayMs={}",
                      jobId, slot.getSlotId(), nextCheckAt, nextCheckAt - now);
        }
    }
    
    private boolean handleMatchingModeLocked(String key,
            FlowSlot<T> slot,
            FlowEntry<T> incoming,
            List<Runnable> afterUnlock) {
        // 双流配对模式：优先尝试从当前槽位中找 partner，找不到再写入
        PairingStrategy<T> strategy = joiner().getPairingStrategy();
        PairingContext<T> ctx = new SlotPairingContext(key, slot);
        java.util.Optional<FlowEntry<T>> partnerOpt = strategy.findPartner(key, incoming, ctx);
        if (partnerOpt.isEmpty()) {
            // 未找到 partner，incoming 已在 ctx.put 中写入或被丢弃（多值溢出）
            return true;
        }
        FlowEntry<T> partner = partnerOpt.get();
        // 找到配对对象：在锁内先从槽位中彻底移除 partner，更新计数
        if (slot.remove(partner)) {
            usedEntryCount.decrement();
        }
        updateSlotExpiryMetadata(slot);
        // 锁外提交配对消费
        afterUnlock.add(() -> {
            try (FlowEntry<T> existing = partner; FlowEntry<T> current = incoming) {
                joiner().onPairConsumed(existing.getData(), current.getData(), existing.getJobId());
                resourceRegistry().releaseGlobalStorage(1);
                resourceRegistry().releaseGlobalStorage(1);
            } catch (Throwable t) {
                // 失败时走被动出口，按 TIMEOUT 处理
                handleEgress(key, incoming, EgressReason.TIMEOUT, true);
                handleEgress(key, partner, EgressReason.TIMEOUT, true);
            }
        });
        return true;
    }
    
    private boolean handleOverwriteModeLocked(String key,
            FlowSlot<T> slot,
            FlowEntry<T> entry,
            List<Runnable> afterUnlock) {
        // 单值模式：最新写入覆盖旧值，旧值以 REPLACE 原因离库
        boolean multiValue = perJob.getKeyedCache().isMultiValueEnabled();
        if (!multiValue && !slot.isEmpty()) {
            FlowEntry<T> old = slot.poll().orElse(null);
            if (old != null) {
                usedEntryCount.decrement();
                updateSlotExpiryMetadata(slot);
                afterUnlock.add(() -> handleEgress(key, old, EgressReason.REPLACE, true));
            }
        }
        slot.append(entry);
        return true;
    }
    
    private static void runAll(List<Runnable> tasks) {
        for (Runnable r : tasks) {
            try {
                r.run();
            } catch (Throwable ignored) {
                // 记录日志可选
            }
        }
    }
    
    /** 按 key 判断超时：若 slot 的过期点已到，该 key 下所有 entry 视为已过期。 */
    private List<FlowEntry<T>> collectExpired(FlowSlot<T> slot, long now) {
        long expireAt = slot.getEarliestExpireAt();
        if (expireAt <= 0L || now < expireAt) {
            return new ArrayList<>();
        }
        List<FlowEntry<T>> result = new ArrayList<>();
        for (FlowEntry<T> entry : slot.entries()) {
            entry.setRuntimeState(FlowEntry.STATE_EGRESSING);
            result.add(entry);
        }
        return result;
    }
    
    private void removeEntriesForEgress(FlowSlot<T> slot, List<FlowEntry<T>> expired) {
        for (FlowEntry<T> entry : expired) {
            if (entry.getRuntimeState() != FlowEntry.STATE_EGRESSING) {
                entry.setRuntimeState(FlowEntry.STATE_EGRESSING);
            }
            slot.remove(entry);
            usedEntryCount.decrement();
        }
        updateSlotExpiryMetadata(slot);
    }
    
    /**
     * 将「下次检查时间」转为系统时间再交给 SlotExpiryToken，使 DelayQueue 的 getDelay()（用 System.currentTimeMillis()）与等待一致，
     * 避免 clock 与系统时间不一致时 take() 只唤醒一次或 diff 一直大于 0。
     */
    private long toSystemTimeForToken(long nextCheckAtClock) {
        return System.currentTimeMillis() + (nextCheckAtClock - clock.millis());
    }

    private void requeueSlotExpiry(FlowSlot<T> slot, long nextCheckAt) {
        slot.setVersion(slot.getVersion() + 1);
        slot.setNextCheckAt(nextCheckAt);
        slot.setQueuedForExpiry(true);
        expiryIndex.schedule(new SlotExpiryToken(slot.getSlotId(), toSystemTimeForToken(nextCheckAt), slot.getVersion()));
    }
    
    private void requeueBasedOnEarliestFutureExpiry(FlowSlot<T> slot) {
        long expireAt = slot.getEarliestExpireAt();
        if (expireAt > 0) {
            requeueSlotExpiry(slot, expireAt);
        } else {
            slot.setQueuedForExpiry(false);
        }
    }
    
    private boolean slotIsEmpty(FlowSlot<T> slot) {
        return slot.isEmpty();
    }
    
    /**
     * 基于 FlowSlot 的 PairingContext，实现 getAndRemove/put 的原子操作。
     */
    private final class SlotPairingContext implements PairingContext<T> {
        private final String key;
        private final FlowSlot<T> slot;
        
        SlotPairingContext(String key, FlowSlot<T> slot) {
            this.key = key;
            this.slot = slot;
        }
        
        @Override
        public java.util.Optional<FlowEntry<T>> getAndRemove(String k) {
            if (!key.equals(k)) {
                return java.util.Optional.empty();
            }
            return slot.poll();
        }
        
        @Override
        public void put(String k, FlowEntry<T> entry) {
            if (!key.equals(k)) {
                return;
            }
            java.util.Optional<FlowSlot.OverflowResult<T>> overflow = slot.append(entry);
            overflow.ifPresent(r -> handleEgress(k, r.entry(), r.reason(), true));
        }
    }
    
    /** 按 key 记录过期延迟（一次 per slot）。 */
    private void recordExpiryDelay(long nowEpochMs, long effectiveExpireAtEpochMs) {
        if (effectiveExpireAtEpochMs <= 0L || nowEpochMs < effectiveExpireAtEpochMs) {
            return;
        }
        long delay = nowEpochMs - effectiveExpireAtEpochMs;
        expiryDelayTimer.record(delay, TimeUnit.MILLISECONDS);
    }
}

