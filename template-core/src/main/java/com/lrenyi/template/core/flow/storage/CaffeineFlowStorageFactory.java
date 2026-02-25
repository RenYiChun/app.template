package com.lrenyi.template.core.flow.storage;

import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.flow.api.FlowJoiner;
import com.lrenyi.template.core.flow.api.ProgressTracker;
import com.lrenyi.template.core.flow.model.FlowStorageType;
import com.lrenyi.template.core.flow.internal.FlowFinalizer;
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
            MeterRegistry meterRegistry) {
        return new CaffeineFlowStorage<>(config.getProducer().getMaxCacheSize(),
                config.getConsumer().getTtlMill(),
                joiner,
                finalizer, progressTracker, meterRegistry, jobId);
    }

    @Override
    public int getPriority() {
        return 10;
    }
}
