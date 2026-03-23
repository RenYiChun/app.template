package com.lrenyi.template.flow.pipeline;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.model.FlowStorageType;

/**
 * 终端 Joiner。作为管道的最后一个阶段，执行最终的业务落库或收尾逻辑。
 * <p>存储类型说明同 {@link MapOperatorJoiner}（线性阶段与 {@link FlowStorageType#QUEUE} 的取舍）。</p>
 *
 * @param <T> 到达终端的数据类型
 */
public class SinkJoiner<T> implements FlowJoiner<T> {
    private final Class<T> dataType;
    private final BiConsumer<T, String> onSink;
    /**
     * 终端阶段不应因固定 joinKey 导致多条到达数据在同一槽位上被配对/顶掉；
     * 每条入站分配唯一键，保证按条进入 SINGLE_CONSUMED 并触发 onSink。
     */
    private final AtomicLong sinkSequence = new AtomicLong();

    public SinkJoiner(Class<T> dataType, BiConsumer<T, String> onSink) {
        this.dataType = dataType;
        this.onSink = onSink;
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
        return "SINK:" + sinkSequence.incrementAndGet();
    }

    @Override
    public void onPairConsumed(T existing, T incoming, String jobId) {
    }

    @Override
    public void onSingleConsumed(T item, String jobId, EgressReason reason) {
        if (reason == EgressReason.SINGLE_CONSUMED || reason == EgressReason.PAIR_MATCHED) {
            onSink.accept(item, jobId);
        }
    }
}
