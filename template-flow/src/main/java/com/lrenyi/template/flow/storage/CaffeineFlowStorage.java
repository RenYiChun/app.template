package com.lrenyi.template.flow.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import com.github.benmanes.caffeine.cache.Scheduler;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.exception.FlowExceptionHelper;
import com.lrenyi.template.flow.exception.FlowPhase;
import com.lrenyi.template.flow.internal.FlowEgressHandler;
import com.lrenyi.template.flow.internal.FlowFinalizer;
import com.lrenyi.template.flow.internal.FlowLauncher;
import com.lrenyi.template.flow.internal.MatchedPairProcessor;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.model.EgressReason;
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
            ProgressTracker progressTracker, MeterRegistry meterRegistry, FlowEgressHandler<T> egressHandler,
            String jobId) {
        super(joiner, finalizer, progressTracker, meterRegistry, egressHandler);
        this.perJob = config.getLimits().getPerJob();
        this.maxCacheSize = perJob.getStorage();
        this.maxPerKey = perJob.getEffectiveMultiValueMaxPerKey();
        FlowResourceRegistry resourceRegistry = resourceRegistry();
        this.matchedPairProcessor = new MatchedPairProcessor<>(joiner, egressHandler, meterRegistry, resourceRegistry);
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxCacheSize)
                .expireAfterWrite(perJob.getCacheTtlMill(), TimeUnit.MILLISECONDS)
                .executor(resourceRegistry.getCacheRemovalExecutor())
                .removalListener(this::onSlotRemoved)
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

    private void onSlotRemoved(String key, FlowSlot<T> slot, RemovalCause cause) {
        if (slot == null) {
            return;
        }
        EgressReason reason = mapRemovalCause(cause);
        if (reason == EgressReason.SHUTDOWN) {
            for (FlowEntry<T> e : slot.drainAll()) {
                resourceRegistry().releaseGlobalStorage(1);
                handleEgress(key, e, reason, true);
            }
            return;
        }
        processEvictedSlot(key, slot, reason);
    }

    /**
     * 驱逐槽位全量配对：每条 entry 与其余所有 entry 尝试匹配。
     * 若有至少一对配对成功，则未匹配条目的重入标志重置为 -1，防止后续继续走重入漏极。
     */
    private void processEvictedSlot(String key, FlowSlot<T> slot, EgressReason reason) {
        List<FlowEntry<T>> entries = slot.drainAll();
        log.debug("驱逐槽位 {} 全量配对，共 {} 条 entry", key, entries.size());
        int n = entries.size();
        if (n == 0) {
            return;
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
            if (matched != null && launcher != null) {
                processed[i] = true;
                processed[matchedIdx] = true;
                hasAnyPairSucceeded = true;
                resourceRegistry().releaseGlobalStorage(1);
                matchedPairProcessor.processMatchedPair(x, matched, launcher);
                if (!multiMatchEnabled) {
                    break;
                }
            } else {
                processed[i] = true;
                resourceRegistry().releaseGlobalStorage(1);
                unmatched.add(x);
            }
        }
        log.debug("槽位 {} 全量配对完成，共 {} 条 entry，成功配对 {} 对，未匹配 {} 条",
                key,
                n,
                hasAnyPairSucceeded ? 1 : 0,
                unmatched.size());
        if (hasAnyPairSucceeded && !multiMatchEnabled) {
            for (int i = 0; i < n; i++) {
                FlowEntry<T> current = entries.get(i);
                if (!processed[i]) {
                    resourceRegistry().releaseGlobalStorage(1);
                    unmatched.add(current);
                }
            }
        }

        if (hasAnyPairSucceeded) {
            for (FlowEntry<T> e : unmatched) {
                e.resetRetryToIneligible();
            }
        }
        boolean skipRetry = hasAnyPairSucceeded || (reason != EgressReason.TIMEOUT && reason != EgressReason.EVICTION);
        for (FlowEntry<T> e : unmatched) {
            handleEgress(key, e, reason, skipRetry);
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
                    c -> {
                    });
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
            FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.STORAGE,
                    "requeue_storage_acquire_interrupted");
            return false;
        }
    }

    private EgressReason mapRemovalCause(RemovalCause cause) {
        if (cause == RemovalCause.EXPIRED) {
            return EgressReason.TIMEOUT;
        }
        if (cause == RemovalCause.SIZE) {
            return EgressReason.EVICTION;
        }
        if (cause == RemovalCause.EXPLICIT && shuttingDown.get()) {
            return EgressReason.SHUTDOWN;
        }
        return null;
    }

    private void handleOverflowDropped(String key, FlowEntry<T> dropped, EgressReason reason) {
        resourceRegistry().releaseGlobalStorage(1);
        handleEgress(key, dropped, reason, true);
        Counter.builder(FlowMetricNames.STORAGE_MULTI_VALUE_DISCARD_TOTAL)
                .tag(FlowMetricNames.TAG_JOB_ID, dropped.getJobId())
                .tag(FlowMetricNames.TAG_REASON, reason.name().toLowerCase())
                .register(meterRegistry())
                .increment();
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
                        resourceRegistry().releaseGlobalStorage(1);
                        handleEgress(key, e, EgressReason.REPLACE, true);
                    }
                }
            } else {
                BiFunction<String, FlowSlot<T>, FlowSlot<T>> slotBiFunction = (k, existing) -> {
                    FlowSlot<T> slot = existing != null ? existing : createNewSlot();
                    Optional<FlowSlot.OverflowResult<T>> overflow = slot.append(entry);
                    overflow.ifPresent(r -> handleOverflowDropped(k, r.entry(), r.reason()));
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
            try (partner; entry) {
                egressHandler().performSingleConsumed(partner, EgressReason.SHUTDOWN);
                egressHandler().performSingleConsumed(entry, EgressReason.SHUTDOWN);
            }
            return;
        }
        FlowLauncher<Object> launcher = launcherLookup.getActiveLauncher(entry.getJobId());
        if (launcher == null) {
            log.warn("No active launcher found for job id {}", entry.getJobId());
            try (partner; entry) {
                egressHandler().performSingleConsumed(partner, EgressReason.SHUTDOWN);
                egressHandler().performSingleConsumed(entry, EgressReason.SHUTDOWN);
            }
            return;
        }
        matchedPairProcessor.processMatchedPair(partner, entry, launcher);
    }

    /**
     * 多候选尝试配对：依次尝试 slot 内候选，直到匹配或全部试完。
     *
     * @param key                聚合键
     * @param entry              待配对的 entry（incoming 或重入 entry）
     * @param matchedPairHandler 匹配成功时的处理逻辑
     * @param onNoMatch          无匹配时的回调（如 handleMatchingMode 需 put，preRetry 可空操作）
     * @return true 数据已保存进缓存，false 数据没有入缓存
     */
    private boolean tryMatchFromSlot(String key,
            FlowEntry<T> entry,
            BiConsumer<FlowEntry<T>, FlowEntry<T>> matchedPairHandler,
            Consumer<PairingContext<T>> onNoMatch) {
        CaffeinePairingContext<T> ctx = new CaffeinePairingContext<>(cache, maxPerKey, perJob,
                this::handleOverflowDropped);
        List<FlowEntry<T>> candidates = new ArrayList<>();
        while (true) {
            Optional<FlowEntry<T>> partner = joiner().getPairingStrategy().findPartner(key, entry, ctx);
            if (partner.isEmpty()) {
                break;
            }
            candidates.add(partner.get());
        }
        boolean matched = false;
        FlowEntry<T> parent = null;
        for (FlowEntry<T> candidate : candidates) {
            matched = joiner().isMatched(candidate.getData(), entry.getData());
            if (matched) {
                matchedPairHandler.accept(candidate, entry);
                parent = candidate;
                break;
            }
        }
        for (FlowEntry<T> next : candidates) {
            if (parent != null && parent.equals(next)) {
                continue;
            }
            if (matched && !perJob.isPairingMultiMatchEnabled()) {
                next.resetRetryToIneligible();
                resourceRegistry().releaseGlobalStorage(1);
                handleEgress(key, next, EgressReason.CLEARED_AFTER_PAIR_SUCCESS, true);
            } else {
                ctx.putBackPartnerAtEnd(key, next);
            }
        }
        if (!matched) {
            onNoMatch.accept(ctx);
        }
        return matched;
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

    /**
     * Source 已结束时将槽位内剩余条目排空并提交给 finalizer（SINGLE_CONSUMED），
     * 使完成阶段剩余数据走正常消费而非 SHUTDOWN/TIMEOUT。
     */
    @Override
    public int drainRemainingToFinalizer() {
        if (shuttingDown.get()) {
            return 0;
        }
        Set<String> keys = Set.copyOf(cache.asMap().keySet());
        int drained = 0;
        for (String key : keys) {
            Lock stripe = KEY_STRIPES[((key.hashCode() & 0x7FFFFFFF) % STRIPE_COUNT)];
            stripe.lock();
            try {
                FlowSlot<T> slot = cache.getIfPresent(key);
                if (slot == null) {
                    continue;
                }
                List<FlowEntry<T>> entries = slot.drainAll();
                if (entries.isEmpty()) {
                    continue;
                }
                cache.invalidate(key);
                for (FlowEntry<T> entry : entries) {
                    resourceRegistry().releaseGlobalStorage(1);
                    handleEgress(key, entry, EgressReason.SINGLE_CONSUMED, false);
                    drained++;
                }
            } finally {
                stripe.unlock();
            }
        }
        return drained;
    }
}
