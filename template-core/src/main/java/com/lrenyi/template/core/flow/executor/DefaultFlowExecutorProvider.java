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
