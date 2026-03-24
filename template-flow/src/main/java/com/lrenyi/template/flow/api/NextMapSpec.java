package com.lrenyi.template.flow.api;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * {@link FlowPipeline.Builder#nextMap(NextMapSpec)} 的配置载体：驻留类型、下游类型、映射与存储消费节拍等。
 * 内嵌攒批参数见 {@link EmbeddedBatchSpec}，与 {@link FlowPipeline.Builder#nextMap(NextMapSpec, EmbeddedBatchSpec)} 搭配使用。
 * 后续若需增加可选参数，优先在本类型上扩展，以保持 {@link FlowPipeline.Builder} 方法签名稳定。
 *
 * @param <T> 本段 {@link com.lrenyi.template.flow.storage.FlowStorage} 驻留元素类型
 * @param <R> 本段产出类型（亦即下游阶段元素类型）
 */
public record NextMapSpec<T, R>(Class<T> storageElementType, Class<R> outputType, Function<T, R> cacheProducer,
                                long consumeInterval, TimeUnit consumeIntervalUnit) {

    public NextMapSpec {
        Objects.requireNonNull(storageElementType, "storageElementType");
        Objects.requireNonNull(outputType, "outputType");
        Objects.requireNonNull(cacheProducer, "cacheProducer");
        Objects.requireNonNull(consumeIntervalUnit, "consumeIntervalUnit");
        if (consumeIntervalUnit.toMillis(consumeInterval) <= 0) {
            throw new IllegalArgumentException("consume interval must be positive in milliseconds");
        }
    }

    /**
     * 与直接构造等价，便于链式调用处阅读。
     */
    public static <T, R> NextMapSpec<T, R> of(Class<T> storageElementType,
        Class<R> outputType,
        Function<T, R> cacheProducer,
        long consumeInterval,
        TimeUnit consumeIntervalUnit) {
        return new NextMapSpec<>(storageElementType, outputType, cacheProducer, consumeInterval, consumeIntervalUnit);
    }
}
