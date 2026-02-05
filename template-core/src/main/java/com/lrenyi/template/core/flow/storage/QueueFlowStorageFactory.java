package com.lrenyi.template.core.flow.storage;

import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.flow.FlowJoiner;
import com.lrenyi.template.core.flow.ProgressTracker;
import com.lrenyi.template.core.flow.config.FlowStorageType;
import com.lrenyi.template.core.flow.impl.FlowFinalizer;

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
                                            TemplateConfigProperties.JobConfig config,
                                            FlowFinalizer<T> finalizer,
                                            ProgressTracker progressTracker) {
        return new QueueFlowStorage<>(config.getMaxCacheSize(),
                                      progressTracker,
                                      finalizer,
                                      jobId,
                                      config.getTtlMill()
        );
    }
    
    @Override
    public int getPriority() {
        return 10; // 默认实现，优先级较高
    }
}
