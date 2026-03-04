package com.lrenyi.template.flow.storage;

import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
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
import com.lrenyi.template.flow.context.Orchestrator;
import com.lrenyi.template.flow.exception.FlowExceptionHelper;
import com.lrenyi.template.flow.exception.FlowPhase;
import com.lrenyi.template.flow.internal.FlowFinalizer;
import com.lrenyi.template.flow.internal.FlowLauncher;
import com.lrenyi.template.flow.internal.MatchRetryCoordinator;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.model.FailureReason;
import com.lrenyi.template.flow.resource.ActiveLauncherLookup;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于 Caffeine 实现的 Key-Value 流式存储
 *
 * @param <T> 存储的数据类型
 */
@Slf4j
public class CaffeineFlowStorage<T> implements FlowStorage<T> {
    private static final String CONSUMPTION = "CONSUMPTION";
    private static final int STRIPE_COUNT = 256;
    private static final Lock[] KEY_STRIPES = new Lock[STRIPE_COUNT];
    
    static {
        for (int i = 0; i < STRIPE_COUNT; i++) {
            KEY_STRIPES[i] = new ReentrantLock();
        }
    }
    
    @Getter
    private final FlowFinalizer<T> finalizer;
    private final Cache<String, FlowEntry<T>> cache;
    private final FlowJoiner<T> joiner;
    private final ProgressTracker progressTracker;
    private final long maxCacheSize;
    private final FlowResourceRegistry resourceRegistry;
    private final MeterRegistry meterRegistry;
    private final LongAdder removalSubmittedCount = new LongAdder();
    
    public CaffeineFlowStorage(long maxSize,
            long ttlMill,
            FlowJoiner<T> joiner,
            FlowFinalizer<T> finalizer,
            ProgressTracker progressTracker,
            MeterRegistry meterRegistry,
            String jobId) {
        this.joiner = joiner;
        this.finalizer = finalizer;
        this.progressTracker = progressTracker;
        this.maxCacheSize = maxSize;
        this.resourceRegistry = finalizer.resourceRegistry();
        this.meterRegistry = meterRegistry;
        this.cache = Caffeine.newBuilder()
                             .maximumSize(maxSize)
                             .expireAfterWrite(ttlMill, TimeUnit.MILLISECONDS)
                             .executor(resourceRegistry.getCacheRemovalExecutor())
                             .removalListener((String key, FlowEntry<T> entry, RemovalCause cause) -> onEntryRemoved(
                                     key,
                                     entry,
                                     cause
                             ))
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
        resourceRegistry.releaseGlobalStorage(1);
        if (entry == null) {
            return;
        }
        FailureReason reason = mapRemovalCause(cause);
        if (reason == null) {
            entry.close();
            return;
        }
        ActiveLauncherLookup launcherLookup = resourceRegistry.getLauncherLookup();
        if (launcherLookup == null) {
            log.warn("LauncherLookup not available for entry removal, jobId={}", entry.getJobId());
            Counter.builder(FlowMetricNames.ERRORS)
                   .tag(FlowMetricNames.TAG_ERROR_TYPE, "flow_manager_unavailable")
                   .tag(FlowMetricNames.TAG_PHASE, "STORAGE")
                   .register(meterRegistry)
                   .increment();
            handlePassiveFailure(entry, reason);
            return;
        }
        FlowLauncher<Object> launcher = launcherLookup.getActiveLauncher(entry.getJobId());
        try {
            if (launcher == null) {
                handlePassiveFailure(entry, reason);
                return;
            }
            if (tryRetryBeforeFailure(key, entry, reason, launcher)) {
                return;
            }
            handlePassiveFailure(entry, reason);
        } finally {
            if (launcher != null) {
                launcher.getBackpressureController().signalRelease();
            }
        }
    }
    
