package com.lrenyi.template.flow.api;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 将攒批挂在本段 {@link FlowPipeline.Builder#nextMap(NextMapSpec, EmbeddedBatchSpec)} /
 * {@link FlowPipeline.Builder#nextStage(NextStageSpec, EmbeddedBatchSpec)} 出口时的参数（与独立 {@code aggregate} 段语义对齐）。
 *
 * @param batchSize 单批最大条数（&gt;0）
 * @param timeout   时间窗长度
 * @param unit      时间单位
 */
public record EmbeddedBatchSpec(int batchSize, long timeout, TimeUnit unit) {

    public EmbeddedBatchSpec {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        Objects.requireNonNull(unit, "unit");
    }

    /**
     * 与构造等价，便于链式调用处阅读。
     */
    public static EmbeddedBatchSpec of(int batchSize, long timeout, TimeUnit unit) {
        return new EmbeddedBatchSpec(batchSize, timeout, unit);
    }
}
