package com.lrenyi.template.flow.manager;

import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.LongAdder;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.backpressure.BackpressureManager;
import com.lrenyi.template.flow.backpressure.DimensionContext;
import com.lrenyi.template.flow.context.FlowResourceContext;
import com.lrenyi.template.flow.internal.FlowEgressHandler;
import com.lrenyi.template.flow.internal.FlowFinalizer;
import com.lrenyi.template.flow.internal.FlowLauncher;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import com.lrenyi.template.flow.resource.PermitPair;
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
            TemplateConfigProperties.Flow flow) {
        FlowResourceRegistry registry = flowManager.getResourceRegistry();
        MeterRegistry meterRegistry = flowManager.getMeterRegistry();
        TemplateConfigProperties.Flow.Limits limits = flow.getLimits();
        TemplateConfigProperties.Flow.Global global = limits.getGlobal();
        TemplateConfigProperties.Flow.PerJob perJob = limits.getPerJob();

        boolean fair = global.isFairScheduling();
        PerJobSemaphores semaphores = createPerJobSemaphores(perJob, fair);
        PermitPairs permitPairs = createPermitPairs(registry, semaphores);

        FlowEgressHandler<T> egressHandler = new FlowEgressHandler<>(flowJoiner, tracker, meterRegistry);
        FlowFinalizer<T> finalizer = new FlowFinalizer<>(registry, meterRegistry, egressHandler, flowJoiner);
        FlowStorage<T> storage = registry.getCacheManager()
                                         .getOrCreateStorage(jobId,
                                                             flowJoiner,
                                                             flow,
                                                             finalizer,
                                                             tracker,
                                                             egressHandler
                                         );
        
        BackpressureManager backpressureManager = createBackpressureManager(jobId,
                                                                            flow,
                                                                            registry,
                                                                            meterRegistry,
                                                                            permitPairs,
                                                                            global.getConsumerThreads()
        );
        
        FlowResourceContext resourceContext = FlowResourceContext.builder()
                                                                 .resourceRegistry(registry)
                                                                 .flowManager(flowManager)
                                                                 .jobProducerSemaphore(semaphores.jobProducer)
                                                                 .storage(storage)
                                                                 .backpressureManager(backpressureManager)
                                                                 .producerExecutor(Executors.newVirtualThreadPerTaskExecutor())
                                                                 .inFlightProductionSemaphore(semaphores.inFlightProduction)
                                                                 .jobConsumerSemaphore(semaphores.jobConsumer)
                                                                 .pendingConsumerSlotSemaphore(semaphores.pendingConsumerSlot)
                                                                 .egressHandler(egressHandler)
                                                                 .consumerPermitPair(permitPairs.consumer)
                                                                 .inFlightPermitPair(permitPairs.inFlight)
                                                                 .producerPermitPair(permitPairs.producer)
                                                                 .build();
        
        registerPerJobMetrics(jobId, perJob, semaphores, tracker, meterRegistry);
        registerGlobalMetricsOnce(registry, global, meterRegistry);
        
        return FlowLauncher.create(jobId, flowJoiner, flowManager, tracker, flow, resourceContext);
    }
    
    private static PerJobSemaphores createPerJobSemaphores(TemplateConfigProperties.Flow.PerJob perJob, boolean fair) {
        int consumerLimit = perJob.getConsumerThreads();
        int effectivePending = perJob.getEffectivePendingConsumer();
        return new PerJobSemaphores(new Semaphore(perJob.getProducerThreads(), fair),
                                    new Semaphore(perJob.getInFlightProduction(), fair),
                                    consumerLimit > 0 ? new Semaphore(consumerLimit, fair) : null,
                                    effectivePending > 0 ? new Semaphore(effectivePending, fair) : null,
                                    new Semaphore(perJob.getStorageCapacity(), fair)
        );
    }
    
    private static PermitPairs createPermitPairs(FlowResourceRegistry registry, PerJobSemaphores semaphores) {
        return new PermitPairs(PermitPair.of(registry.getGlobalSemaphore(), semaphores.jobConsumer),
                               PermitPair.of(registry.getGlobalInFlightSemaphore(), semaphores.inFlightProduction),
                               registry.getGlobalProducerThreadsSemaphore() != null ?
                                       PermitPair.of(registry.getGlobalProducerThreadsSemaphore(),
                                                     semaphores.jobProducer
                                       ) : null,
                               PermitPair.of(registry.getGlobalStorageSemaphore(), semaphores.perJobStorage),
                               (registry.getGlobalInFlightConsumerSemaphore() != null
                                       || semaphores.pendingConsumerSlot != null) ?
                                       PermitPair.of(registry.getGlobalInFlightConsumerSemaphore(),
                                                     semaphores.pendingConsumerSlot
                                       ) : null
        );
    }
    
    private static BackpressureManager createBackpressureManager(String jobId,
            TemplateConfigProperties.Flow flow,
            FlowResourceRegistry registry,
            MeterRegistry meterRegistry,
            PermitPairs pairs,
            int globalConsumerLimit) {
        DimensionContext baseCtx = DimensionContext.builder()
                                                   .jobId(jobId)
                                                   .dimensionId(null)
                                                   .stopCheck(() -> false)
                                                   .permits(1)
                                                   .meterRegistry(meterRegistry)
                                                   .flowConfig(flow)
                                                   .resourceRegistry(registry)
                                                   .inFlightPermitPair(pairs.inFlight)
                                                   .producerPermitPair(pairs.producer)
                                                   .consumerPermitPair(pairs.consumer)
                                                   .inFlightConsumerPermitPair(pairs.inFlightConsumer)
                                                   .storagePermitPair(pairs.storage)
                                                   .globalConsumerLimit(globalConsumerLimit)
                                                   .build();
        return new BackpressureManager(baseCtx, meterRegistry);
    }
    
    private static void registerPerJobMetrics(String jobId,
            TemplateConfigProperties.Flow.PerJob perJob,
            PerJobSemaphores semaphores,
            ProgressTracker tracker,
            MeterRegistry meterRegistry) {
        String tag = FlowMetricNames.TAG_JOB_ID;
        int producerLimit = perJob.getProducerThreads();
        int inFlightLimit = perJob.getInFlightProduction();
        int consumerLimit = perJob.getConsumerThreads();
        int effectivePending = perJob.getEffectivePendingConsumer();

        Gauge.builder(FlowMetricNames.LIMITS_PRODUCER_THREADS_USED,
                      semaphores.jobProducer,
                      s -> producerLimit - s.availablePermits()
        ).tag(tag, jobId).description("每 Job 已占用生产线程数").register(meterRegistry);
        Gauge.builder(FlowMetricNames.LIMITS_PRODUCER_THREADS_LIMIT, semaphores.jobProducer, s -> producerLimit)
             .tag(tag, jobId)
             .description("每 Job 生产线程上限")
             .register(meterRegistry);
        
        Gauge.builder(FlowMetricNames.LIMITS_IN_FLIGHT_USED, semaphores.inFlightProduction,
                      s -> inFlightLimit - s.availablePermits()
        ).tag(tag, jobId).description("每 Job 在途数据条数").register(meterRegistry);
        Gauge.builder(FlowMetricNames.LIMITS_IN_FLIGHT_LIMIT, semaphores.inFlightProduction, s -> inFlightLimit)
             .tag(tag, jobId)
             .description("每 Job 在途数据上限")
             .register(meterRegistry);

        Gauge.builder(FlowMetricNames.LIMITS_PENDING_CONSUMER_COUNT,
                      () -> tracker.getSnapshot().getPendingConsumerCount()
        ).tag(tag, jobId).description("每 Job 已离库未终结条数").register(meterRegistry);
        Gauge.builder(FlowMetricNames.LIMITS_PENDING_CONSUMER_LIMIT, () -> effectivePending).tag(tag, jobId)
             .description("每 Job 背压阈值")
             .register(meterRegistry);
        
        if (semaphores.jobConsumer != null) {
            Gauge.builder(FlowMetricNames.LIMITS_CONSUMER_CONCURRENCY_USED,
                          semaphores.jobConsumer,
                          s -> consumerLimit - s.availablePermits()
            ).tag(tag, jobId).description("每 Job 已占用消费许可数").register(meterRegistry);
            Gauge.builder(FlowMetricNames.LIMITS_CONSUMER_CONCURRENCY_LIMIT, semaphores.jobConsumer, s -> consumerLimit)
                 .tag(tag, jobId)
                 .description("每 Job 消费许可上限")
                 .register(meterRegistry);
        } else {
            Gauge.builder(FlowMetricNames.LIMITS_CONSUMER_CONCURRENCY_USED, () -> 0).tag(tag, jobId)
                 .description("每 Job 已占用消费许可数（仅全局限制时恒为 0）")
                 .register(meterRegistry);
            Gauge.builder(FlowMetricNames.LIMITS_CONSUMER_CONCURRENCY_LIMIT, () -> 0).tag(tag, jobId)
                 .description("每 Job 消费许可上限（仅全局限制时为 0）")
                 .register(meterRegistry);
        }
    }
    
    private static void registerGlobalMetricsOnce(FlowResourceRegistry registry,
            TemplateConfigProperties.Flow.Global global,
            MeterRegistry meterRegistry) {
        if (registry.getGlobalProducerThreadsSemaphore() != null
                && meterRegistry.find(FlowMetricNames.LIMITS_PRODUCER_THREADS_GLOBAL_USED).gauge() == null) {
            int limit = global.getProducerThreads();
            Semaphore s = registry.getGlobalProducerThreadsSemaphore();
            Gauge.builder(FlowMetricNames.LIMITS_PRODUCER_THREADS_GLOBAL_USED, s, x -> limit - x.availablePermits())
                 .description("全主机已占用生产线程数")
                 .register(meterRegistry);
            Gauge.builder(FlowMetricNames.LIMITS_PRODUCER_THREADS_GLOBAL_LIMIT, () -> limit)
                 .description("全主机生产线程上限")
                 .register(meterRegistry);
        }
        if (registry.getGlobalInFlightSemaphore() != null
                && meterRegistry.find(FlowMetricNames.LIMITS_IN_FLIGHT_GLOBAL_USED).gauge() == null) {
            int limit = global.getInFlightProduction();
            Semaphore s = registry.getGlobalInFlightSemaphore();
            Gauge.builder(FlowMetricNames.LIMITS_IN_FLIGHT_GLOBAL_USED, s, x -> limit - x.availablePermits())
                 .description("全主机在途数据条数")
                 .register(meterRegistry);
            Gauge.builder(FlowMetricNames.LIMITS_IN_FLIGHT_GLOBAL_LIMIT, () -> limit)
                 .description("全主机在途数据上限")
                 .register(meterRegistry);
        }
        if (global.getConsumerThreads() > 0
                && meterRegistry.find(FlowMetricNames.LIMITS_CONSUMER_CONCURRENCY_GLOBAL_USED).gauge() == null) {
            int limit = global.getConsumerThreads();
            Semaphore s = registry.getGlobalSemaphore();
            Gauge.builder(FlowMetricNames.LIMITS_CONSUMER_CONCURRENCY_GLOBAL_USED, s, x -> limit - x.availablePermits())
                 .description("全主机已占用消费许可数")
                 .register(meterRegistry);
            Gauge.builder(FlowMetricNames.LIMITS_CONSUMER_CONCURRENCY_GLOBAL_LIMIT, () -> limit)
                 .description("全主机消费许可上限")
                 .register(meterRegistry);
        }
        if (registry.getGlobalStorageSemaphore() != null
                && meterRegistry.find(FlowMetricNames.LIMITS_STORAGE_GLOBAL_USED).gauge() == null) {
            int limit = global.getStorageCapacity();
            Semaphore s = registry.getGlobalStorageSemaphore();
            Gauge.builder(FlowMetricNames.LIMITS_STORAGE_GLOBAL_USED, s, x -> limit - x.availablePermits())
                 .description("全主机缓存总条数")
                 .register(meterRegistry);
            Gauge.builder(FlowMetricNames.LIMITS_STORAGE_GLOBAL_LIMIT, () -> limit)
                 .description("全主机缓存容量上限")
                 .register(meterRegistry);
        }
        if (global.getInFlightConsumer() > 0
                && meterRegistry.find(FlowMetricNames.LIMITS_PENDING_CONSUMER_GLOBAL_COUNT).gauge() == null) {
            int limit = global.getInFlightConsumer();
            Gauge.builder(FlowMetricNames.LIMITS_PENDING_CONSUMER_GLOBAL_COUNT,
                          registry.getGlobalPendingConsumerAdder(),
                          LongAdder::sum
            ).description("全主机已离库未终结条数").register(meterRegistry);
            Gauge.builder(FlowMetricNames.LIMITS_PENDING_CONSUMER_GLOBAL_LIMIT, () -> limit)
                 .description("全主机背压阈值")
                 .register(meterRegistry);
        }
    }
    
    //@formatter:off
    private record PerJobSemaphores(Semaphore jobProducer,
                                    Semaphore inFlightProduction,
                                    Semaphore jobConsumer,
                                    Semaphore pendingConsumerSlot,
                                    Semaphore perJobStorage) {}
    
    private record PermitPairs(PermitPair consumer,
                               PermitPair inFlight,
                               PermitPair producer,
                               PermitPair storage,
                               PermitPair inFlightConsumer) {}
    //@formatter:on
}
