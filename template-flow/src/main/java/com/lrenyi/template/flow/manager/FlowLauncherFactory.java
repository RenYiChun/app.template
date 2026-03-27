package com.lrenyi.template.flow.manager;

import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import com.lrenyi.template.flow.model.FlowConstants;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.backpressure.BackpressureManager;
import com.lrenyi.template.flow.backpressure.DimensionContext;
import com.lrenyi.template.flow.context.FlowResourceContext;
import com.lrenyi.template.flow.internal.AsyncEgressConsumeStrategy;
import com.lrenyi.template.flow.internal.EgressConsumeStrategy;
import com.lrenyi.template.flow.internal.FlowEgressHandler;
import com.lrenyi.template.flow.internal.FlowFinalizer;
import com.lrenyi.template.flow.internal.FlowLauncher;
import com.lrenyi.template.flow.internal.InlineEgressConsumeStrategy;
import com.lrenyi.template.flow.model.FlowConsumeExecutionMode;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import com.lrenyi.template.flow.resource.PermitPair;
import com.lrenyi.template.flow.storage.FlowStorage;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 创建 FlowLauncher 的工厂类，将创建逻辑从 FlowManager 中抽离。
 */
final class FlowLauncherFactory {

    private FlowLauncherFactory() {
    }

    static <T> FlowLauncher<T> create(FlowManager flowManager,
            String jobId,
            String metricJobId,
            FlowJoiner<T> flowJoiner,
            ProgressTracker tracker,
            TemplateConfigProperties.Flow flow,
            FlowConsumeExecutionMode consumeExecutionMode) {
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
        EgressConsumeStrategy<T> egressConsumeStrategy = createEgressConsumeStrategy(finalizer, consumeExecutionMode);
        int egressWorkerThreads = resolveEgressWorkerThreads(flow, consumeExecutionMode);
        FlowStorage<T> storage = registry.getCacheManager()
                                         .getOrCreateStorage(jobId,
                                                             flowJoiner,
                                                             flow,
                                                             finalizer,
                                                             tracker,
                                                             egressHandler,
                                                             consumeExecutionMode != null
                                                                     ? consumeExecutionMode
                                                                     : FlowConsumeExecutionMode.ASYNC,
                                                             egressWorkerThreads
                                         );

        BackpressureManager backpressureManager = createBackpressureManager(jobId,
                                                                            tracker,
                                                                            metricJobId,
                                                                            flow,
                                                                            registry,
                                                                            meterRegistry,
                                                                            permitPairs,
                                                                            global.getConsumerThreads()
        );

        ThreadFactory producerThreadFactory =
                Thread.ofVirtual().name(FlowConstants.THREAD_NAME_PREFIX_PRODUCER, 0).factory();
        FlowResourceContext resourceContext = FlowResourceContext.builder()
                                                                 .resourceRegistry(registry)
                                                                 .flowManager(flowManager)
                                                                 .storage(storage)
                                                                 .backpressureManager(backpressureManager)
                                                                 .producerExecutor(Executors.newThreadPerTaskExecutor(producerThreadFactory))
                                                                 .egressHandler(egressHandler)
                                                                 .finalizer(finalizer)
                                                                 .egressConsumeStrategy(egressConsumeStrategy)
                                                                 .consumerPermitPair(permitPairs.consumer)
                                                                 .producerPermitPair(permitPairs.producer)
                                                                 .build();

        return FlowLauncher.create(jobId, metricJobId, flowJoiner, flowManager, tracker, flow, resourceContext);
    }

    private static <T> EgressConsumeStrategy<T> createEgressConsumeStrategy(FlowFinalizer<T> finalizer,
            FlowConsumeExecutionMode consumeExecutionMode) {
        FlowConsumeExecutionMode mode = consumeExecutionMode != null ? consumeExecutionMode : FlowConsumeExecutionMode.ASYNC;
        return switch (mode) {
            case INLINE -> new InlineEgressConsumeStrategy<>(finalizer);
            case ASYNC -> new AsyncEgressConsumeStrategy<>(finalizer);
        };
    }

    private static int resolveEgressWorkerThreads(TemplateConfigProperties.Flow flow,
            FlowConsumeExecutionMode consumeExecutionMode) {
        if (consumeExecutionMode == FlowConsumeExecutionMode.INLINE) {
            return Math.max(1, flow.getLimits().getPerJob().getConsumerThreads());
        }
        return 1;
    }

    private static PerJobSemaphores createPerJobSemaphores(TemplateConfigProperties.Flow.PerJob perJob, boolean fair) {
        int consumerLimit = perJob.getConsumerThreads();
        return new PerJobSemaphores(new Semaphore(perJob.getProducerThreads(), fair),
                                    consumerLimit > 0 ? new Semaphore(consumerLimit, fair) : null,
                                    new Semaphore(perJob.getStorageCapacity(), fair)
        );
    }

    private static PermitPairs createPermitPairs(FlowResourceRegistry registry, PerJobSemaphores semaphores) {
        return new PermitPairs(PermitPair.of(registry.getGlobalSemaphore(), semaphores.jobConsumer),
                               PermitPair.of(registry.getGlobalProducerThreadsSemaphore(), semaphores.jobProducer),
                               PermitPair.of(registry.getGlobalStorageSemaphore(), semaphores.perJobStorage)
        );
    }

    private static BackpressureManager createBackpressureManager(String jobId,
            ProgressTracker tracker,
            String metricJobId,
            TemplateConfigProperties.Flow flow,
            FlowResourceRegistry registry,
            MeterRegistry meterRegistry,
            PermitPairs pairs,
            int globalConsumerLimit) {
        DimensionContext baseCtx = DimensionContext.builder()
                                                   .jobId(jobId)
                                                   .metricJobId(metricJobId)
                                                   .progressTracker(tracker)
                                                   .dimensionId(null)
                                                   .stopCheck(() -> false)
                                                   .permits(1)
                                                   .meterRegistry(meterRegistry)
                                                   .flowConfig(flow)
                                                   .resourceRegistry(registry)
                                                   .producerPermitPair(pairs.producer)
                                                   .consumerPermitPair(pairs.consumer)
                                                   .storagePermitPair(pairs.storage)
                                                   .globalConsumerLimit(globalConsumerLimit)
                                                   .build();
        return new BackpressureManager(baseCtx, meterRegistry);
    }


    //@formatter:off
    private record PerJobSemaphores(Semaphore jobProducer,
                                    Semaphore jobConsumer,
                                    Semaphore perJobStorage) {}

    private record PermitPairs(PermitPair consumer,
                               PermitPair producer,
                               PermitPair storage) {}
    //@formatter:on
}
