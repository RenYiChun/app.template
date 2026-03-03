package com.lrenyi.template.flow.internal;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.context.FlowResourceContext;
import com.lrenyi.template.flow.context.Orchestrator;
import com.lrenyi.template.flow.context.Registration;
import com.lrenyi.template.flow.exception.FlowExceptionHelper;
import com.lrenyi.template.flow.exception.FlowPhase;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.model.FailureReason;
import com.lrenyi.template.flow.model.FlowStorageType;
import com.lrenyi.template.flow.storage.FlowStorage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * 流量发射器 - 动态配置版
 */
@Slf4j
@Getter
@Setter
public class FlowLauncher<T> {
    private static final String PHASE_PRODUCTION = "PRODUCTION";
    private final AtomicInteger counter = new AtomicInteger(0);
    private final String jobId;
    private final Orchestrator taskOrchestrator;
    private final FlowStorage<T> storage;
    private final FlowManager flowManager;
    private final FlowJoiner<T> flowJoiner;
    private final Semaphore jobProducerSemaphore;
    private final TemplateConfigProperties.Flow flow;
    private final BackpressureController backpressureController;
    private final FlowResourceContext resourceContext;
    private volatile boolean stopped = false;
    
    private FlowLauncher(String jobId,
            FlowManager flowManager,
            FlowJoiner<T> flowJoiner,
            ProgressTracker tracker,
            Registration registration,
            FlowResourceContext resourceContext) {
        this.jobId = jobId;
        this.flowManager = flowManager;
        this.flowJoiner = flowJoiner;
        this.resourceContext = resourceContext;
        this.flow = registration.getFlow();
        this.jobProducerSemaphore = resourceContext.getJobProducerSemaphore();
        this.storage = (FlowStorage<T>) resourceContext.getStorage();
        this.backpressureController = resourceContext.getBackpressureController();
        this.taskOrchestrator = new Orchestrator(jobId, tracker, registration, resourceContext);
    }
    
    public static <T> FlowLauncher<T> create(String jobId,
            FlowJoiner<T> flowJoiner,
            FlowManager flowManager,
            ProgressTracker tracker,
            Registration registration,
            FlowResourceContext resourceContext) {
        
        return new FlowLauncher<>(jobId, flowManager, flowJoiner, tracker, registration, resourceContext);
    }
    
    public void launch(T data) {
        ProgressTracker tracker = taskOrchestrator.tracker();
        if (stopped) {
            Counter.builder(FlowMetricNames.ERRORS)
                   .tag(FlowMetricNames.TAG_ERROR_TYPE, "job_stopped")
                   .tag(FlowMetricNames.TAG_PHASE, PHASE_PRODUCTION)
                   .register(registry())
                   .increment();
            return;
        }
        
        Semaphore inFlight = resourceContext.getInFlightProductionSemaphore();
        if (inFlight != null) {
            try {
                inFlight.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.PRODUCTION);
                Counter.builder(FlowMetricNames.ERRORS)
                       .tag(FlowMetricNames.TAG_ERROR_TYPE, "inFlight_acquire_interrupted")
                       .tag(FlowMetricNames.TAG_PHASE, PHASE_PRODUCTION)
                       .register(registry())
                       .increment();
                return;
            }
        }
        
        tracker.onProductionAcquired();
        Counter.builder(FlowMetricNames.PRODUCTION_ACQUIRED)
               .tag(FlowMetricNames.TAG_JOB_ID, jobId)
               .register(registry())
               .increment();
        
        if (stopped) {
            tracker.onProductionReleased();
            if (inFlight != null) {
                inFlight.release();
            }
            Counter.builder(FlowMetricNames.ERRORS)
                   .tag(FlowMetricNames.TAG_ERROR_TYPE, "job_stopped")
                   .tag(FlowMetricNames.TAG_PHASE, PHASE_PRODUCTION)
                   .register(registry())
                   .increment();
            return;
        }
        
        try {
            awaitBackpressure();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            tracker.onProductionReleased();
            if (inFlight != null) {
                inFlight.release();
            }
            FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.PRODUCTION);
            Counter.builder(FlowMetricNames.ERRORS)
                   .tag(FlowMetricNames.TAG_ERROR_TYPE, "backpressure_interrupted")
                   .tag(FlowMetricNames.TAG_PHASE, PHASE_PRODUCTION)
                   .register(registry())
                   .increment();
            return;
        }
        
        submitDepositTask(data, tracker, inFlight);
    }
    
    private MeterRegistry registry() {
        return flowManager.getMeterRegistry();
    }
    
    private void awaitBackpressure() throws InterruptedException {
        if (stopped) {
            return;
        }
        try {
            backpressureController.awaitSpace(() -> stopped);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }
    
    private void submitDepositTask(T data, ProgressTracker tracker, Semaphore inFlight) {
        getProducerExecutor().execute(() -> {
            try (FlowEntry<T> ctx = new FlowEntry<>(data, jobId)) {
                if (stopped) {
                    tracker.onPassiveEgress(FailureReason.SHUTDOWN);
                    flowJoiner.onFailed(data, jobId, FailureReason.SHUTDOWN);
                    Counter.builder(FlowMetricNames.EGRESS_PASSIVE)
                           .tag(FlowMetricNames.TAG_JOB_ID, jobId)
                           .tag(FlowMetricNames.TAG_REASON, "SHUTDOWN")
                           .register(registry())
                           .increment();
                    return;
                }
                
                long depositStartTime = System.currentTimeMillis();
                getStorage().deposit(ctx);
                long depositLatency = System.currentTimeMillis() - depositStartTime;

                Counter.builder(FlowMetricNames.PRODUCTION_RELEASED)
                       .tag(FlowMetricNames.TAG_JOB_ID, jobId)
                       .register(registry())
                       .increment();
                
                Timer.builder(FlowMetricNames.DEPOSIT_DURATION)
                     .tag(FlowMetricNames.TAG_JOB_ID, jobId)
                     .register(registry())
                     .record(depositLatency, TimeUnit.MILLISECONDS);
            } catch (Throwable e) {
                FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.STORAGE);
                Counter.builder(FlowMetricNames.ERRORS)
                       .tag(FlowMetricNames.TAG_ERROR_TYPE, "deposit_failed")
                       .tag(FlowMetricNames.TAG_PHASE, "STORAGE")
                       .register(registry())
                       .increment();
            } finally {
                tracker.onProductionReleased();
                if (inFlight != null) {
                    inFlight.release();
                }
            }
        });
    }
    
    public long getCacheCapacity() {
        return flow.getProducer().getMaxCacheSize();
    }
    
    public ExecutorService getProducerExecutor() {
        return resourceContext.getProducerExecutor();
    }
    
    public void stop(boolean force) {
        if (stopped) {
            return;
        }
        log.info("停止 Job [{}], force={}", jobId, force);
        this.stopped = true;
        taskOrchestrator.tracker().markSourceFinished(jobId);
        try {
            FlowStorageType type = flowJoiner.getStorageType();
            resourceContext.getCacheManager().invalidateByJobId(jobId, type, flowJoiner.getDataType().getSimpleName());
        } catch (Exception e) {
            log.error("Job [{}] 停止时清理 Storage 失败", jobId, e);
        }
        flowManager.unregister(jobId);
    }
}
