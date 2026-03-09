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
 * Queue 存储工厂实现
 */
public class QueueFlowStorageFactory implements FlowStorageFactory {
    
    @Override
    public FlowStorageType getSupportedType() {
        return FlowStorageType.QUEUE;
    }
    
    @Override
    public boolean supports(FlowStorageType type) {
        return type == FlowStorageType.QUEUE;
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
        return new QueueFlowStorage<>(config.getLimits().getPerJob().getStorageCapacity(),
                joiner,
                progressTracker,
                finalizer,
                egressHandler,
                jobId,
                config.getLimits().getPerJob().getQueuePollIntervalMill(),
                meterRegistry
        );
    }
    
    @Override
    public int getPriority() {
        return 10;
    }
}
