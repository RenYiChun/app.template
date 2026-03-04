package com.lrenyi.template.flow.manager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.context.FlowResourceContext;
import com.lrenyi.template.flow.context.Registration;
import com.lrenyi.template.flow.internal.BackpressureController;
import com.lrenyi.template.flow.internal.FlowFinalizer;
import com.lrenyi.template.flow.internal.FlowLauncher;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import com.lrenyi.template.flow.storage.FlowStorage;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 创建 FlowLauncher 的工厂类，将创建逻辑从 FlowManager 中抽离。
 */
final class FlowLauncherFactory {
    
    private FlowLauncherFactory() {
    }
    
    static <T> FlowLauncher<T> create(FlowManager flowManager,
            String jobId,
            FlowJoiner<T> flowJoiner,
            ProgressTracker tracker,
            Registration registration) {
        FlowResourceRegistry resourceRegistry = flowManager.getResourceRegistry();
        MeterRegistry meterRegistry = flowManager.getMeterRegistry();
        TemplateConfigProperties.Flow flow = registration.getFlow();
        TemplateConfigProperties.Flow.Limits limits = flow.getLimits();
        TemplateConfigProperties.Flow.Global global = limits.getGlobal();
        TemplateConfigProperties.Flow.PerJob perJob = limits.getPerJob();
        
        boolean fair = global.isFairScheduling();
        Semaphore jobProducerSemaphore = new Semaphore(perJob.getProducerThreads(), fair);
        Semaphore globalProducerThreads = resourceRegistry.getGlobalProducerThreadsSemaphore();
        ExecutorService producerExecutor = globalProducerThreads != null ? resourceRegistry.getExecutorProvider()
                                                                                           .createProducerExecutor(
                                                                                                   globalProducerThreads,
                                                                                                   jobProducerSemaphore,
                                                                                                   meterRegistry,
                                                                                                   jobId
                                                                                           ) :
                resourceRegistry.getExecutorProvider().createProducerExecutor(jobProducerSemaphore);
        
        FlowFinalizer<T> finalizer = new FlowFinalizer<>(resourceRegistry, meterRegistry);
        FlowStorage<T> storage =
                resourceRegistry.getCacheManager().getOrCreateStorage(jobId, flowJoiner, flow, finalizer, tracker);
        
        IntSupplier consumerPermits = () -> resourceRegistry.getGlobalSemaphore().availablePermits();
        LongSupplier perJobPendingCount = () -> tracker.getSnapshot().getPendingConsumerCount();
        LongSupplier globalPendingCount = () -> resourceRegistry.getGlobalPendingConsumerAdder().sum();
        int effectivePendingConsumer = perJob.getEffectivePendingConsumer();
        BackpressureController backpressureController = new BackpressureController(storage,
                                                                                   consumerPermits,
                                                                                   perJobPendingCount,
                                                                                   effectivePendingConsumer,
                                                                                   globalPendingCount,
                                                                                   global.getPendingConsumer(),
                                                                                   meterRegistry,
                                                                                   jobId
        );
        
        int inFlightLimit = perJob.getInFlightProduction();
        Semaphore inFlightProductionSemaphore = new Semaphore(inFlightLimit, fair);
        
        int consumerConcurrencyLimit = perJob.getConsumerConcurrency();
        Semaphore jobConsumerSemaphore = new Semaphore(consumerConcurrencyLimit, fair);
        
        FlowResourceContext resourceContext = FlowResourceContext.builder()
                                                                 .resourceRegistry(resourceRegistry)
                                                                 .flowManager(flowManager)
                                                                 .jobProducerSemaphore(jobProducerSemaphore)
                                                                 .storage(storage)
                                                                 .backpressureController(backpressureController)
                                                                 .producerExecutor(producerExecutor)
                                                                 .inFlightProductionSemaphore(inFlightProductionSemaphore)
                                                                 .jobConsumerSemaphore(jobConsumerSemaphore)
                                                                 .build();
        
        // limits 维度指标
        String tagJobId = FlowMetricNames.TAG_JOB_ID;
        int producerThreadsLimit = perJob.getProducerThreads();
        Gauge.builder(FlowMetricNames.LIMITS_PRODUCER_THREADS_USED,
                      jobProducerSemaphore,
                      s -> producerThreadsLimit - s.availablePermits()
        ).tag(tagJobId, jobId).description("每 Job 已占用生产线程数").register(meterRegistry);
        Gauge.builder(FlowMetricNames.LIMITS_PRODUCER_THREADS_LIMIT, jobProducerSemaphore, s -> producerThreadsLimit)
             .tag(tagJobId, jobId)
             .description("每 Job 生产线程上限")
             .register(meterRegistry);
        
        Gauge.builder(FlowMetricNames.LIMITS_IN_FLIGHT_USED, inFlightProductionSemaphore,
                      s -> inFlightLimit - s.availablePermits()
        ).tag(tagJobId, jobId).description("每 Job 在途数据条数").register(meterRegistry);
        Gauge.builder(FlowMetricNames.LIMITS_IN_FLIGHT_LIMIT, inFlightProductionSemaphore, s -> inFlightLimit)
             .tag(tagJobId, jobId)
             .description("每 Job 在途数据上限")
             .register(meterRegistry);
        
        Gauge.builder(FlowMetricNames.LIMITS_PENDING_CONSUMER_COUNT,
                      () -> tracker.getSnapshot().getPendingConsumerCount()
        ).tag(tagJobId, jobId).description("每 Job 已离库未终结条数").register(meterRegistry);
        Gauge.builder(FlowMetricNames.LIMITS_PENDING_CONSUMER_LIMIT, () -> effectivePendingConsumer)
             .tag(tagJobId, jobId)
             .description("每 Job 背压阈值")
             .register(meterRegistry);
        
        Gauge.builder(FlowMetricNames.LIMITS_CONSUMER_CONCURRENCY_USED, jobConsumerSemaphore,
                      s -> consumerConcurrencyLimit - s.availablePermits()
        ).tag(tagJobId, jobId).description("每 Job 已占用消费许可数").register(meterRegistry);
        Gauge.builder(FlowMetricNames.LIMITS_CONSUMER_CONCURRENCY_LIMIT, jobConsumerSemaphore,
                      s -> consumerConcurrencyLimit
        ).tag(tagJobId, jobId).description("每 Job 消费许可上限").register(meterRegistry);
        
        Semaphore globalProducerThreadsSem = resourceRegistry.getGlobalProducerThreadsSemaphore();
        if (globalProducerThreadsSem != null
                && meterRegistry.find(FlowMetricNames.LIMITS_PRODUCER_THREADS_GLOBAL_USED).gauge() == null) {
            int globalProducerLimit = global.getProducerThreads();
            Gauge.builder(FlowMetricNames.LIMITS_PRODUCER_THREADS_GLOBAL_USED,
                          globalProducerThreadsSem,
                          s -> globalProducerLimit - s.availablePermits()
            ).description("全主机已占用生产线程数").register(meterRegistry);
            Gauge.builder(FlowMetricNames.LIMITS_PRODUCER_THREADS_GLOBAL_LIMIT, () -> globalProducerLimit)
                 .description("全主机生产线程上限")
                 .register(meterRegistry);
        }
        Semaphore globalInFlightSem = resourceRegistry.getGlobalInFlightSemaphore();
        if (globalInFlightSem != null
                && meterRegistry.find(FlowMetricNames.LIMITS_IN_FLIGHT_GLOBAL_USED).gauge() == null) {
            int globalInFlightLimit = global.getInFlightProduction();
            Gauge.builder(FlowMetricNames.LIMITS_IN_FLIGHT_GLOBAL_USED,
                          globalInFlightSem,
                          s -> globalInFlightLimit - s.availablePermits()
            ).description("全主机在途数据条数").register(meterRegistry);
            Gauge.builder(FlowMetricNames.LIMITS_IN_FLIGHT_GLOBAL_LIMIT, () -> globalInFlightLimit)
                 .description("全主机在途数据上限")
                 .register(meterRegistry);
        }

        Semaphore globalSemaphore = resourceRegistry.getGlobalSemaphore();
        int globalConsumerLimit = global.getConsumerConcurrency();
        if (globalConsumerLimit > 0
                && meterRegistry.find(FlowMetricNames.LIMITS_CONSUMER_CONCURRENCY_GLOBAL_USED).gauge() == null) {
            Gauge.builder(FlowMetricNames.LIMITS_CONSUMER_CONCURRENCY_GLOBAL_USED,
                          globalSemaphore,
                          s -> globalConsumerLimit - s.availablePermits()
            ).description("全主机已占用消费许可数").register(meterRegistry);
            Gauge.builder(FlowMetricNames.LIMITS_CONSUMER_CONCURRENCY_GLOBAL_LIMIT, () -> globalConsumerLimit)
                 .description("全主机消费许可上限")
                 .register(meterRegistry);
        }
        Semaphore globalStorageSem = resourceRegistry.getGlobalStorageSemaphore();
        if (globalStorageSem != null
                && meterRegistry.find(FlowMetricNames.LIMITS_STORAGE_GLOBAL_USED).gauge() == null) {
            int globalStorageLimit = global.getStorage();
            Gauge.builder(FlowMetricNames.LIMITS_STORAGE_GLOBAL_USED,
                          globalStorageSem,
                          s -> globalStorageLimit - s.availablePermits()
            ).description("全主机缓存总条数").register(meterRegistry);
            Gauge.builder(FlowMetricNames.LIMITS_STORAGE_GLOBAL_LIMIT, () -> globalStorageLimit)
                 .description("全主机缓存容量上限")
                 .register(meterRegistry);
        }
        int globalPendingLimit = global.getPendingConsumer();
        if (globalPendingLimit > 0
                && meterRegistry.find(FlowMetricNames.LIMITS_PENDING_CONSUMER_GLOBAL_COUNT).gauge() == null) {
            Gauge.builder(FlowMetricNames.LIMITS_PENDING_CONSUMER_GLOBAL_COUNT,
                          resourceRegistry.getGlobalPendingConsumerAdder(),
                          LongAdder::sum
            ).description("全主机已离库未终结条数").register(meterRegistry);
            Gauge.builder(FlowMetricNames.LIMITS_PENDING_CONSUMER_GLOBAL_LIMIT, () -> globalPendingLimit)
                 .description("全主机背压阈值")
                 .register(meterRegistry);
        }

        return FlowLauncher.create(jobId, flowJoiner, flowManager, tracker, registration, resourceContext);
    }
}
