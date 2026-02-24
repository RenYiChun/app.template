package com.lrenyi.template.core.flow.manager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.function.IntSupplier;
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

        // 缓存满时必背压；消费许可耗尽时背压
        IntSupplier consumerPermits = () -> resourceRegistry.getGlobalSemaphore().availablePermits();
        BackpressureController backpressureController = new BackpressureController(storage, consumerPermits);

        // 在途生产上限：封顶 Wait(Q)，避免 OOM；达限时 launch() 入口阻塞，背压传到调用 launch 的上层；配置为 0 时用 1 倍全局消费许可数
        int inFlightLimit = jobConfig.getMaxInFlightProduction() > 0
                ? jobConfig.getMaxInFlightProduction()
                : resourceRegistry.getGlobalConfig().getGlobalSemaphoreMaxLimit();
        Semaphore inFlightProductionSemaphore = new Semaphore(inFlightLimit, true);

        FlowResourceContext resourceContext = FlowResourceContext.builder()
                .resourceRegistry(resourceRegistry)
                .flowManager(flowManager)
                .jobProducerSemaphore(jobProducerSemaphore)
                .storage(storage)
                .backpressureController(backpressureController)
                .producerExecutor(producerExecutor)
                .inFlightProductionSemaphore(inFlightProductionSemaphore)
                .build();

        return FlowLauncher.create(jobId, flowJoiner, flowManager, tracker, registration, resourceContext);
    }
}
