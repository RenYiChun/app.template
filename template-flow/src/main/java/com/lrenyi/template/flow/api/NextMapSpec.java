package com.lrenyi.template.flow.api;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import com.lrenyi.template.flow.model.FlowConsumeExecutionMode;
import lombok.Getter;

/**
 * {@link FlowPipeline.Builder#nextMap(NextMapSpec)} 的配置载体：驻留类型、下游类型、映射与存储消费节拍等。
 * 内嵌攒批参数见 {@link EmbeddedBatchSpec}，与 {@link FlowPipeline.Builder#nextMap(NextMapSpec, EmbeddedBatchSpec)} 搭配使用。
 * 后续若需增加可选参数，优先在本类型上扩展，以保持 {@link FlowPipeline.Builder} 方法签名稳定。
 *
 * @param <T> 本段 {@link com.lrenyi.template.flow.storage.FlowStorage} 驻留元素类型
 * @param <R> 本段产出类型（亦即下游阶段元素类型）
 */
@Getter
public final class NextMapSpec<T, R> {
    private final Class<T> storageElementType;
    private final Class<R> outputType;
    private final Function<T, R> cacheProducer;
    private final long consumeInterval;
    private final TimeUnit consumeIntervalUnit;
    private final Integer storageCapacity;
    private final Integer consumerThreads;
    private final String displayName;
    private final FlowConsumeExecutionMode consumeExecutionMode;

    private NextMapSpec(Builder<T, R> builder) {
        this.storageElementType = Objects.requireNonNull(builder.storageElementType, "storageElementType");
        this.outputType = Objects.requireNonNull(builder.outputType, "outputType");
        this.cacheProducer = Objects.requireNonNull(builder.cacheProducer, "cacheProducer");
        this.consumeInterval = builder.consumeInterval;
        this.consumeIntervalUnit = Objects.requireNonNull(builder.consumeIntervalUnit, "consumeIntervalUnit");
        this.storageCapacity = builder.storageCapacity;
        this.consumerThreads = builder.consumerThreads;
        this.displayName = builder.displayName;
        this.consumeExecutionMode = builder.consumeExecutionMode;
        if (consumeIntervalUnit.toMillis(consumeInterval) <= 0) {
            throw new IllegalArgumentException("consume interval must be positive in milliseconds");
        }
        if (storageCapacity != null && storageCapacity <= 0) {
            throw new IllegalArgumentException("storageCapacity must be > 0 when set, got " + storageCapacity);
        }
        if (consumerThreads != null && consumerThreads <= 0) {
            throw new IllegalArgumentException("consumerThreads must be > 0 when set, got " + consumerThreads);
        }
    }

    public static <T, R> Builder<T, R> builder(Class<T> storageElementType,
                                               Class<R> outputType,
                                               Function<T, R> cacheProducer) {
        return new Builder<>(storageElementType, outputType, cacheProducer);
    }

    public static final class Builder<T, R> {
        private final Class<T> storageElementType;
        private final Class<R> outputType;
        private final Function<T, R> cacheProducer;
        private long consumeInterval;
        private TimeUnit consumeIntervalUnit;
        private Integer storageCapacity;
        private Integer consumerThreads;
        private String displayName;
        private FlowConsumeExecutionMode consumeExecutionMode;

        private Builder(Class<T> storageElementType, Class<R> outputType, Function<T, R> cacheProducer) {
            this.storageElementType = Objects.requireNonNull(storageElementType, "storageElementType");
            this.outputType = Objects.requireNonNull(outputType, "outputType");
            this.cacheProducer = Objects.requireNonNull(cacheProducer, "cacheProducer");
        }

        public Builder<T, R> consumeInterval(long consumeInterval, TimeUnit consumeIntervalUnit) {
            this.consumeInterval = consumeInterval;
            this.consumeIntervalUnit = Objects.requireNonNull(consumeIntervalUnit, "consumeIntervalUnit");
            return this;
        }

        public Builder<T, R> storageCapacity(Integer storageCapacity) {
            this.storageCapacity = storageCapacity;
            return this;
        }

        public Builder<T, R> consumerThreads(Integer consumerThreads) {
            this.consumerThreads = consumerThreads;
            return this;
        }

        public Builder<T, R> displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder<T, R> consumeExecutionMode(FlowConsumeExecutionMode consumeExecutionMode) {
            this.consumeExecutionMode = consumeExecutionMode;
            return this;
        }

        public NextMapSpec<T, R> build() {
            return new NextMapSpec<>(this);
        }
    }
}
