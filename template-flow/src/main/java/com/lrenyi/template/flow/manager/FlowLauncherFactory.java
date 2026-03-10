package com.lrenyi.template.flow.manager;

import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.backpressure.BackpressureManager;
import com.lrenyi.template.flow.backpressure.DimensionContext;
import com.lrenyi.template.flow.context.FlowResourceContext;
import com.lrenyi.template.flow.internal.FlowEgressHandler;
import com.lrenyi.template.flow.internal.FlowFinalizer;
import com.lrenyi.template.flow.internal.FlowLauncher;
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
