package com.lrenyi.template.flow.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowPipeline;
import com.lrenyi.template.flow.api.NextMapSpec;
import com.lrenyi.template.flow.api.NextStageSpec;
import com.lrenyi.template.flow.manager.FlowManager;

/**
 * FlowPipeline 构建器实现。
 * 使用 Fluent API 模式逐步编排管道阶段。
 * 由于管道各阶段涉及频繁的类型转换，内部使用 Object 类型擦除并结合 SuppressWarnings 处理。
 *
 * @param <T> 当前构建层级处理的数据类型
 */
public class FlowPipelineBuilderImpl<T> implements FlowPipeline.Builder<T> {
    private final String jobId;
    private final FlowManager flowManager;
    private final List<StageDefinition<?, ?>> stages;
    private final Class<T> currentClass;

    public FlowPipelineBuilderImpl(String jobId, Class<T> currentClass, FlowManager flowManager) {
        this(jobId, currentClass, flowManager, new ArrayList<>());
    }

    /**
     * 用于 fork 内部和链式调用的内部构造函数。
     */
    private FlowPipelineBuilderImpl(String jobId, Class<T> currentClass, FlowManager flowManager, List<StageDefinition<?, ?>> stages) {
        this.jobId = jobId;
        this.currentClass = currentClass;
        this.flowManager = flowManager;
        this.stages = stages;
    }

    @Override
    public <R> FlowPipeline.Builder<R> nextStage(NextStageSpec<T, R> spec) {
        Objects.requireNonNull(spec, "spec");
        return nextStageInternal(spec.outputClass(), spec.joiner(), spec.transformer(), spec.pairOutput());
    }

    @Override
    public <R> FlowPipeline.Builder<R> nextMap(NextMapSpec<T, R> spec) {
        Objects.requireNonNull(spec, "spec");
        if (!spec.storageElementType().equals(currentClass)) {
            throw new IllegalArgumentException(
                    "storageElementType must match current stage type: expected " + currentClass.getName()
                            + ", got " + spec.storageElementType().getName());
        }
        long millis = spec.consumeIntervalUnit().toMillis(spec.consumeInterval());
        MapOperatorJoiner<T> joiner = new MapOperatorJoiner<>(currentClass, millis);
        Function<T, List<R>> tf = t -> {
            R r = spec.cacheProducer().apply(t);
            if (r == null) {
                return List.of();
            }
            return List.of(r);
        };
        return nextStageInternal(spec.outputType(), joiner, tf, null);
    }

    @SuppressWarnings("unchecked")
    private <R> FlowPipeline.Builder<R> nextStageInternal(Class<R> outputClass,
                                                          FlowJoiner<T> joiner,
                                                          Function<T, List<R>> transformer,
                                                          BiFunction<T, T, List<R>> explicitPairOutput) {
        Function<T, List<R>> tf = transformer != null ? transformer : t -> List.of((R) t);
        PipelineStageDispatch<T, R> dispatch = PipelineDispatchFactories.create(joiner, tf, explicitPairOutput);
        stages.add(new StageDefinition<Object, Object>((FlowJoiner<Object>) joiner,
                null,
                (PipelineStageDispatch<Object, Object>) (Object) dispatch));
        return new FlowPipelineBuilderImpl<>(jobId, outputClass, flowManager, stages);
    }

    @Override
    @SuppressWarnings("unchecked")
    public FlowPipeline<?> fork(Consumer<FlowPipeline.Builder<T>>... branches) {
        List<List<StageDefinition<?, ?>>> branchStages = new ArrayList<>();
        for (Consumer<FlowPipeline.Builder<T>> branch : branches) {
            FlowPipelineBuilderImpl<T> subBuilder = new FlowPipelineBuilderImpl<>(jobId, currentClass, flowManager);
            branch.accept(subBuilder);
            branchStages.add(subBuilder.stages);
        }
        stages.add(new StageDefinition<Object, Object>(null, branchStages, null));
        return build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public FlowPipeline.Builder<List<T>> aggregate(int batchSize, long timeout, TimeUnit unit) {
        AggregationJoiner<T> aggregator = new AggregationJoiner<>(currentClass, batchSize, timeout, unit);
        Function<Object, List<Object>> identity = List::of;
        PipelineStageDispatch<Object, Object> dispatch = PipelineDispatchFactories.create((FlowJoiner<Object>) aggregator, identity, null);
        stages.add(new StageDefinition<Object, Object>((FlowJoiner<Object>) aggregator, null, dispatch));
        return new FlowPipelineBuilderImpl<>(jobId, (Class<List<T>>) (Class<?>) List.class, flowManager, stages);
    }

    @Override
    @SuppressWarnings("unchecked")
    public FlowPipeline<?> sink(Class<T> sinkClass, BiConsumer<T, String> onSink) {
        Class<T> actualClass = sinkClass != null ? sinkClass : currentClass;
        SinkJoiner<T> sinkJoiner = new SinkJoiner<>(actualClass, onSink);
        Function<Object, List<Object>> identity = List::of;
        PipelineStageDispatch<Object, Object> dispatch = PipelineDispatchFactories.create((FlowJoiner<Object>) sinkJoiner, identity, null);
        stages.add(new StageDefinition<>((FlowJoiner<Object>) sinkJoiner, null, dispatch));
        return build();
    }

    private FlowPipeline<Object> build() {
        return new FlowPipelineImpl<>(jobId, flowManager, stages);
    }
}
