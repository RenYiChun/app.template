package com.lrenyi.template.flow.api;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * {@link FlowPipeline.Builder#nextStage(NextStageSpec)} 的配置载体：下游类型、Joiner、列表转换器及可选的配对产出。
 * 后续若需增加可选参数，优先在本类型上扩展，以保持 {@link FlowPipeline.Builder} 方法签名稳定。
 *
 * @param <T> 本段输入（当前管道阶段元素类型）
 * @param <R> 本段产出、亦即下游阶段元素类型
 * @param pairOutput 在 {@code onPairConsumed} 之后向下游的产出；为 {@code null} 时表示未注入，沿用引擎兼容行为
 */
public record NextStageSpec<T, R>(Class<R> outputClass, FlowJoiner<T> joiner, Function<T, List<R>> transformer,
                                  BiFunction<T, T, List<R>> pairOutput) {

    public NextStageSpec {
        Objects.requireNonNull(outputClass, "outputClass");
        Objects.requireNonNull(joiner, "joiner");
        Objects.requireNonNull(transformer, "transformer");
    }

    /**
     * 无配对产出覆盖（等价于原三参数 {@code nextStage}）。
     */
    public static <T, R> NextStageSpec<T, R> of(Class<R> outputClass,
        FlowJoiner<T> joiner,
        Function<T, List<R>> transformer) {
        return new NextStageSpec<>(outputClass, joiner, transformer, null);
    }

    /**
     * 含「配对消费后」向下游的显式产出（等价于原四参数 {@code nextStage}）。
     */
    public static <T, R> NextStageSpec<T, R> of(Class<R> outputClass,
        FlowJoiner<T> joiner,
        Function<T, List<R>> transformer,
        BiFunction<T, T, List<R>> pairOutput) {
        return new NextStageSpec<>(outputClass, joiner, transformer, pairOutput);
    }
}