    private boolean tryRetryBeforeFailure(String key,
            FlowEntry<T> entry,
            FailureReason reason,
            FlowLauncher<Object> launcher) {
        MatchRetryCoordinator<T> retryCoordinator = new MatchRetryCoordinator<>(entry.getJobId(),
                                                                                launcher.getFlow().getLimits().getPerJob(),
                                                                                joiner,
                                                                                launcher.getFlowManager(),
                                                                                meterRegistry
        );
        if (!retryCoordinator.tryConsumeRetry(reason, entry)) {
            return false;
        }
        long backoffMill = launcher.getFlow().getLimits().getPerJob().getMustMatchRetryBackoffMill();
        if (backoffMill > 0) {
            try {
                Thread.sleep(backoffMill);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                FlowExceptionHelper.handleException(entry.getJobId(), null, e, FlowPhase.STORAGE,
                        "retry_backoff_interrupted");
                return false;
            }
        }
        if (tryMatchThenRequeue(key, entry, launcher)) {
            retryCoordinator.onRetrySucceeded(reason);
            return true;
        }
        return false;
    }
    
    private boolean tryMatchThenRequeue(String key, FlowEntry<T> entry, FlowLauncher<Object> launcher) {
        Lock stripe = KEY_STRIPES[((key.hashCode() & 0x7FFFFFFF) % STRIPE_COUNT)];
        stripe.lock();
        try {
            FlowEntry<T> partner = findAndRemoveExisting(key);
            if (partner != null) {
                processMatchedPairFromRetry(partner, entry, launcher);
                return true;
            }
            if (!acquireGlobalStorageForRequeue(entry.getJobId())) {
                return false;
            }
            boolean requeued = requeue(entry);
            if (!requeued) {
                resourceRegistry.releaseGlobalStorage(1);
            }
            if (requeued) {
                entry.close();
            }
            return requeued;
        } finally {
            stripe.unlock();
        }
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
        Semaphore globalStorage = resourceRegistry.getGlobalStorageSemaphore();
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
        return null;
    }
    
    private void handlePassiveFailure(FlowEntry<T> entry, FailureReason reason) {
        try (entry) {
            joiner.onFailed(entry.getData(), entry.getJobId(), reason);
            Counter.builder(FlowMetricNames.EGRESS_PASSIVE)
                   .tag(FlowMetricNames.TAG_JOB_ID, entry.getJobId())
                   .tag(FlowMetricNames.TAG_REASON, reason.name())
                   .register(meterRegistry)
                   .increment();
        } catch (Exception e) {
            FlowExceptionHelper.handleException(entry.getJobId(), null, e, FlowPhase.STORAGE, "eviction_process_failed");
        } finally {
            progressTracker.onPassiveEgress(reason);
        }
    }
    
