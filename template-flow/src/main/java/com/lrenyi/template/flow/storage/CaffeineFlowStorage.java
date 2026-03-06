package com.lrenyi.template.flow.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.exception.FlowExceptionHelper;
import com.lrenyi.template.flow.exception.FlowPhase;
import com.lrenyi.template.flow.internal.FlowFinalizer;
import com.lrenyi.template.flow.internal.FlowLauncher;
import com.lrenyi.template.flow.internal.MatchedPairProcessor;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.model.FailureReason;
import com.lrenyi.template.flow.model.PreRetryResult;
import com.lrenyi.template.flow.resource.ActiveLauncherLookup;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于 Caffeine 实现的 Key-Value 流式存储。
 * 支持单值模式（multiValueEnabled=false）与多值模式（同 key 多 value）。
 *
 * @param <T> 存储的数据类型
 */
@Slf4j
public class CaffeineFlowStorage<T> extends AbstractEgressFlowStorage<T> implements FlowStorage<T> {
    private static final int STRIPE_COUNT = 256;
    private static final Lock[] KEY_STRIPES = new Lock[STRIPE_COUNT];
    
    static {
        for (int i = 0; i < STRIPE_COUNT; i++) {
            KEY_STRIPES[i] = new ReentrantLock();
        }
    }
    
    @Getter
    private final Cache<String, FlowSlot<T>> cache;
    private final long maxCacheSize;
    private final MatchedPairProcessor<T> matchedPairProcessor;
    private final LongAdder removalSubmittedCount = new LongAdder();
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final TemplateConfigProperties.Flow.PerJob perJob;
    private final int maxPerKey;
    
    public CaffeineFlowStorage(TemplateConfigProperties.Flow config,
            FlowJoiner<T> joiner,
            FlowFinalizer<T> finalizer,
            ProgressTracker progressTracker,
            MeterRegistry meterRegistry,
            String jobId) {
        super(joiner, finalizer, progressTracker, meterRegistry);
        this.perJob = config.getLimits().getPerJob();
        this.maxCacheSize = perJob.getStorage();
        this.maxPerKey = perJob.getEffectiveMultiValueMaxPerKey();
        FlowResourceRegistry resourceRegistry = resourceRegistry();
        this.matchedPairProcessor =
                new MatchedPairProcessor<>(joiner, progressTracker, meterRegistry, resourceRegistry);
        RemovalListener<String, FlowSlot<T>> removalListener =
                (String key, FlowSlot<T> slot, RemovalCause cause) -> onSlotRemoved(slot, cause);
        this.cache = Caffeine.newBuilder()
                             .maximumSize(maxCacheSize)
                             .expireAfterWrite(perJob.getCacheTtlMill(), TimeUnit.MILLISECONDS)
                             .executor(resourceRegistry.getCacheRemovalExecutor())
                             .removalListener(removalListener)
                             .scheduler(Scheduler.systemScheduler())
                             .build();
        
        Gauge.builder(FlowMetricNames.LIMITS_STORAGE_USED, cache, Cache::estimatedSize)
             .tag(FlowMetricNames.TAG_JOB_ID, jobId)
             .tag(FlowMetricNames.TAG_STORAGE_TYPE, "caffeine")
             .description("每 Job 缓存当前条数")
             .register(meterRegistry);
        Gauge.builder(FlowMetricNames.LIMITS_STORAGE_LIMIT, () -> maxCacheSize)
             .tag(FlowMetricNames.TAG_JOB_ID, jobId)
             .tag(FlowMetricNames.TAG_STORAGE_TYPE, "caffeine")
             .description("每 Job 缓存容量上限")
             .register(meterRegistry);
    }
    
    private void onSlotRemoved(FlowSlot<T> slot, RemovalCause cause) {
        if (slot == null) {
            return;
        }
        FailureReason reason = mapRemovalCause(cause);
        if (reason == FailureReason.SHUTDOWN) {
            for (FlowEntry<T> e : slot.drainAll()) {
                resourceRegistry().releaseGlobalStorage(1);
                handlePassiveFailure(e, reason);
            }
            return;
        }
        processEvictedSlot(slot, reason);
    }
    
    /**
     * 驱逐槽位全量配对：每条 entry 与其余所有 entry 尝试匹配。
     * 若有至少一对配对成功，则未匹配条目的重入标志重置为 -1，防止后续继续走重入漏极。
     */
    private void processEvictedSlot(FlowSlot<T> slot, FailureReason reason) {
        List<FlowEntry<T>> entries = slot.drainAll();
        int n = entries.size();
        if (n == 0) {
            return;
        }
        boolean[] processed = new boolean[n];
        List<FlowEntry<T>> unmatched = new ArrayList<>(n);
        boolean hasAnyPairSucceeded = false;
        ActiveLauncherLookup launcherLookup = resourceRegistry().getLauncherLookup();
        FlowLauncher<Object> launcher = null;
        if (launcherLookup != null) {
            launcher = launcherLookup.getActiveLauncher(entries.getFirst().getJobId());
        }
        
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
            if (matched != null && launcher != null) {
                processed[i] = true;
                processed[matchedIdx] = true;
                hasAnyPairSucceeded = true;
                resourceRegistry().releaseGlobalStorage(1);
                matchedPairProcessor.processMatchedPair(x, matched, launcher);
            } else {
                resourceRegistry().releaseGlobalStorage(1);
                unmatched.add(x);
            }
        }
        
