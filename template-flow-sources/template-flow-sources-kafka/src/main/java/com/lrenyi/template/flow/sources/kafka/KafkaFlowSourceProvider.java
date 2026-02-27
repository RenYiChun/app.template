package com.lrenyi.template.flow.sources.kafka;

import java.time.Duration;
import java.util.List;
import com.lrenyi.template.flow.api.FlowSource;
import com.lrenyi.template.flow.api.FlowSourceAdapters;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;

/**
 * 多子流 Kafka 提供者：为每个 {@link KafkaConsumer} 提供一个 {@link FlowSource}，
 * 引擎会在独立虚拟线程中消费每个子流，实现多分区并行拉取。
 * <p>
 * 典型用法：多分区时创建多个 Consumer（如每分区一个，或按 assign 分配），
 * 传入本 Provider；引擎按 {@link FlowSourceProvider} 语义并发消费各子流。
 *
 * @param <T> 业务产出类型，由 mapper 从 ConsumerRecord 转换得到
 */
public final class KafkaFlowSourceProvider<T> implements FlowSourceProvider<T> {
    
    private final FlowSourceProvider<T> delegate;
    
    /**
     * @param consumers   已配置并完成 subscribe/assign 的 Kafka Consumer 列表，非 null 非空；
     *                    每元素对应一个子流（如一个分区或一组分区）
     * @param mapper      ConsumerRecord 转 T，非 null
     * @param pollTimeout 无数据时 poll 等待时长，非 null
     */
    public KafkaFlowSourceProvider(List<KafkaConsumer<?, ?>> consumers,
                                   java.util.function.Function<ConsumerRecord<?, ?>, T> mapper,
                                   Duration pollTimeout) {
        if (consumers == null || consumers.isEmpty()) {
            throw new IllegalArgumentException("consumers 非空");
        }
        if (mapper == null) {
            throw new IllegalArgumentException("mapper 非 null");
        }
        Duration timeout = pollTimeout != null ? pollTimeout : Duration.ofMillis(1000);
        List<FlowSource<T>> sources = consumers.stream()
                                               .<FlowSource<T>>map(c -> new KafkaFlowSource<>(c, mapper, timeout))
                                               .toList();
        this.delegate = FlowSourceAdapters.fromFlowSources(sources);
    }
    
    @Override
    public boolean hasNextSubSource() throws InterruptedException {
        return delegate.hasNextSubSource();
    }
    
    @Override
    public FlowSource<T> nextSubSource() {
        return delegate.nextSubSource();
    }
    
    @Override
    public void close() {
        delegate.close();
    }
}
