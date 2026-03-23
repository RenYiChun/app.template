package com.lrenyi.template.flow.pipeline;

import java.util.concurrent.atomic.AtomicLong;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.model.FlowStorageType;

/**
 * 仅用于 {@link com.lrenyi.template.flow.api.FlowPipeline.Builder#nextMap(Class, java.util.function.Function)} 的线性映射占位 Joiner：
 * 业务映射在 {@code transformer} 中完成；本类提供唯一 {@link #joinKey}，避免固定 key 在存储层冲突。
 *
 * @param <T> 输入元素类型
 */
public final class MapOperatorJoiner<T> implements FlowJoiner<T> {
    private final Class<T> dataType;
    private final AtomicLong sequence = new AtomicLong();

    public MapOperatorJoiner(Class<T> dataType) {
        this.dataType = dataType;
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
