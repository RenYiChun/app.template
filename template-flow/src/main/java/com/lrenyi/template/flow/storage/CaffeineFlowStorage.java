package com.lrenyi.template.flow.storage;

import java.util.Optional;
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
                                     entry,
                                     cause
                             ))
                             .scheduler(Scheduler.systemScheduler())
                             .build();
        
        Gauge.builder(FlowMetricNames.STORAGE_SIZE, cache, Cache::estimatedSize)
             .tag(FlowMetricNames.TAG_JOB_ID, jobId)
             .tag(FlowMetricNames.TAG_STORAGE_TYPE, "caffeine")
             .description("当前 Caffeine 缓存中的数据条数")
             .register(meterRegistry);
    }
    
    private void onEntryRemoved(FlowEntry<T> entry, RemovalCause cause) {
        ActiveLauncherLookup launcherLookup = resourceRegistry.getLauncherLookup();
        if (launcherLookup == null) {
            log.warn("LauncherLookup not available for entry removal, jobId={}", entry.getJobId());
            Counter.builder(FlowMetricNames.ERRORS)
                   .tag(FlowMetricNames.TAG_ERROR_TYPE, "flow_manager_unavailable")
                   .tag(FlowMetricNames.TAG_PHASE, "STORAGE")
                   .register(meterRegistry)
                   .increment();
            return;
        }
        FlowLauncher<Object> launcher = launcherLookup.getActiveLauncher(entry.getJobId());
        try (entry) {
            if (!cause.wasEvicted() || launcher == null) {
                return;
            }
            removalSubmittedCount.increment();
            entry.setRemovalReason(cause.name());
            finalizer.submitBodyOnly(entry, launcher);
        } finally {
            if (launcher != null) {
                launcher.getBackpressureController().signalRelease();
            }
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
        try (oldEntry) {
            joiner.onFailed(oldEntry.getData(), oldEntry.getJobId(), FailureReason.REPLACE);
            Counter.builder(FlowMetricNames.EGRESS_PASSIVE)
                   .tag(FlowMetricNames.TAG_JOB_ID, oldEntry.getJobId())
                   .tag(FlowMetricNames.TAG_REASON, "REPLACE")
                   .register(meterRegistry)
                   .increment();
        } catch (Exception e) {
            FlowExceptionHelper.handleException(oldEntry.getJobId(), null, e, FlowPhase.STORAGE);
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
            FlowExceptionHelper.handleException(entry.getJobId(), null, e, FlowPhase.CONSUMPTION);
            Counter.builder(FlowMetricNames.ERRORS)
                   .tag(FlowMetricNames.TAG_ERROR_TYPE, "onSuccess_failed")
                   .tag(FlowMetricNames.TAG_PHASE, CONSUMPTION)
                   .register(meterRegistry)
                   .increment();
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
            FlowExceptionHelper.handleException(entry.getJobId(), null, e, FlowPhase.CONSUMPTION);
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
