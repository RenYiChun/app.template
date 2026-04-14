package com.lrenyi.template.flow.pipeline;

import java.util.List;
import com.lrenyi.template.flow.model.EgressReason;

/**
 * 管道阶段在 Joiner 回调之后、向下游下发的显式产出策略。
 * <p>
 * 若 {@link com.lrenyi.template.flow.api.FlowJoiner FlowJoiner} 实现本接口，则构建期 {@link PipelineDispatchFactories} 会选择 {@code PsoDispatch}，由 {@link PipelineJoinerWrapper} 在
 * {@link com.lrenyi.template.flow.api.FlowJoiner#onPairConsumed} /
 * {@link com.lrenyi.template.flow.api.FlowJoiner#onSingleConsumed} 执行 delegate 之后，
 * 优先根据此处返回的列表下发；未实现本接口时，配对场景可使用
 * {@link com.lrenyi.template.flow.api.FlowPipeline.Builder} 带 {@code BiFunction} 的重载注入合并逻辑，
 * 否则保留与旧版兼容的「配对后对 existing、incoming 各走一遍 transformer」行为。
 * </p>
 *
 * @param <I> 本阶段输入类型（与 Joiner 一致）
 * @param <O> 下发给下游的类型
 */
public interface PipelineStageOutput<I, O> {

    /**
     * 在 {@code delegate.onPairConsumed} 之后调用。
     * <ul>
     *   <li>返回非 null：按列表逐条 {@code push} 到下游（空列表表示不下发）。</li>
     *   <li>返回 null：若 Builder 提供了 pairOutput 则使用之；否则使用旧版双路 {@code transformer} 行为。</li>
     * </ul>
     *
     * @param existing  已存在侧
     * @param incoming  新入侧
     * @return 下发列表，或 null 表示交由 Builder / 兼容路径
     */
    List<O> outputsAfterPair(I existing, I incoming);

    /**
     * 在 {@code delegate.onSingleConsumed} 之后调用（{@link com.lrenyi.template.flow.pipeline.PipelineEmitter} 仍自行下发，不会调用本方法的有效路径与 transformer 自动转发互斥）。
     * <ul>
     *   <li>返回非 null：按列表逐条下发。</li>
     *   <li>返回 null：仅在 {@code reason == EgressReason.SINGLE_CONSUMED} 时回退为 {@code transformer.apply(item)}。</li>
     * </ul>
     *
     * @param item   单条数据
     * @param reason 出口原因
     * @return 下发列表，或 null 表示回退 transformer（仅 SINGLE_CONSUMED）
     */
    List<O> outputsAfterSingle(I item, EgressReason reason);
}
