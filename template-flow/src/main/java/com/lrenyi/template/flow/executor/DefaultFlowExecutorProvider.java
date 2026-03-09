package com.lrenyi.template.flow.executor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.lrenyi.template.flow.executor.BoundedVirtualExecutor.PermitStrategy;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.resource.PermitPair;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * FlowExecutorProvider 默认实现
 */
public class DefaultFlowExecutorProvider implements FlowExecutorProvider {
    
    private final ExecutorService flowConsumerExecutor;
    private final ScheduledExecutorService storageEgressExecutor;
    private final ExecutorService cacheRemovalExecutor;
    
    /**
     * @param globalSemaphore         全局消费许可；为 null 时表示不启用全局限制，执行器内部不做许可控制（由 Orchestrator 按 per-job 或无限制处理）。
     * @param removalSubmissionLimit  用于限制 Caffeine 驱逐回调的并发数。
     *                                 改为使用 ThreadPoolExecutor + 有界队列 + CallerRunsPolicy，
     *                                 彻底解决无界队列在极端高并发驱逐下的 Heap OOM 问题。
     */
    public DefaultFlowExecutorProvider(Semaphore globalSemaphore, int removalSubmissionLimit) {
        this.flowConsumerExecutor = globalSemaphore != null
                ? new BoundedVirtualExecutor(globalSemaphore)
                : new BoundedVirtualExecutor(noOpStrategy());
        this.storageEgressExecutor = Executors.newScheduledThreadPool(4, Thread.ofVirtual().factory());
        
        // 使用 ThreadPoolExecutor + Bounded Queue + CallerRunsPolicy：
        // 1. 限制并发数 = limit
        // 2. 有界队列 (capacity=10000) 防止任务堆积 OOM
        // 3. CallerRunsPolicy 实现生产端背压：当队列满时，由驱逐触发线程（如 Caffeine 维护线程或 put 线程）执行回调
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

    private static PermitStrategy noOpStrategy() {
        return new PermitStrategy() {
            @Override
            public void acquire() {
                // 全局未限制时由 Orchestrator 按 per-job 或无限制控制
            }

            @Override
            public void release() {
            }
        };
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
    
    @Override
    public ExecutorService createProducerExecutor(Semaphore globalSemaphore, Semaphore perJobSemaphore) {
        return createProducerExecutor(globalSemaphore, perJobSemaphore, null, null);
    }
    
    @Override
    public ExecutorService createProducerExecutor(PermitPair permitPair,
            MeterRegistry meterRegistry,
            String jobId) {
        if (permitPair == null) {
            return createProducerExecutor(null, null);
        }
        PermitStrategy baseStrategy = new PermitStrategy() {
            @Override
            public void acquire() throws InterruptedException {
                if (!permitPair.tryAcquireBoth(1)) {
                    throw new IllegalStateException("PermitPair.tryAcquireBoth returned false");
                }
            }

            @Override
            public void release() {
                permitPair.release(1);
            }
        };
        PermitStrategy strategy = baseStrategy;
        if (meterRegistry != null && jobId != null) {
            Timer timer = Timer.builder(FlowMetricNames.LIMITS_ACQUIRE_WAIT_DURATION)
                               .tag(FlowMetricNames.TAG_JOB_ID, jobId)
                               .tag(FlowMetricNames.TAG_DIMENSION, FlowMetricNames.DIMENSION_PRODUCER_THREADS)
                               .register(meterRegistry);
            strategy = new PermitStrategy() {
                @Override
                public void acquire() throws InterruptedException, TimeoutException {
                    Timer.Sample sample = Timer.start(meterRegistry);
                    baseStrategy.acquire();
                    sample.stop(timer);
                }

                @Override
                public void release() {
                    baseStrategy.release();
                }
            };
        }
        return new BoundedVirtualExecutor(strategy);
    }

    @Override
    public ExecutorService createProducerExecutor(Semaphore globalSemaphore,
            Semaphore perJobSemaphore,
            MeterRegistry meterRegistry,
            String jobId) {
        if (globalSemaphore == null) {
            return createProducerExecutor(perJobSemaphore);
        }
        return createProducerExecutor(PermitPair.of(globalSemaphore, perJobSemaphore), meterRegistry, jobId);
    }
}
