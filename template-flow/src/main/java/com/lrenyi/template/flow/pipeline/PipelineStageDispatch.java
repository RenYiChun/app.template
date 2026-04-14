package com.lrenyi.template.flow.pipeline;

import java.util.List;
import java.util.function.Consumer;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.model.EgressReason;

/**
 * 管道阶段在 delegate 回调之后、向下游下发的策略；由 {@link PipelineDispatchFactories} 在构建期装配，
 * 使 {@link PipelineJoinerWrapper} 内无需对 {@link PipelineEmitter} / {@link PipelineStageOutput} 做 {@code instanceof}。
 *
 * @param <I> 阶段输入类型
 * @param <O> 下发给下游的类型
 */
public interface PipelineStageDispatch<I, O> {

    /**
     * 若 delegate 为 {@link PipelineEmitter}，绑定主动下发通道。
     */
    void wireEmitter(FlowJoiner<I> delegate, Consumer<O> forwardDirect);

    /**
     * 在 {@code delegate.onPairConsumed} 之后调用；{@code emitDownstream} 每次接受一批待下发列表（可多次调用）。
     */
    void afterPairConsumed(I existing, I incoming, Consumer<List<O>> emitDownstream);

    /**
     * 在 {@code delegate.onSingleConsumed} 之后调用。
     */
    void afterSingleConsumed(I item, EgressReason reason, Consumer<List<O>> emitDownstream);

    /**
     * 允许实现直接下发单条元素，避免为单元素结果额外创建 List。
     * 默认回退到批量下发语义。
     */
    default void afterPairConsumed(I existing,
                                   I incoming,
                                   Consumer<O> emitOneDownstream,
                                   Consumer<List<O>> emitBatchDownstream) {
        afterPairConsumed(existing, incoming, emitBatchDownstream);
    }

    /**
     * 允许实现直接下发单条元素，避免为单元素结果额外创建 List。
     * 默认回退到批量下发语义。
     */
    default void afterSingleConsumed(I item,
                                     EgressReason reason,
                                     Consumer<O> emitOneDownstream,
                                     Consumer<List<O>> emitBatchDownstream) {
        afterSingleConsumed(item, reason, emitBatchDownstream);
    }
}
