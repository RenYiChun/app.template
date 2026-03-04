package com.lrenyi.template.flow.storage;

import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Scheduler;
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
 * 基于 Caffeine 实现的 Key-Value 流式存储
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
    private final Cache<String, FlowEntry<T>> cache;
    private final long maxCacheSize;
    private final MatchedPairProcessor<T> matchedPairProcessor;
    private final LongAdder removalSubmittedCount = new LongAdder();
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    
    public CaffeineFlowStorage(long maxSize,
            long ttlMill,
            FlowJoiner<T> joiner,
            FlowFinalizer<T> finalizer,
            ProgressTracker progressTracker,
            MeterRegistry meterRegistry,
            String jobId) {
        super(joiner, finalizer, progressTracker, meterRegistry);
        this.maxCacheSize = maxSize;
        FlowResourceRegistry resourceRegistry = resourceRegistry();
        this.matchedPairProcessor =
                new MatchedPairProcessor<>(joiner, progressTracker, meterRegistry, resourceRegistry);
        this.cache = Caffeine.newBuilder()
                             .maximumSize(maxSize)
                             .expireAfterWrite(ttlMill, TimeUnit.MILLISECONDS)
                             .executor(resourceRegistry.getCacheRemovalExecutor())
                             .removalListener(this::onEntryRemoved)
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
    
    private void onEntryRemoved(String key, FlowEntry<T> entry, RemovalCause cause) {
        resourceRegistry().releaseGlobalStorage(1);
        FailureReason reason = mapRemovalCause(cause);
        if (reason == FailureReason.SHUTDOWN) {
            if (entry != null) {
                handlePassiveFailure(entry, reason);
            }
            return;
        }
        handleEgress(key, entry, reason);
    }
    
    @Override
    public PreRetryResult preRetry(String key, FlowEntry<T> entry, FlowLauncher<Object> launcher) {
        Lock stripe = KEY_STRIPES[((key.hashCode() & 0x7FFFFFFF) % STRIPE_COUNT)];
        stripe.lock();
        try {
            FlowEntry<T> partner = findAndRemoveExisting(key);
            if (partner != null) {
                matchedPairProcessor.processMatchedPair(partner, entry, launcher);
                return PreRetryResult.HANDLED;
            }
            return PreRetryResult.PROCEED_TO_REQUEUE;
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
    
    private FlowEntry<T> findAndRemoveExisting(String key) {
        final AtomicReference<FlowEntry<T>> matchFound = new AtomicReference<>();
        cache.asMap().computeIfPresent(key, (k, existing) -> {
            matchFound.set(existing);
            return null;
        });
        return matchFound.get();
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
        FlowEntry<T> oldEntry = cache.asMap().put(key, entry);
        Optional.ofNullable(oldEntry).ifPresent(this::handleReplacedEntry);
        return true;
    }
    
    private boolean handleMatchingMode(String key, FlowEntry<T> entry) {
        FlowEntry<T> partner = findAndRemovePartner(key, entry);
        if (partner == null) {
            return true;
        }
        
        processMatchedPair(partner, entry);
        return false;
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
    
    private FlowEntry<T> findAndRemovePartner(String key, FlowEntry<T> entry) {
        Lock stripe = KEY_STRIPES[((key.hashCode() & 0x7FFFFFFF) % STRIPE_COUNT)];
        stripe.lock();
        try {
            final AtomicReference<FlowEntry<T>> matchFound = new AtomicReference<>();
            cache.asMap().compute(key, (k, existing) -> {
                                      if (existing != null) {
                                          matchFound.set(existing);
                                          return null;
                                      }
                                      return entry;
                                  }
            );
            return matchFound.get();
        } finally {
            stripe.unlock();
        }
    }
    
    private void processMatchedPair(FlowEntry<T> partner, FlowEntry<T> entry) {
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
