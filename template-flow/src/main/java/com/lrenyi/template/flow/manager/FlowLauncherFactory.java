package com.lrenyi.template.flow.manager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
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
        
        Semaphore jobProducerSemaphore = new Semaphore(flow.getProducer().getParallelism());
        ExecutorService producerExecutor =
                resourceRegistry.getExecutorProvider().createProducerExecutor(jobProducerSemaphore);
        
        FlowFinalizer<T> finalizer = new FlowFinalizer<>(resourceRegistry, meterRegistry);
        FlowStorage<T> storage =
                resourceRegistry.getCacheManager().getOrCreateStorage(jobId, flowJoiner, flow, finalizer, tracker);
        
        IntSupplier consumerPermits = () -> resourceRegistry.getGlobalSemaphore().availablePermits();
        LongSupplier pendingCount = () -> tracker.getSnapshot().getPendingConsumerCount();
        IntSupplier globalSemaphoreCapacity =
                () -> resourceRegistry.getFlowConfig().getConsumer().getConcurrencyLimit();
        BackpressureController backpressureController = new BackpressureController(storage,
                                                                                   consumerPermits,
                                                                                   pendingCount,
                                                                                   globalSemaphoreCapacity,
                                                                                   meterRegistry,
                                                                                   jobId
        );
        
        int inFlightLimit =
                flow.getProducer().getMaxInFlightThreshold() > 0 ? flow.getProducer().getMaxInFlightThreshold() :
                        resourceRegistry.getFlowConfig().getConsumer().getConcurrencyLimit();
        Semaphore inFlightProductionSemaphore = new Semaphore(inFlightLimit, true);
        
        FlowResourceContext resourceContext = FlowResourceContext.builder()
                                                                 .resourceRegistry(resourceRegistry)
                                                                 .flowManager(flowManager)
                                                                 .jobProducerSemaphore(jobProducerSemaphore)
                                                                 .storage(storage)
                                                                 .backpressureController(backpressureController)
                                                                 .producerExecutor(producerExecutor)
                                                                 .inFlightProductionSemaphore(
                                                                         inFlightProductionSemaphore)
                                                                 .build();
        
        // Metrics for production semaphores
        Gauge.builder("app.template.flow.producer.semaphore.used", jobProducerSemaphore,
                        s -> flow.getProducer().getParallelism() - s.availablePermits())
                .tag("jobId", jobId)
                .description("Current active producer threads for the job")
                .register(meterRegistry);

        Gauge.builder("app.template.flow.producer.semaphore.limit", jobProducerSemaphore,
                        s -> flow.getProducer().getParallelism())
                .tag("jobId", jobId)
                .description("Max allowed producer threads for the job")
                .register(meterRegistry);

        Gauge.builder("app.template.flow.producer.inflight.used", inFlightProductionSemaphore,
                        s -> inFlightLimit - s.availablePermits())
                .tag("jobId", jobId)
                .description("Current in-flight tasks (backpressure)")
                .register(meterRegistry);

        Gauge.builder("app.template.flow.producer.inflight.limit", inFlightProductionSemaphore,
                        s -> inFlightLimit)
                .tag("jobId", jobId)
                .description("Max allowed in-flight tasks (backpressure limit)")
                .register(meterRegistry);

        return FlowLauncher.create(jobId, flowJoiner, flowManager, tracker, registration, resourceContext);
    }
}
