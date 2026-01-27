package com.lrenyi.template.core.flow.storage;

import com.lrenyi.template.core.flow.context.FlowEntry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于阻塞队列的任务存储实现
 * 适用于：顺序消费、削峰填谷场景
 */
@Slf4j
public class QueueFlowStorage<T> implements FlowStorage<T> {
    private final BlockingQueue<FlowEntry<T>> queue;
    private final long maxCaseSize;
    
    /**
     * @param capacity 队列容量，决定了背压触发的阈值
     */
    public QueueFlowStorage(int capacity) {
        this.queue = new LinkedBlockingQueue<>(capacity);
        this.maxCaseSize = capacity;
    }
    
    @Override
    public boolean doDeposit(FlowEntry<T> ctx) {
        // 2. 尝试非阻塞入队
        boolean success = queue.offer(ctx);
        if (success) {
            if (log.isDebugEnabled()) {
                log.debug("Data deposited into queue: jobId={}, queueSize={}", ctx.getJobId(), queue.size());
            }
            // 入队成功，Launcher 线程退出 try-with-resources 调用 close() 时，
            // refCnt 由 2 变为 1，任务在队列中存活
            return true;
        }
        if (log.isWarnEnabled()) {
            log.warn("Queue full, task rejected: jobId={}", ctx.getJobId());
        }
        return false;
    }
    
    /**
     * 暴露给消费者的获取接口
     */
    public FlowEntry<T> poll() {
        return queue.poll();
    }
    
    /**
     * 阻塞获取接口
     */
    public FlowEntry<T> take() throws InterruptedException {
        return queue.take();
    }
    
    @Override
    public long size() {
        return queue.size();
    }
    
    @Override
    public long maxCacheSize() {
        return maxCaseSize;
    }
    
    @Override
    public void shutdown() {
        // 清理队列中的剩余任务，防止内存泄露
        FlowEntry<T> remaining;
        while ((remaining = queue.poll()) != null) {
            remaining.close();
        }
    }
}