    @Override
    public boolean doDeposit(FlowEntry<T> entry) {
        String key = joiner.joinKey(entry.getData());
        
        if (!joiner.needMatched()) {
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
        resourceRegistry.releaseGlobalStorage(1);
        try (oldEntry) {
            joiner.onFailed(oldEntry.getData(), oldEntry.getJobId(), FailureReason.REPLACE);
            Counter.builder(FlowMetricNames.EGRESS_PASSIVE)
                   .tag(FlowMetricNames.TAG_JOB_ID, oldEntry.getJobId())
                   .tag(FlowMetricNames.TAG_REASON, "REPLACE")
                   .register(meterRegistry)
                   .increment();
        } catch (Exception e) {
            FlowExceptionHelper.handleException(oldEntry.getJobId(), null, e, FlowPhase.STORAGE,
                    "replace_process_failed");
        } finally {
            progressTracker.onPassiveEgress(FailureReason.REPLACE);
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
        resourceRegistry.releaseGlobalStorage(1);
        ActiveLauncherLookup launcherLookup = resourceRegistry.getLauncherLookup();
        if (launcherLookup == null) {
            log.warn("LauncherLookup not available for job {}", entry.getJobId());
            Counter.builder(FlowMetricNames.ERRORS)
                   .tag(FlowMetricNames.TAG_ERROR_TYPE, "flow_manager_unavailable")
                   .tag(FlowMetricNames.TAG_PHASE, CONSUMPTION)
                   .register(meterRegistry)
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
        long matchStartTime = System.currentTimeMillis();
        Orchestrator taskOrchestrator = launcher.getTaskOrchestrator();
        Runnable runnable = () -> {
            try {
                executeMatchedPairLogicBody(partner, entry);
                long matchLatency = System.currentTimeMillis() - matchStartTime;
                Timer.builder(FlowMetricNames.MATCH_DURATION)
                     .tag(FlowMetricNames.TAG_JOB_ID, entry.getJobId())
                     .register(meterRegistry)
                     .record(matchLatency, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                FlowExceptionHelper.handleException(entry.getJobId(), null, e, FlowPhase.CONSUMPTION);
                Counter.builder(FlowMetricNames.ERRORS)
                       .tag(FlowMetricNames.TAG_ERROR_TYPE, "match_process_failed")
                       .tag(FlowMetricNames.TAG_PHASE, CONSUMPTION)
                       .register(meterRegistry)
                       .increment();
            } finally {
                launcher.getBackpressureController().signalRelease();
            }
        };
        resourceRegistry.submitConsumerToGlobal(taskOrchestrator, 2, runnable);
    }
    
    private void processMatchedPairFromRetry(FlowEntry<T> partner, FlowEntry<T> entry, FlowLauncher<Object> launcher) {
        resourceRegistry.releaseGlobalStorage(1);
        long matchStartTime = System.currentTimeMillis();
        Orchestrator taskOrchestrator = launcher.getTaskOrchestrator();
        Runnable runnable = () -> {
            try (partner; entry) {
                if (joiner.isMatched(partner.getData(), entry.getData())) {
                    handleMatchedSuccess(partner, entry);
                } else {
                    handleMatchedFailure(partner, entry);
                }
                long matchLatency = System.currentTimeMillis() - matchStartTime;
                Timer.builder(FlowMetricNames.MATCH_DURATION)
                     .tag(FlowMetricNames.TAG_JOB_ID, entry.getJobId())
                     .register(meterRegistry)
                     .record(matchLatency, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                FlowExceptionHelper.handleException(entry.getJobId(), null, e, FlowPhase.CONSUMPTION,
                        "retry_match_process_failed");
            } finally {
                launcher.getBackpressureController().signalRelease();
            }
        };
        resourceRegistry.submitConsumerToGlobal(taskOrchestrator, 2, runnable);
    }
    
    private void executeMatchedPairLogicBody(FlowEntry<T> partner, FlowEntry<T> entry) {
        try (partner) {
            if (joiner.isMatched(partner.getData(), entry.getData())) {
                handleMatchedSuccess(partner, entry);
            } else {
                handleMatchedFailure(partner, entry);
            }
        }
    }
    
    private void handleMatchedSuccess(FlowEntry<T> partner, FlowEntry<T> entry) {
        progressTracker.onActiveEgress();
        progressTracker.onActiveEgress();
        Counter.builder(FlowMetricNames.EGRESS_ACTIVE)
               .tag(FlowMetricNames.TAG_JOB_ID, entry.getJobId())
               .register(meterRegistry)
               .increment(2);
        try {
            joiner.onSuccess(partner.getData(), entry.getData(), entry.getJobId());
        } catch (Exception e) {
            FlowExceptionHelper.handleException(entry.getJobId(), null, e, FlowPhase.CONSUMPTION, "onSuccess_failed");
        }
    }
    
    private void handleMatchedFailure(FlowEntry<T> partner, FlowEntry<T> entry) {
        try {
            joiner.onFailed(partner.getData(), partner.getJobId(), FailureReason.MISMATCH);
            joiner.onFailed(entry.getData(), entry.getJobId(), FailureReason.MISMATCH);
            Counter.builder(FlowMetricNames.EGRESS_PASSIVE)
                   .tag(FlowMetricNames.TAG_JOB_ID, partner.getJobId())
                   .tag(FlowMetricNames.TAG_REASON, "MISMATCH")
                   .register(meterRegistry)
                   .increment();
            Counter.builder(FlowMetricNames.EGRESS_PASSIVE)
                   .tag(FlowMetricNames.TAG_JOB_ID, entry.getJobId())
                   .tag(FlowMetricNames.TAG_REASON, "MISMATCH")
                   .register(meterRegistry)
                   .increment();
        } catch (Exception e) {
            FlowExceptionHelper.handleException(entry.getJobId(), null, e, FlowPhase.CONSUMPTION,
                    "mismatch_process_failed");
        }
        progressTracker.onPassiveEgress(FailureReason.MISMATCH);
        progressTracker.onPassiveEgress(FailureReason.MISMATCH);
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
