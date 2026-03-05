package com.lrenyi.template.flow.context;

import java.util.concurrent.Semaphore;
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
        Semaphore semaphore = resourceContext.getGlobalSemaphore();
        int concurrencyLimit = getManager().getGlobalConfig().getLimits().getGlobal().getConsumerConcurrency();
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
    
    public void acquire() throws InterruptedException, TimeoutException {
        long acquireStartTime = System.currentTimeMillis();
        long acquireStartNanos = System.nanoTime();
        long acquireTimeoutMs = FlowConstants.DEFAULT_ACQUIRE_TIMEOUT_MS;
        long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(acquireTimeoutMs);

        if (getManager().isStopped(jobId)) {
            InterruptedException e = new InterruptedException("Job " + jobId + " is not running.");
            FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.CONSUMPTION, "acquire_job_stopped");
            throw e;
        }
        
        Semaphore globalSemaphore = resourceContext.getGlobalSemaphore();
        Semaphore perJobSemaphore = resourceContext.getJobConsumerSemaphore();
        int perJobLimit = registration.getFlow().getLimits().getPerJob().getConsumerConcurrency();
        int fairShare = Math.max(1, perJobLimit);
        
        boolean globalExhausted = globalSemaphore.availablePermits() == 0;
        boolean perJobExhausted = perJobSemaphore != null && perJobSemaphore.availablePermits() == 0;
        
        while (registration.getActiveCount().get() >= fairShare && (globalExhausted || perJobExhausted)) {
            Lock fairLock = resourceContext.getFairLock();
            fairLock.lock();
            try {
                globalExhausted = globalSemaphore.availablePermits() == 0;
                perJobExhausted = perJobSemaphore != null && perJobSemaphore.availablePermits() == 0;
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
            
            if (getManager().isStopped(jobId)) {
                InterruptedException e = new InterruptedException("Job stopped during acquire");
                FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.CONSUMPTION, "acquire_job_stopped");
                throw e;
            }
        }
        
        if (perJobSemaphore != null) {
            boolean acquired;
            if (timeoutNanos > 0) {
                long elapsedNanos = System.nanoTime() - acquireStartNanos;
                long remainingNanos = timeoutNanos - elapsedNanos;
                if (remainingNanos <= 0) {
                    throw buildAcquireTimeoutException(acquireTimeoutMs, acquireStartTime);
                }
                acquired = PermitPair.tryAcquireBoth(globalSemaphore,
                                                     perJobSemaphore,
                                                     1,
                                                     remainingNanos,
                                                     TimeUnit.NANOSECONDS
                );
            } else {
                acquired = PermitPair.tryAcquireBoth(globalSemaphore, perJobSemaphore, 1);
            }
            if (!acquired) {
                throw buildAcquireTimeoutException(acquireTimeoutMs, acquireStartTime);
            }
        } else {
            if (timeoutNanos > 0) {
                long elapsedNanos = System.nanoTime() - acquireStartNanos;
                long remainingNanos = timeoutNanos - elapsedNanos;
                if (remainingNanos <= 0 || !globalSemaphore.tryAcquire(1, remainingNanos, TimeUnit.NANOSECONDS)) {
                    throw buildAcquireTimeoutException(acquireTimeoutMs, acquireStartTime);
                }
            } else {
                globalSemaphore.acquire();
            }
        }
        registration.increment();
        tracker.onConsumerBegin();

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
