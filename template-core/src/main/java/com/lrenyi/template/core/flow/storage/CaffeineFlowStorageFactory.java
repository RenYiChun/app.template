package com.lrenyi.template.core.flow.storage;

import java.util.concurrent.ScheduledExecutorService;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.flow.FlowJoiner;
import com.lrenyi.template.core.flow.ProgressTracker;
import com.lrenyi.template.core.flow.config.FlowStorageType;
import com.lrenyi.template.core.flow.impl.FlowFinalizer;

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
                                            TemplateConfigProperties.JobConfig config,
                                            FlowFinalizer<T> finalizer,
                                            ProgressTracker progressTracker,
                                            ScheduledExecutorService storageEgressExecutor) {
        return new CaffeineFlowStorage<>(config.getMaxCacheSize(),
                                         config.getTtlMill(),
                                         joiner,
                                         finalizer,
                                         progressTracker,
                                         storageEgressExecutor
        );
    }
    
    @Override
    public int getPriority() {
        return 10; // 默认实现，优先级较高
    }
}
