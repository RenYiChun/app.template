package com.lrenyi.template.flow.executor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * FlowExecutorProvider 默认实现
 */
public class DefaultFlowExecutorProvider implements FlowExecutorProvider {

    private final ExecutorService flowConsumerExecutor;
    private final ScheduledExecutorService storageEgressExecutor;
    private final ExecutorService cacheRemovalExecutor;
    private final ExecutorService flowProducerExecutor;

    /**
     * @param removalSubmissionLimit  用于限制 Caffeine 驱逐回调的并发数
     */
    public DefaultFlowExecutorProvider(int removalSubmissionLimit) {
        // 消费并发由 FlowFinalizer 通过 BackpressureManager 控制
        this.flowConsumerExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.flowProducerExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.storageEgressExecutor = Executors.newScheduledThreadPool(4, Thread.ofVirtual().factory());
        
        int limit = removalSubmissionLimit > 0 ? removalSubmissionLimit : 100;
        this.cacheRemovalExecutor = new ThreadPoolExecutor(limit,
                                                           limit,
                                                           0L,
                                                           TimeUnit.MILLISECONDS,
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
    public ExecutorService getFlowProducerExecutor() {
        return flowProducerExecutor;
    }
}
