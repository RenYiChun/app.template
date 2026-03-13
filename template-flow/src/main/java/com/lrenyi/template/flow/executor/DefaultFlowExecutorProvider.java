package com.lrenyi.template.flow.executor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import com.lrenyi.template.flow.model.FlowConstants;

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
        // 消费并发由 FlowFinalizer 通过 BackpressureManager 控制；使用 newThreadPerTaskExecutor 以支持自定义线程名
        this.flowConsumerExecutor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                                                                                    .name(FlowConstants.THREAD_NAME_PREFIX_CONSUMER,
                                                                                          0
                                                                                    )
                                                                                    .factory());
        this.flowProducerExecutor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                                                                                    .name(FlowConstants.THREAD_NAME_PREFIX_PRODUCER,
                                                                                          0
                                                                                    )
                                                                                    .factory());
        this.storageEgressExecutor = Executors.newScheduledThreadPool(4,
                                                                      Thread.ofVirtual()
                                                                            .name(FlowConstants.THREAD_NAME_PREFIX_STORAGE_EGRESS,
                                                                                  0
                                                                            )
                                                                            .factory()
        );

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
