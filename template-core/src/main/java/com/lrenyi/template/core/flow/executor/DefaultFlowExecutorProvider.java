package com.lrenyi.template.core.flow.executor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;

/**
 * FlowExecutorProvider 默认实现
 */
public class DefaultFlowExecutorProvider implements FlowExecutorProvider {
    
    private final ExecutorService flowConsumerExecutor;
    private final ScheduledExecutorService storageEgressExecutor;
    private final ExecutorService cacheRemovalExecutor;
    
    public DefaultFlowExecutorProvider(Semaphore globalSemaphore) {
        this(globalSemaphore, Integer.MAX_VALUE);
    }
    
    /**
     * @param globalSemaphore         全局消费许可
     * @param removalSubmissionLimit  当前未使用。背压不在 removal executor 提交处做（会阻塞 Caffeine 导致 eviction lock 超时），
     *                                 改为在生产者侧 BackpressureController 中当「消费许可耗尽」时阻塞，实现层层向上背压。
     */
    public DefaultFlowExecutorProvider(Semaphore globalSemaphore, int removalSubmissionLimit) {
        this.flowConsumerExecutor = new BoundedVirtualExecutor(globalSemaphore);
        this.storageEgressExecutor = Executors.newScheduledThreadPool(4, Thread.ofVirtual().factory());
        this.cacheRemovalExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }
    
    @Override
    public ExecutorService getFlowConsumerExecutor() {
        return flowConsumerExecutor;
    }
    
    @Override
    public ScheduledExecutorService getStorageEgressExecutor() {
        return storageEgressExecutor;
    }
    
    @Override
    public ExecutorService getCacheRemovalExecutor() {
        return cacheRemovalExecutor;
    }
    
    @Override
    public ExecutorService createProducerExecutor(Semaphore semaphore) {
        return new BoundedVirtualExecutor(semaphore);
    }
}
