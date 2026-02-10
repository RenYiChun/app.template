package com.lrenyi.template.core.flow.context;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import com.lrenyi.template.core.flow.FlowConstants;
import com.lrenyi.template.core.flow.ProgressTracker;
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
                // 兜底：即便不释放信号量，也要减掉注册计数，保持内存状态一致
                if (registration.getActiveCount().get() > 0) {
                    registration.decrement();
                }
            }
            
            // 记录信号量使用情况
            int available = semaphore.availablePermits();
            int used = maxLimit - available;
            FlowMetrics.recordResourceUsage("semaphore_used", used);
            FlowMetrics.recordResourceUsage("semaphore_available", available);
            
        } finally {
            // 无论物理释放是否溢出，只要这一单结束了，就必须触发终结信号
            // 这会使 ProgressTracker 中的 activeConsumers 递减，并检查任务是否可以彻底结束
            tracker.onGlobalTerminated(jobId);
            // 唤醒公平锁等待者
            Lock fairLock = resourceContext.getFairLock();
            fairLock.lock();
            try {
                resourceContext.getPermitReleased().signalAll();
            } finally {
                fairLock.unlock();
            }
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