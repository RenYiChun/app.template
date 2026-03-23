package com.lrenyi.template.flow.storage;

import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.internal.FlowEgressHandler;
import com.lrenyi.template.flow.internal.FlowFinalizer;
import com.lrenyi.template.flow.model.FlowStorageType;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 受控超时存储工厂实现（BoundedTimedFlowStorage）。
 */
public class BoundedTimedFlowStorageFactory implements FlowStorageFactory {
    
    @Override
    public FlowStorageType getSupportedType() {
        return FlowStorageType.LOCAL_BOUNDED;
    }
    
    @Override
    public boolean supports(FlowStorageType type) {
        return type == FlowStorageType.LOCAL_BOUNDED;
    }
    
    @Override
    public <T> FlowStorage<T> createStorage(String jobId,
            FlowJoiner<T> joiner,
            TemplateConfigProperties.Flow config,
            FlowFinalizer<T> finalizer,
            ProgressTracker progressTracker,
            FlowEgressHandler<T> egressHandler,
            FlowResourceRegistry resourceRegistry,
            MeterRegistry meterRegistry) {
        TemplateConfigProperties.Flow.Global global = config.getLimits().getGlobal();
        TemplateConfigProperties.Flow.PerJob perJob = config.getLimits().getPerJob();
        long evictionScanMillis = joiner.storageConsumerTickIntervalMillis()
                .orElseGet(() -> perJob.getEffectiveEvictionScanIntervalMill(global));
        return new BoundedTimedFlowStorage<>(config,
                                             joiner,
                                             progressTracker,
                                             finalizer,
                                             egressHandler, meterRegistry,
                                             jobId,
                                             evictionScanMillis
        );
    }
    
    @Override
    public int getPriority() {
        return 5;
    }
}

