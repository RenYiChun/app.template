package com.lrenyi.template.flow.pipeline;

import java.util.List;
import com.lrenyi.template.flow.api.EmbeddedBatchSpec;
import com.lrenyi.template.flow.api.FlowJoiner;

/**
 * 管道阶段定义。
 *
 * @param <I>          本阶段接收的输入数据类型
 * @param <O>          本阶段产出并传递给下一阶段的输出数据类型
 * @param joiner       负责本阶段业务逻辑的 Joiner
 * @param branchStages 分叉阶段专属：包含多个并行的子管道定义。
 *                     如果是普通顺序阶段，该字段为 null 或空。
 * @param dispatch       配对/单条下发策略（已内联含 transformer 语义）；fork 阶段为 null。
 * @param embeddedBatch  非 null 时在本阶段 Joiner 出口侧攒批后再 {@code push} 下游，不增加独立 aggregate Launcher
 * @param storageCapacityOverride 非 null 时覆盖本阶段 {@code limits.per-job.storage-capacity}，与运行时基底 flow 合并为独立快照
 * @param consumerThreadsOverride 非 null 时覆盖本阶段 {@code limits.per-job.consumer-threads}，与运行时基底 flow 合并为独立快照
 */
record StageDefinition<I, O>(
        FlowJoiner<I> joiner,
        List<List<StageDefinition<?, ?>>> branchStages,
        List<String> branchNames,
        PipelineStageDispatch<I, O> dispatch,
        EmbeddedBatchSpec embeddedBatch,
        Integer storageCapacityOverride,
        Integer consumerThreadsOverride) {
    /**
     * 判断当前阶段是否为分叉（扇出）阶段。
     */
    boolean isFork() {
        return branchStages != null && !branchStages.isEmpty();
    }

    /**
     * 判断当前阶段是否为终端（Sink）阶段。
     */
    boolean isSink() {
        return joiner instanceof SinkJoiner && !isFork();
    }
}
