package com.lrenyi.template.flow.sources.kafka;

import java.time.Duration;
import java.util.Iterator;
import java.util.NoSuchElementException;
import com.lrenyi.template.core.flow.api.FlowSource;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import lombok.extern.slf4j.Slf4j;

/**
 * 单子流 Kafka 数据源：包装一个 {@link KafkaConsumer}，按顺序产出 T。
 * 由引擎在虚拟线程中拉取（hasNext/next），适用于单 partition 或单 consumer 场景；
 * 多 partition 时可构造多个 KafkaFlowSource 或使用 {@link com.lrenyi.template.core.flow.api.FlowSourceProvider}。
 * <p>
 * 阻塞与中断：{@link #hasNext()} 内无数据时调用 {@code consumer.poll(Duration)}；
 * 若当前线程被中断则抛 {@link InterruptedException}；若 poll 抛 {@link WakeupException} 且线程已中断则转为 InterruptedException。
 *
 * @param <T> 业务产出类型，由 mapper 从 ConsumerRecord 转换得到
 */
@Slf4j
public final class KafkaFlowSource<T> implements FlowSource<T> {
    
    private final KafkaConsumer<?, ?> consumer;
    private final java.util.function.Function<ConsumerRecord<?, ?>, T> mapper;
    private final Duration pollTimeout;
    
    private Iterator<? extends ConsumerRecord<?, ?>> currentBatch;
    private boolean closed;
    
    /**
     * @param consumer    Kafka Consumer，调用方负责配置与 subscribe/assign；本类负责在 close() 时关闭
     * @param mapper      ConsumerRecord 转 T，非 null
     * @param pollTimeout 无数据时 poll 等待时长，非 null
     */
    public KafkaFlowSource(KafkaConsumer<?, ?> consumer,
                           java.util.function.Function<ConsumerRecord<?, ?>, T> mapper,
                           Duration pollTimeout) {
        this.consumer = consumer;
        this.mapper = mapper;
        this.pollTimeout = pollTimeout != null ? pollTimeout : Duration.ofMillis(1000);
    }
    
    @Override
    public boolean hasNext() throws InterruptedException {
        if (closed) {
            return false;
        }
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        if (currentBatch != null && currentBatch.hasNext()) {
            return true;
        }
        fetchNextBatch();
        return currentBatch != null && currentBatch.hasNext();
    }
    
    private void fetchNextBatch() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        try {
            ConsumerRecords<?, ?> records = consumer.poll(pollTimeout);
            currentBatch = records.iterator();
        } catch (WakeupException e) {
            closed = true;
            Thread.currentThread().interrupt();
            throw new InterruptedException("Kafka consumer woken up");
        }
    }
    
    @Override
    public T next() {
        if (closed) {
            throw new NoSuchElementException();
        }
        if (currentBatch == null || !currentBatch.hasNext()) {
            throw new NoSuchElementException();
        }
        ConsumerRecord<?, ?> rd = currentBatch.next();
        return mapper.apply(rd);
    }
    
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            consumer.close();
        } catch (Exception e) {
            log.debug("Kafka consumer close failed, ignoring for best-effort release", e);
        }
    }
}