        if (hasAnyPairSucceeded) {
            for (FlowEntry<T> e : unmatched) {
                e.resetRetryToIneligible();
            }
        }
        for (FlowEntry<T> e : unmatched) {
            handlePassiveFailure(e, reason);
        }
    }
    
    @Override
    public PreRetryResult preRetry(String key, FlowEntry<T> entry, FlowLauncher<Object> launcher) {
        Lock stripe = KEY_STRIPES[((key.hashCode() & 0x7FFFFFFF) % STRIPE_COUNT)];
        stripe.lock();
        try {
            boolean matched = tryMatchFromSlot(key,
                                               entry,
                                               (p, e) -> matchedPairProcessor.processMatchedPair(p, e, launcher),
                                               c -> {}
            );
            return matched ? PreRetryResult.HANDLED : PreRetryResult.PROCEED_TO_REQUEUE;
        } finally {
            stripe.unlock();
        }
    }
    
    @Override
    public boolean tryRequeue(FlowEntry<T> entry) {
        if (!acquireGlobalStorageForRequeue(entry.getJobId())) {
            return false;
        }
        boolean requeued = requeue(entry);
        if (!requeued) {
            resourceRegistry().releaseGlobalStorage(1);
        }
        if (requeued) {
            entry.close();
        }
        return requeued;
    }
    
    private boolean acquireGlobalStorageForRequeue(String jobId) {
        Semaphore globalStorage = resourceRegistry().getGlobalStorageSemaphore();
        if (globalStorage == null) {
            return true;
        }
        try {
            globalStorage.acquire(1);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.STORAGE, "requeue_storage_acquire_interrupted");
            return false;
        }
    }
    
    private FailureReason mapRemovalCause(RemovalCause cause) {
        if (cause == RemovalCause.EXPIRED) {
            return FailureReason.TIMEOUT;
        }
        if (cause == RemovalCause.SIZE) {
            return FailureReason.EVICTION;
        }
        if (cause == RemovalCause.EXPLICIT && shuttingDown.get()) {
            return FailureReason.SHUTDOWN;
        }
        return null;
    }
    
    private void handleOverflowDropped(FlowEntry<T> dropped, FailureReason reason) {
        resourceRegistry().releaseGlobalStorage(1);
        handlePassiveFailure(dropped, reason);
        Counter.builder(FlowMetricNames.STORAGE_MULTI_VALUE_DISCARD_TOTAL)
               .tag(FlowMetricNames.TAG_JOB_ID, dropped.getJobId())
               .tag(FlowMetricNames.TAG_REASON, reason.name().toLowerCase())
               .register(meterRegistry())
               .increment();
    }
    
    @Override
    public void handlePassiveFailure(FlowEntry<T> entry, FailureReason reason) {
        try (entry) {
            joiner().onFailed(entry.getData(), entry.getJobId(), reason);
            Counter.builder(FlowMetricNames.EGRESS_PASSIVE)
                   .tag(FlowMetricNames.TAG_JOB_ID, entry.getJobId())
                   .tag(FlowMetricNames.TAG_REASON, reason.name())
                   .register(meterRegistry())
                   .increment();
        } catch (Exception e) {
            FlowExceptionHelper.handleException(entry.getJobId(), null, e, FlowPhase.STORAGE, "eviction_process_failed");
        } finally {
            progressTracker().onPassiveEgress(reason);
        }
    }
    
    @Override
    public boolean doDeposit(FlowEntry<T> entry) {
        String key = joiner().joinKey(entry.getData());
        
        if (!joiner().needMatched()) {
            return handleOverwriteMode(key, entry);
        }
        
        return handleMatchingMode(key, entry);
    }
    
    private boolean handleOverwriteMode(String key, FlowEntry<T> entry) {
        Lock stripe = KEY_STRIPES[((key.hashCode() & 0x7FFFFFFF) % STRIPE_COUNT)];
        stripe.lock();
        try {
            if (maxPerKey == 1) {
                FlowSlot<T> newSlot = createNewSlot();
                newSlot.append(entry);
                FlowSlot<T> oldSlot = cache.asMap().put(key, newSlot);
                if (oldSlot != null) {
                    for (FlowEntry<T> e : oldSlot.drainAll()) {
                        handleReplacedEntry(e);
                    }
                }
            } else {
                BiFunction<String, FlowSlot<T>, FlowSlot<T>> slotBiFunction = (k, existing) -> {
                    FlowSlot<T> slot = existing != null ? existing : createNewSlot();
                    Optional<FlowSlot.OverflowResult<T>> overflow = slot.append(entry);
                    overflow.ifPresent(r -> handleOverflowDropped(r.entry(), r.reason()));
                    return slot.isEmpty() ? null : slot;
                };
                cache.asMap().compute(key, slotBiFunction);
            }
            return true;
        } finally {
            stripe.unlock();
        }
    }
    
    private boolean handleMatchingMode(String key, FlowEntry<T> entry) {
        Lock stripe = KEY_STRIPES[((key.hashCode() & 0x7FFFFFFF) % STRIPE_COUNT)];
        stripe.lock();
        try {
            boolean matched = tryMatchFromSlot(key, entry, this::processMatchedPair, c -> c.put(key, entry));
            return !matched;
        } finally {
            stripe.unlock();
        }
    }
    
    private void handleReplacedEntry(FlowEntry<T> oldEntry) {
        resourceRegistry().releaseGlobalStorage(1);
        try (oldEntry) {
            joiner().onFailed(oldEntry.getData(), oldEntry.getJobId(), FailureReason.REPLACE);
            Counter.builder(FlowMetricNames.EGRESS_PASSIVE)
                   .tag(FlowMetricNames.TAG_JOB_ID, oldEntry.getJobId())
                   .tag(FlowMetricNames.TAG_REASON, "REPLACE")
                   .register(meterRegistry())
                   .increment();
        } catch (Exception e) {
            FlowExceptionHelper.handleException(oldEntry.getJobId(), null, e, FlowPhase.STORAGE,
                    "replace_process_failed");
        } finally {
            progressTracker().onPassiveEgress(FailureReason.REPLACE);
        }
    }
    
    private void processMatchedPair(FlowEntry<T> partner, FlowEntry<T> entry) {
        resourceRegistry().releaseGlobalStorage(1);
        ActiveLauncherLookup launcherLookup = resourceRegistry().getLauncherLookup();
        if (launcherLookup == null) {
            log.warn("LauncherLookup not available for job {}", entry.getJobId());
            Counter.builder(FlowMetricNames.ERRORS)
                   .tag(FlowMetricNames.TAG_ERROR_TYPE, "flow_manager_unavailable")
                   .tag(FlowMetricNames.TAG_PHASE, "CONSUMPTION")
                   .register(meterRegistry())
                   .increment();
            partner.close();
            return;
        }
        FlowLauncher<Object> launcher = launcherLookup.getActiveLauncher(entry.getJobId());
        if (launcher == null) {
            log.warn("No active launcher found for job id {}", entry.getJobId());
            partner.close();
            return;
        }
        matchedPairProcessor.processMatchedPair(partner, entry, launcher);
    }
    
    /**
     * 多候选尝试配对：依次尝试 slot 内候选，直到匹配或全部试完。
     *
     * @param key               聚合键
     * @param entry             待配对的 entry（incoming 或重入 entry）
     * @param matchedPairHandler 匹配成功时的处理逻辑
     * @param onNoMatch         无匹配时的回调（如 handleMatchingMode 需 put，preRetry 可空操作）
     * @return true 若找到匹配并处理，false 若无匹配
     */
    private boolean tryMatchFromSlot(String key,
            FlowEntry<T> entry,
            BiConsumer<FlowEntry<T>, FlowEntry<T>> matchedPairHandler,
            Consumer<PairingContext<T>> onNoMatch) {
        CaffeinePairingContext<T> ctx =
                new CaffeinePairingContext<>(cache, maxPerKey, perJob, this::handleOverflowDropped);
        List<FlowEntry<T>> candidates = new ArrayList<>();
        while (true) {
            Optional<FlowEntry<T>> partner = joiner().getPairingStrategy().findPartner(key, entry, ctx);
            if (partner.isEmpty()) {
                break;
            }
            candidates.add(partner.get());
        }
        for (FlowEntry<T> partner : candidates) {
            if (joiner().isMatched(partner.getData(), entry.getData())) {
                matchedPairHandler.accept(partner, entry);
                if (!perJob.isPairingMultiMatchEnabled()) {
                    for (FlowEntry<T> e : candidates) {
                        if (e != partner) {
                            e.resetRetryToIneligible();
                            resourceRegistry().releaseGlobalStorage(1);
                            handlePassiveFailure(e, FailureReason.CLEARED_AFTER_PAIR_SUCCESS);
                        }
                    }
                } else {
                    for (FlowEntry<T> e : candidates) {
                        if (e != partner) {
                            ctx.putBackPartnerAtEnd(key, e);
                        }
                    }
                }
                return true;
            }
        }
        for (FlowEntry<T> e : candidates) {
            ctx.putBackPartnerAtEnd(key, e);
        }
        onNoMatch.accept(ctx);
        return false;
    }
    
    private FlowSlot<T> createNewSlot() {
        return new FlowSlot<>(maxPerKey, perJob.getMultiValueOverflowPolicy());
    }
    
    @Override
    public long size() {
        return cache.estimatedSize();
    }
    
    @Override
    public long maxCacheSize() {
        return maxCacheSize;
    }
    
    @Override
    public void shutdown() {
        shuttingDown.set(true);
        cache.invalidateAll();
        cache.cleanUp();
        log.info("CaffeineFlowStorage shut down, all entries invalidated.");
    }
    
    @Override
    public void remove(String key) {
        cache.asMap().remove(key);
    }
    
    @Override
    public long getRemovalSubmittedCount() {
        return removalSubmittedCount.sum();
    }
}
