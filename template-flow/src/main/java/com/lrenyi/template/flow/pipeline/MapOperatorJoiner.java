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
 * <p>存储类型说明：线性阶段应尽快按 FIFO 出库，避免数据在本段 storage 中以 TTL 语义滞留，影响
 * {@code in_flight_consumer_used}、consumer 使用率与端到端吞吐观测，因此默认使用 {@link FlowStorageType#QUEUE}。</p>
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
        return FlowStorageType.QUEUE;
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
