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
 * @param pairOutput 在 {@code onPairConsumed} 之后向下游的产出；为 {@code null} 时表示未注入，沿用引擎兼容行为
 * @param storageCapacity 非 null 时为本阶段单独设置 {@code limits.per-job.storage-capacity}（条数上限，必须 {@code > 0}）
 */
public record NextStageSpec<T, R>(Class<R> outputClass, FlowJoiner<T> joiner, Function<T, List<R>> transformer,
                                  BiFunction<T, T, List<R>> pairOutput, Integer storageCapacity) {

    public NextStageSpec {
        Objects.requireNonNull(outputClass, "outputClass");
        Objects.requireNonNull(joiner, "joiner");
        Objects.requireNonNull(transformer, "transformer");
        if (storageCapacity != null && storageCapacity <= 0) {
            throw new IllegalArgumentException("storageCapacity must be > 0 when set, got " + storageCapacity);
        }
    }

    /**
     * 无配对产出覆盖（等价于原三参数 {@code nextStage}）。
     */
    public static <T, R> NextStageSpec<T, R> of(Class<R> outputClass,
        FlowJoiner<T> joiner,
        Function<T, List<R>> transformer) {
        return new NextStageSpec<>(outputClass, joiner, transformer, null, null);
    }

    /**
     * 含「配对消费后」向下游的显式产出（等价于原四参数 {@code nextStage}）。
     */
    public static <T, R> NextStageSpec<T, R> of(Class<R> outputClass,
        FlowJoiner<T> joiner,
        Function<T, List<R>> transformer,
        BiFunction<T, T, List<R>> pairOutput) {
        return new NextStageSpec<>(outputClass, joiner, transformer, pairOutput, null);
    }

    /**
     * 含本阶段存储条数上限覆盖（覆盖 {@code limits.per-job.storage-capacity}）。
     */
    public static <T, R> NextStageSpec<T, R> of(Class<R> outputClass,
        FlowJoiner<T> joiner,
        Function<T, List<R>> transformer,
        BiFunction<T, T, List<R>> pairOutput,
        int storageCapacity) {
        return new NextStageSpec<>(outputClass, joiner, transformer, pairOutput, storageCapacity);
    }
}
