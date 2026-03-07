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
 * Caffeine 存储工厂实现
 */
public class CaffeineFlowStorageFactory implements FlowStorageFactory {
    
    @Override
    public FlowStorageType getSupportedType() {
        return FlowStorageType.CAFFEINE;
    }
    
    @Override
    public boolean supports(FlowStorageType type) {
        return type == FlowStorageType.CAFFEINE;
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
        return new CaffeineFlowStorage<>(config, joiner, finalizer, progressTracker, meterRegistry, egressHandler, jobId
        );
    }
    
    @Override
    public int getPriority() {
        return 10;
    }
}
