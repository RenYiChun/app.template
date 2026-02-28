package com.lrenyi.template.flow.context;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.exception.FlowExceptionHelper;
import com.lrenyi.template.flow.exception.FlowPhase;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.model.FlowConstants;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public record Orchestrator(String jobId, ProgressTracker tracker, Registration registration,
                           FlowResourceContext resourceContext) {
    
    public void release() {
        Semaphore semaphore = resourceContext.getGlobalSemaphore();
        int concurrencyLimit = getManager().getGlobalConfig().getConsumer().getConcurrencyLimit();
        try {
            if (semaphore.availablePermits() < concurrencyLimit) {
                semaphore.release();
                registration.decrement();
            } else {
                if (registration.getActiveCount().get() > 0) {
                    registration.decrement();
                }
            }
        } finally {
            runReleaseHooks();
        }
    }
    
    private FlowManager getManager() {
        return resourceContext.getFlowManager();
    }
    
    private void runReleaseHooks() {
        tracker.onGlobalTerminated(jobId);
        
        Counter.builder(FlowMetricNames.TERMINATED)
               .tag(FlowMetricNames.TAG_JOB_ID, jobId)
               .register(registry())
               .increment();
        
        Lock fairLock = resourceContext.getFairLock();
        fairLock.lock();
        try {
            resourceContext.getPermitReleased().signalAll();
        } finally {
            fairLock.unlock();
        }
    }
    
    private MeterRegistry registry() {
        return getManager().getMeterRegistry();
    }
    
    public void releaseWithoutSemaphore() {
        try {
            if (registration.getActiveCount().get() > 0) {
                registration.decrement();
            }
        } finally {
            runReleaseHooks();
        }
    }
    
    public void acquire() throws InterruptedException {
        long acquireStartTime = System.currentTimeMillis();
        
        if (getManager().isStopped(jobId)) {
            InterruptedException e = new InterruptedException("Job " + jobId + " is not running.");
            FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.CONSUMPTION);
            Counter.builder(FlowMetricNames.ERRORS)
                   .tag(FlowMetricNames.TAG_ERROR_TYPE, "acquire_job_stopped")
                   .tag(FlowMetricNames.TAG_PHASE, "CONSUMPTION")
                   .register(registry())
                   .increment();
            throw e;
        }
        
        int concurrencyLimit = getManager().getGlobalConfig().getConsumer().getConcurrencyLimit();
        Semaphore semaphore = resourceContext.getGlobalSemaphore();
        
        int activeJobs = getManager().getActiveJobCount();
        int fairShare = Math.max(1, concurrencyLimit / activeJobs);
        
        while (registration.getActiveCount().get() >= fairShare && semaphore.availablePermits() == 0) {
            Lock fairLock = resourceContext.getFairLock();
            fairLock.lock();
            try {
                if (registration.getActiveCount().get() < fairShare || semaphore.availablePermits() > 0) {
                    break;
                }
                boolean signalled = resourceContext.getPermitReleased()
                                                   .await(FlowConstants.DEFAULT_FAIR_LOCK_WAIT_MS,
                                                          TimeUnit.MILLISECONDS
                                                   );
                if (!signalled) {
                    log.debug("Wait for permit timeout, re-calculating fairShare for Job: {}", jobId);
                } else {
                    log.debug("Received permit release signal, re-evaluating for Job: {}", jobId);
                }
            } finally {
                fairLock.unlock();
            }
            
            if (getManager().isStopped(jobId)) {
                InterruptedException e = new InterruptedException("Job stopped during acquire");
                FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.CONSUMPTION);
                throw e;
            }
        }
        
        semaphore.acquire();
        registration.increment();
        tracker.onConsumerBegin();
        
        long acquireLatency = System.currentTimeMillis() - acquireStartTime;
        Timer.builder(FlowMetricNames.ACQUIRE_DURATION)
             .tag(FlowMetricNames.TAG_JOB_ID, jobId)
             .register(registry())
             .record(acquireLatency, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 注册全局信号量 Gauge（仅在初始化时调用一次）
     */
    public void registerSemaphoreGauges() {
        Semaphore semaphore = resourceContext.getGlobalSemaphore();
        int concurrencyLimit = getManager().getGlobalConfig().getConsumer().getConcurrencyLimit();
        
        Gauge.builder(FlowMetricNames.SEMAPHORE_USED, semaphore, s -> concurrencyLimit - s.availablePermits())
             .description("全局消费信号量已占用许可数")
             .register(registry());
        
        Gauge.builder(FlowMetricNames.SEMAPHORE_LIMIT, () -> concurrencyLimit)
             .description("全局消费信号量上限")
             .register(registry());
    }
}
