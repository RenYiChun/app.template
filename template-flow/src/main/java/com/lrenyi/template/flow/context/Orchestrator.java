package com.lrenyi.template.flow.context;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.exception.FlowExceptionHelper;
import com.lrenyi.template.flow.exception.FlowPhase;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.model.FlowConstants;
import com.lrenyi.template.flow.resource.PermitPair;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public record Orchestrator(String jobId, ProgressTracker tracker, Registration registration,
                           FlowResourceContext resourceContext) {
    
    public void release() {
        PermitPair consumerPair = resourceContext.getConsumerPermitPair();
        int concurrencyLimit = getManager().getGlobalConfig().getLimits().getGlobal().getConsumerThreads();
        try {
            consumerPair.release(1, concurrencyLimit);
            if (registration.getActiveCount().get() > 0) {
                registration.decrement();
            }
        } finally {
            runReleaseHooks();
        }
    }
    
    private FlowManager getManager() {
        return resourceContext.getFlowManager();
    }
    
    private void runReleaseHooks() {
        tracker.onConsumerReleased(jobId);
        
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
    
    public void acquire() throws InterruptedException, TimeoutException {
        long acquireStartTime = System.currentTimeMillis();
        long acquireStartNanos = System.nanoTime();
        var flowConfig = registration.getFlow();
        var mode = flowConfig.getConsumerAcquireBlockingMode();
        long acquireTimeoutMs =
                mode == com.lrenyi.template.core.TemplateConfigProperties.Flow.BackpressureBlockingMode.BLOCK_WITH_TIMEOUT
                        ? flowConfig.getConsumerAcquireTimeoutMill()
                        : 0L;
        long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(acquireTimeoutMs);
        
        FlowManager manager = getManager();
        if (manager == null) {
            IllegalStateException e = new IllegalStateException(
                    "Orchestrator for job " + jobId + " has null FlowManager (resourceContext.getFlowManager()). "
                            + "Ensure launchers are created via FlowManager.createLauncher(); "
                            + "check custom code that may construct Orchestrator or FlowResourceContext with null "
                            + "flowManager.");
            FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.CONSUMPTION, "acquire_manager_null");
            throw e;
        }
        if (manager.isStopped(jobId)) {
            InterruptedException e = new InterruptedException("Job " + jobId + " is not running.");
            FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.CONSUMPTION, "acquire_job_stopped");
            throw e;
        }
        
        PermitPair consumerPair = resourceContext.getConsumerPermitPair();
        int perJobLimit = registration.getFlow().getLimits().getPerJob().getConsumerThreads();
        int fairShare = Math.max(1, perJobLimit);
        
        boolean globalExhausted = consumerPair.getGlobalAvailablePermits() == 0;
        boolean perJobExhausted = consumerPair.getPerJobAvailablePermits() == 0;

        while (registration.getActiveCount().get() >= fairShare && (globalExhausted || perJobExhausted)) {
            Lock fairLock = resourceContext.getFairLock();
            fairLock.lock();
            try {
                globalExhausted = consumerPair.getGlobalAvailablePermits() == 0;
                perJobExhausted = consumerPair.getPerJobAvailablePermits() == 0;
                if (registration.getActiveCount().get() < fairShare || (!globalExhausted && !perJobExhausted)) {
                    break;
                }
                long fairWaitNanos = TimeUnit.MILLISECONDS.toNanos(FlowConstants.DEFAULT_FAIR_LOCK_WAIT_MS);
                if (timeoutNanos > 0) {
                    long elapsedNanos = System.nanoTime() - acquireStartNanos;
                    long remainingNanos = timeoutNanos - elapsedNanos;
                    if (remainingNanos <= 0) {
                        throw buildAcquireTimeoutException(acquireTimeoutMs, acquireStartTime);
                    }
                    fairWaitNanos = Math.min(fairWaitNanos, remainingNanos);
                }
                boolean signalled = resourceContext.getPermitReleased().await(fairWaitNanos, TimeUnit.NANOSECONDS);
                if (!signalled) {
                    log.debug("Wait for permit timeout, re-calculating fairShare for Job: {}", jobId);
                } else {
                    log.debug("Received permit release signal, re-evaluating for Job: {}", jobId);
                }
            } finally {
                fairLock.unlock();
            }
            
            if (manager.isStopped(jobId)) {
                InterruptedException e = new InterruptedException("Job stopped during acquire");
                FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.CONSUMPTION, "acquire_job_stopped");
                throw e;
            }
        }
        
        if (timeoutNanos > 0) {
            long elapsedNanos = System.nanoTime() - acquireStartNanos;
            long remainingNanos = timeoutNanos - elapsedNanos;
            if (remainingNanos <= 0) {
                throw buildAcquireTimeoutException(acquireTimeoutMs, acquireStartTime);
            }
            if (!consumerPair.tryAcquireBoth(1, remainingNanos, TimeUnit.NANOSECONDS)) {
                throw buildAcquireTimeoutException(acquireTimeoutMs, acquireStartTime);
            }
        } else {
            if (!consumerPair.tryAcquireBoth(1)) {
                throw buildAcquireTimeoutException(acquireTimeoutMs, acquireStartTime);
            }
        }
        registration.increment();
        tracker.onConsumerAcquired();

        long acquireLatency = System.currentTimeMillis() - acquireStartTime;
        Timer.builder(FlowMetricNames.LIMITS_ACQUIRE_WAIT_DURATION)
             .tag(FlowMetricNames.TAG_JOB_ID, jobId)
             .tag(FlowMetricNames.TAG_DIMENSION, FlowMetricNames.DIMENSION_CONSUMER_CONCURRENCY)
             .register(registry())
             .record(acquireLatency, TimeUnit.MILLISECONDS);
    }
    
    private TimeoutException buildAcquireTimeoutException(long acquireTimeoutMs, long acquireStartTime) {
        long waitedMs = System.currentTimeMillis() - acquireStartTime;
        TimeoutException timeoutException = new TimeoutException(
                "Consumer permit acquire timeout for job " + jobId + ", acquireTimeoutMs=" + acquireTimeoutMs
                        + ", waitedMs=" + waitedMs);
        FlowExceptionHelper.handleException(jobId, null, timeoutException, FlowPhase.CONSUMPTION, "acquire_timeout");
        return timeoutException;
    }
    
}
