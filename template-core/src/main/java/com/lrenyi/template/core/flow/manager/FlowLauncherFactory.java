package com.lrenyi.template.core.flow.manager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.flow.api.FlowJoiner;
import com.lrenyi.template.core.flow.api.ProgressTracker;
import com.lrenyi.template.core.flow.context.FlowResourceContext;
import com.lrenyi.template.core.flow.context.Registration;
import com.lrenyi.template.core.flow.internal.BackpressureController;
import com.lrenyi.template.core.flow.internal.FlowFinalizer;
import com.lrenyi.template.core.flow.internal.FlowLauncher;
import com.lrenyi.template.core.flow.resource.FlowResourceRegistry;
import com.lrenyi.template.core.flow.storage.FlowStorage;

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
        TemplateConfigProperties.JobConfig jobConfig = registration.getJobConfig();

        Semaphore jobProducerSemaphore = new Semaphore(jobConfig.getJobProducerLimit());
        ExecutorService producerExecutor = resourceRegistry.getExecutorProvider()
                .createProducerExecutor(jobProducerSemaphore);

        FlowFinalizer<T> finalizer = new FlowFinalizer<>(resourceRegistry);
        FlowStorage<T> storage = resourceRegistry.getCacheManager()
                .getOrCreateStorage(jobId, flowJoiner, jobConfig, finalizer, tracker);

        BackpressureController backpressureController = new BackpressureController(storage);
        FlowResourceContext resourceContext = FlowResourceContext.builder()
                .resourceRegistry(resourceRegistry)
                .flowManager(flowManager)
                .jobProducerSemaphore(jobProducerSemaphore)
                .storage(storage)
                .backpressureController(backpressureController)
                .producerExecutor(producerExecutor)
                .build();

        return FlowLauncher.create(jobId, flowJoiner, flowManager, tracker, registration, resourceContext);
    }
}
