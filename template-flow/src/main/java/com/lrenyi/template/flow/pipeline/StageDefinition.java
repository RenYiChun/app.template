package com.lrenyi.template.flow.pipeline;

import java.util.List;
import com.lrenyi.template.flow.api.EmbeddedBatchSpec;
import com.lrenyi.template.flow.api.FlowJoiner;

/**
 * 管道阶段定义。
 *
 * @param <I> 本阶段接收的输入数据类型
 * @param <O> 本阶段产出并传递给下一阶段的输出数据类型
 */
final class StageDefinition<I, O> {
    private final FlowJoiner<I> joiner;
    private final List<List<StageDefinition<?, ?>>> branchStages;
    private final List<String> branchNames;
    private final PipelineStageDispatch<I, O> dispatch;
    private final EmbeddedBatchSpec embeddedBatch;
    private final Integer storageCapacityOverride;
    private final Integer consumerThreadsOverride;
    private final String displayNameOverride;

    private StageDefinition(Builder<I, O> builder) {
        this.joiner = builder.joiner;
        this.branchStages = builder.branchStages;
        this.branchNames = builder.branchNames;
        this.dispatch = builder.dispatch;
        this.embeddedBatch = builder.embeddedBatch;
        this.storageCapacityOverride = builder.storageCapacityOverride;
        this.consumerThreadsOverride = builder.consumerThreadsOverride;
        this.displayNameOverride = builder.displayNameOverride;
    }

    static <I, O> Builder<I, O> builder() {
        return new Builder<>();
    }

    FlowJoiner<I> getJoiner() {
        return joiner;
    }

    List<List<StageDefinition<?, ?>>> getBranchStages() {
        return branchStages;
    }

    List<String> getBranchNames() {
        return branchNames;
    }

    PipelineStageDispatch<I, O> getDispatch() {
        return dispatch;
    }

    EmbeddedBatchSpec getEmbeddedBatch() {
        return embeddedBatch;
    }

    Integer getStorageCapacityOverride() {
        return storageCapacityOverride;
    }

    Integer getConsumerThreadsOverride() {
        return consumerThreadsOverride;
    }

    String getDisplayNameOverride() {
        return displayNameOverride;
    }

    boolean isFork() {
        return branchStages != null && !branchStages.isEmpty();
    }

    boolean isSink() {
        return joiner instanceof SinkJoiner && !isFork();
    }

    static final class Builder<I, O> {
        private FlowJoiner<I> joiner;
        private List<List<StageDefinition<?, ?>>> branchStages;
        private List<String> branchNames;
        private PipelineStageDispatch<I, O> dispatch;
        private EmbeddedBatchSpec embeddedBatch;
        private Integer storageCapacityOverride;
        private Integer consumerThreadsOverride;
        private String displayNameOverride;

        Builder<I, O> joiner(FlowJoiner<I> joiner) {
            this.joiner = joiner;
            return this;
        }

        Builder<I, O> branchStages(List<List<StageDefinition<?, ?>>> branchStages) {
            this.branchStages = branchStages;
            return this;
        }

        Builder<I, O> branchNames(List<String> branchNames) {
            this.branchNames = branchNames;
            return this;
        }

        Builder<I, O> dispatch(PipelineStageDispatch<I, O> dispatch) {
            this.dispatch = dispatch;
            return this;
        }

        Builder<I, O> embeddedBatch(EmbeddedBatchSpec embeddedBatch) {
            this.embeddedBatch = embeddedBatch;
            return this;
        }

        Builder<I, O> storageCapacityOverride(Integer storageCapacityOverride) {
            this.storageCapacityOverride = storageCapacityOverride;
            return this;
        }

        Builder<I, O> consumerThreadsOverride(Integer consumerThreadsOverride) {
            this.consumerThreadsOverride = consumerThreadsOverride;
            return this;
        }

        Builder<I, O> displayNameOverride(String displayNameOverride) {
            this.displayNameOverride = displayNameOverride;
            return this;
        }

        StageDefinition<I, O> build() {
            return new StageDefinition<>(this);
        }
    }
}
