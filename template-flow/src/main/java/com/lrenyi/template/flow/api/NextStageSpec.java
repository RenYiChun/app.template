package com.lrenyi.template.flow.api;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * {@link FlowPipeline.Builder#nextStage(NextStageSpec)} 的配置载体：下游类型、Joiner、列表转换器及可选的配对产出。
 * 内嵌攒批参数见 {@link EmbeddedBatchSpec}，与 {@link FlowPipeline.Builder#nextStage(NextStageSpec, EmbeddedBatchSpec)} 搭配使用。
 * 后续若需增加可选参数，优先在本类型上扩展，以保持 {@link FlowPipeline.Builder} 方法签名稳定。
 *
 * @param <T> 本段输入（当前管道阶段元素类型）
 * @param <R> 本段产出、亦即下游阶段元素类型
 */
public final class NextStageSpec<T, R> {
    private final Class<R> outputClass;
    private final FlowJoiner<T> joiner;
    private final Function<T, List<R>> transformer;
    private final BiFunction<T, T, List<R>> pairOutput;
    private final Integer storageCapacity;
    private final String displayName;

    private NextStageSpec(Builder<T, R> builder) {
        this.outputClass = Objects.requireNonNull(builder.outputClass, "outputClass");
        this.joiner = Objects.requireNonNull(builder.joiner, "joiner");
        this.transformer = Objects.requireNonNull(builder.transformer, "transformer");
        this.pairOutput = builder.pairOutput;
        this.storageCapacity = builder.storageCapacity;
        this.displayName = builder.displayName;
        if (storageCapacity != null && storageCapacity <= 0) {
            throw new IllegalArgumentException("storageCapacity must be > 0 when set, got " + storageCapacity);
        }
    }

    public static <T, R> Builder<T, R> builder(Class<R> outputClass,
                                               FlowJoiner<T> joiner,
                                               Function<T, List<R>> transformer) {
        return new Builder<>(outputClass, joiner, transformer);
    }

    public Class<R> getOutputClass() {
        return outputClass;
    }

    public FlowJoiner<T> getJoiner() {
        return joiner;
    }

    public Function<T, List<R>> getTransformer() {
        return transformer;
    }

    public BiFunction<T, T, List<R>> getPairOutput() {
        return pairOutput;
    }

    public Integer getStorageCapacity() {
        return storageCapacity;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static final class Builder<T, R> {
        private final Class<R> outputClass;
        private final FlowJoiner<T> joiner;
        private final Function<T, List<R>> transformer;
        private BiFunction<T, T, List<R>> pairOutput;
        private Integer storageCapacity;
        private String displayName;

        private Builder(Class<R> outputClass,
                        FlowJoiner<T> joiner,
                        Function<T, List<R>> transformer) {
            this.outputClass = Objects.requireNonNull(outputClass, "outputClass");
            this.joiner = Objects.requireNonNull(joiner, "joiner");
            this.transformer = Objects.requireNonNull(transformer, "transformer");
        }

        public Builder<T, R> pairOutput(BiFunction<T, T, List<R>> pairOutput) {
            this.pairOutput = pairOutput;
            return this;
        }

        public Builder<T, R> storageCapacity(Integer storageCapacity) {
            this.storageCapacity = storageCapacity;
            return this;
        }

        public Builder<T, R> displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public NextStageSpec<T, R> build() {
            return new NextStageSpec<>(this);
        }
    }
}
