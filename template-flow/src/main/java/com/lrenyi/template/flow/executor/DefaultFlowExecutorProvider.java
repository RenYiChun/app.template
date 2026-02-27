package com.lrenyi.template.flow.executor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * FlowExecutorProvider 默认实现
 */
public class DefaultFlowExecutorProvider implements FlowExecutorProvider {
    
    private final ExecutorService flowConsumerExecutor;
    private final ScheduledExecutorService storageEgressExecutor;
    private final ExecutorService cacheRemovalExecutor;
    
    public DefaultFlowExecutorProvider(Semaphore globalSemaphore) {
        this(globalSemaphore, 0);
    }
    
    /**
     * @param globalSemaphore         全局消费许可
     * @param removalSubmissionLimit  用于限制 Caffeine 驱逐回调的并发数。
     *                                 改为使用 ThreadPoolExecutor + 有界队列 + CallerRunsPolicy，
     *                                 彻底解决无界队列在极端高并发驱逐下的 Heap OOM 问题。
     */
    public DefaultFlowExecutorProvider(Semaphore globalSemaphore, int removalSubmissionLimit) {
        this.flowConsumerExecutor = new BoundedVirtualExecutor(globalSemaphore);
        this.storageEgressExecutor = Executors.newScheduledThreadPool(4, Thread.ofVirtual().factory());
        
        // 使用 ThreadPoolExecutor + Bounded Queue + CallerRunsPolicy：
        // 1. 限制并发数 = limit
        // 2. 有界队列 (capacity=10000) 防止任务堆积 OOM
        // 3. CallerRunsPolicy 实现生产端背压：当队列满时，由驱逐触发线程（如 Caffeine 维护线程或 put 线程）执行回调
        int limit = removalSubmissionLimit > 0 ? removalSubmissionLimit : 100;
        this.cacheRemovalExecutor = new ThreadPoolExecutor(
                limit,
                limit,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(limit * 10),
                Thread.ofVirtual().name("flow-removal-", 0).factory(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
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
