package com.lrenyi.template.core.flow.context;

import com.lrenyi.template.core.flow.FlowJoiner;
import com.lrenyi.template.core.flow.ProgressTracker;
import com.lrenyi.template.core.flow.manager.FlowManager;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class Orchestrator<T> {
    private final String jobId;
    private final ProgressTracker tracker;
    private final Registration registration;
    private final FlowManager manager;
    
    public Orchestrator(String jobId, ProgressTracker tracker, Registration registration, FlowManager flowManager) {
        this.jobId = jobId;
        this.tracker = tracker;
        this.registration = registration;
        this.manager = flowManager;
    }
    
    /**
     * 还票：归还全局消费席位（显式调用）
     */
    public void release() {
        Semaphore semaphore = manager.getGlobalSemaphore();
        int maxLimit = manager.getGlobalConfig().getGlobalSemaphoreMaxLimit();
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
        } finally {
            // 无论物理释放是否溢出，只要这一单结束了，就必须触发终结信号
            // 这会使 ProgressTracker 中的 activeConsumers 递减，并检查任务是否可以彻底结束
            tracker.onGlobalTerminated(jobId);
            // 唤醒公平锁等待者
            manager.getFairLock().lock();
            try {
                manager.getPermitReleased().signalAll();
            } finally {
                manager.getFairLock().unlock();
            }
        }
    }
    
    /**
     * 获取许可：支持初次 launch 和后续 reacquire
     */
    public void acquire() throws InterruptedException {
        if (!manager.isRunning(jobId)) {
            throw new InterruptedException("Job " + jobId + " is not running.");
        }
        // 【核心埋点】：消费准入信号
        // 代表此数据正式占用了一个全局消费名额，进入系统生命周期
        tracker.onConsumerBegin();
        
        int globalSemaphoreMaxLimit = manager.getGlobalConfig().getGlobalSemaphoreMaxLimit();
        Semaphore semaphore = manager.getGlobalSemaphore();
        
        // 软性公平退让逻辑（保持现状）
        int activeJobs = manager.getActiveJobCount();
        int fairShare = Math.max(1, globalSemaphoreMaxLimit / activeJobs);
        while (registration.getActiveCount().get() >= fairShare && semaphore.availablePermits() == 0) {
            manager.getFairLock().lock();
            try {
                // 再次检查状态，防止在获取锁的过程中条件已经改变（Double Check）
                if (registration.getActiveCount().get() < fairShare || semaphore.availablePermits() > 0) {
                    break;
                }
                // 挂起线程，等待信号。这比 sleep 精准得多
                // 设置 50ms 超时作为兜底，防止极端情况下的丢失信号
                boolean signalled = manager.getPermitReleased().await(50, TimeUnit.MILLISECONDS);
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
                manager.getFairLock().unlock();
            }
            
            if (!manager.isRunning(jobId)) {
                throw new InterruptedException("Job stopped during acquire");
            }
        }
        semaphore.acquire();
        registration.increment();
    }
    
    /**
     * 开启受控任务上下文
     */
    public FlowEntry<T> open(T data, FlowJoiner<T> flowJoiner) {
        return new FlowEntry<>(data, jobId, flowJoiner.joinKey(data));
    }
}