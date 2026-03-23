package com.lrenyi.template.flow.sources.kafka;

import java.time.Duration;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import com.lrenyi.template.flow.api.FlowSource;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;

/**
 * 单子流 Kafka 数据源：包装一个 {@link KafkaConsumer}，按顺序产出 T。
 *
 * @param <T> 业务产出类型
 */
@Slf4j
public final class KafkaFlowSource<T> implements FlowSource<T> {
    private static final String TAG_SOURCE_TYPE = "sourceType";
    private static final String SOURCE_KAFKA = "kafka";
    
    private final KafkaConsumer<?, ?> consumer;
    private final java.util.function.Function<ConsumerRecord<?, ?>, T> mapper;
    private final Duration pollTimeout;
    private final MeterRegistry meterRegistry;
    
    private Iterator<? extends ConsumerRecord<?, ?>> currentBatch;
    private boolean closed;
    
    public KafkaFlowSource(KafkaConsumer<?, ?> consumer,
            java.util.function.Function<ConsumerRecord<?, ?>, T> mapper,
            Duration pollTimeout) {
        this(consumer, mapper, pollTimeout, null);
    }
    
    public KafkaFlowSource(KafkaConsumer<?, ?> consumer,
            java.util.function.Function<ConsumerRecord<?, ?>, T> mapper,
            Duration pollTimeout,
            MeterRegistry meterRegistry) {
        if (consumer == null) {
            throw new IllegalArgumentException("consumer 非 null");
        }
        if (mapper == null) {
            throw new IllegalArgumentException("mapper 非 null");
        }
        this.consumer = consumer;
        this.mapper = mapper;
        this.pollTimeout = pollTimeout != null ? pollTimeout : Duration.ofMillis(1000);
        this.meterRegistry = meterRegistry;
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
        long start = System.currentTimeMillis();
        try {
            ConsumerRecords<?, ?> records = consumer.poll(pollTimeout);
            currentBatch = records.iterator();
            if (meterRegistry != null) {
                long elapsed = System.currentTimeMillis() - start;
                Timer.builder("app.template.source.poll.duration")
                     .tag(TAG_SOURCE_TYPE, SOURCE_KAFKA)
                     .register(meterRegistry)
                     .record(elapsed, TimeUnit.MILLISECONDS);
                int count = records.count();
                if (count > 0) {
                    Counter.builder("app.template.source.received")
                           .tag(TAG_SOURCE_TYPE, SOURCE_KAFKA)
                           .register(meterRegistry)
                           .increment(count);
                }
            }
        } catch (WakeupException e) {
            closed = true;
            Thread.currentThread().interrupt();
            throw new InterruptedException("Kafka consumer woken up");
        } catch (Exception e) {
            if (meterRegistry != null) {
                Counter.builder("app.template.source.errors")
                       .tag(TAG_SOURCE_TYPE, SOURCE_KAFKA)
                       .tag("errorType", e.getClass().getSimpleName())
                       .register(meterRegistry)
                       .increment();
            }
            throw e;
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
