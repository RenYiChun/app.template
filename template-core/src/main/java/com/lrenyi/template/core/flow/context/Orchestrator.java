package com.lrenyi.template.core.flow.context;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import com.lrenyi.template.core.flow.api.ProgressTracker;
import com.lrenyi.template.core.flow.model.FlowConstants;
import com.lrenyi.template.core.flow.exception.FlowExceptionHelper;
import com.lrenyi.template.core.flow.exception.FlowPhase;
import com.lrenyi.template.core.flow.manager.FlowManager;
import com.lrenyi.template.core.flow.metrics.FlowMetrics;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public record Orchestrator(String jobId, ProgressTracker tracker, Registration registration,
                           FlowResourceContext resourceContext) {
    /**
     * 获取 FlowManager（用于Job状态查询等）
     */
    private FlowManager getManager() {
        return resourceContext.getFlowManager();
    }
    
    /**
     * 还票：归还全局消费席位（显式调用）
     */
    public void release() {
        Semaphore semaphore = resourceContext.getGlobalSemaphore();
        int maxLimit = getManager().getGlobalConfig().getGlobalSemaphoreMaxLimit();
        try {
            if (semaphore.availablePermits() < maxLimit) {
                semaphore.release();
                registration.decrement();
            } else {
                if (registration.getActiveCount().get() > 0) {
                    registration.decrement();
                }
            }
            recordSemaphoreMetrics(semaphore, maxLimit);
        } finally {
            runReleaseHooks();
        }
    }

    /**
     * 消费任务完成时调用：仅执行 release 的 hook（registration、tracker、signal），不释放信号量。
     * 信号量由 FlowGlobalExecutor 在任务结束时统一释放。
     */
    public void releaseWithoutSemaphore() {
        try {
            if (registration.getActiveCount().get() > 0) {
                registration.decrement();
            }
        } finally {
            runReleaseHooks();
        }
    }

    private void recordSemaphoreMetrics(Semaphore semaphore, int maxLimit) {
        int available = semaphore.availablePermits();
        int used = maxLimit - available;
        FlowMetrics.recordResourceUsage("semaphore_used", used);
        FlowMetrics.recordResourceUsage("semaphore_available", available);
    }

    private void runReleaseHooks() {
        tracker.onGlobalTerminated(jobId);
        Lock fairLock = resourceContext.getFairLock();
        fairLock.lock();
        try {
            resourceContext.getPermitReleased().signalAll();
        } finally {
            fairLock.unlock();
        }
    }
    
    /**
     * 获取许可：支持初次 launch 和后续 reacquire
     */
    public void acquire() throws InterruptedException {
        long acquireStartTime = System.currentTimeMillis();
        
        if (getManager().isStopped(jobId)) {
            InterruptedException e = new InterruptedException("Job " + jobId + " is not running.");
            FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.CONSUMPTION);
            FlowMetrics.recordError("acquire_job_stopped", jobId);
            throw e;
        }
        
        int globalSemaphoreMaxLimit = getManager().getGlobalConfig().getGlobalSemaphoreMaxLimit();
        Semaphore semaphore = resourceContext.getGlobalSemaphore();
        
        // 记录信号量使用情况
        int available = semaphore.availablePermits();
        int used = globalSemaphoreMaxLimit - available;
        FlowMetrics.recordResourceUsage("semaphore_used", used);
        FlowMetrics.recordResourceUsage("semaphore_available", available);
        
        // 软性公平退让逻辑（保持现状）
        int activeJobs = getManager().getActiveJobCount();
        int fairShare = Math.max(1, globalSemaphoreMaxLimit / activeJobs);
        int waitCount = 0;
        
        while (registration.getActiveCount().get() >= fairShare && semaphore.availablePermits() == 0) {
            Lock fairLock = resourceContext.getFairLock();
            fairLock.lock();
            try {
                // 再次检查状态，防止在获取锁的过程中条件已经改变（Double Check）
                if (registration.getActiveCount().get() < fairShare || semaphore.availablePermits() > 0) {
                    break;
                }
                // 挂起线程，等待信号。这比 sleep 精准得多
                // 设置超时作为兜底，防止极端情况下的丢失信号
                boolean signalled = resourceContext.getPermitReleased()
                                                   .await(FlowConstants.DEFAULT_FAIR_LOCK_WAIT_MS,
                                                          TimeUnit.MILLISECONDS
                                                   );
                waitCount++;
                if (!signalled) {
                    // 情况 A: 超时到了。
                    // 此时可能没有任务结束，但我们需要醒来重新计算 fairShare，
                    // 因为 activeJobs 数量可能已经变了（可能有新 Job 加入或旧 Job 彻底消失）
                    log.debug("Wait for permit timeout, re-calculating fairShare for Job: {}", jobId);
                } else {
                    // 情况 B: 被其它线程 signal 唤醒。
                    // 说明此时一定有任务归还了许可，这是竞争 globalPool 的最佳时机
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
        //仅在成功持有物理许可后计入活跃消费，使 Active(C) 与信号量持证数一致（≤ globalSemaphoreMaxLimit）
        tracker.onConsumerBegin();
        
        // 记录获取许可的延迟
        long acquireLatency = System.currentTimeMillis() - acquireStartTime;
        FlowMetrics.recordLatency("acquire_permit", acquireLatency);
        if (waitCount > 0) {
            FlowMetrics.incrementCounter("acquire_wait_count", waitCount);
        }
    }
}