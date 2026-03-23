package com.lrenyi.template.flow.pipeline;

import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.model.FlowStorageType;

/**
 * 仅用于 {@link com.lrenyi.template.flow.api.FlowPipeline.Builder#nextMap(com.lrenyi.template.flow.api.NextMapSpec)}
 * 的线性映射占位 Joiner：
 * 业务映射在 {@code transformer} 中完成；本类提供唯一 {@link #joinKey}，避免固定 key 在存储层冲突。
 * <p>存储类型说明：线性阶段语义上更接近 FIFO（{@link FlowStorageType#QUEUE}），但当前
 * {@link com.lrenyi.template.flow.storage.QueueFlowStorage}
 * 主要依赖可配置的队列轮询出队（见 {@code app.template.flow.limits.per-job.queue-poll-interval-mill}，默认较大），入队后不会立即触发消费；
 * 若在此默认改为 {@code QUEUE}，易导致端到端延迟与完成判定异常。故仍使用 {@link FlowStorageType#LOCAL_BOUNDED}，每条唯一 key 单槽短驻留，
 * 与「逐条通过」一致。若将来队列实现支持入队即唤醒或默认轮询间隔足够小，可再评估改为 {@code QUEUE}。</p>
 *
 * @param <T> 输入元素类型
 */
public final class MapOperatorJoiner<T> implements FlowJoiner<T> {
    private final Class<T> dataType;
    private final long consumerTickIntervalMillis;
    private final AtomicLong sequence = new AtomicLong();

    public MapOperatorJoiner(Class<T> dataType, long consumerTickIntervalMillis) {
        if (consumerTickIntervalMillis <= 0) {
            throw new IllegalArgumentException("consumerTickIntervalMillis must be positive");
        }
        this.dataType = dataType;
        this.consumerTickIntervalMillis = consumerTickIntervalMillis;
    }

    @Override
    public OptionalLong storageConsumerTickIntervalMillis() {
        return OptionalLong.of(consumerTickIntervalMillis);
    }

    @Override
    public FlowStorageType getStorageType() {
        return FlowStorageType.LOCAL_BOUNDED;
    }

    @Override
    public Class<T> getDataType() {
        return dataType;
    }

    @Override
    public FlowSourceProvider<T> sourceProvider() {
        return null;
    }

    @Override
    public String joinKey(T item) {
        return "MAP:" + sequence.incrementAndGet();
    }

    @Override
    public void onPairConsumed(T existing, T incoming, String jobId) {
    }

    @Override
    public void onSingleConsumed(T item, String jobId, EgressReason reason) {
    }
}
