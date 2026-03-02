package com.lrenyi.template.flow.sources.kafka;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KafkaFlowSource 单元测试
 */
@ExtendWith(MockitoExtension.class)
class KafkaFlowSourceTest {
    
    @Mock
    private KafkaConsumer<String, String> consumer;
    
    private KafkaFlowSource<String> source;
    private java.util.function.Function<ConsumerRecord<?, ?>, String> mapper;
    
    @BeforeEach
    void setUp() {
        mapper = rec -> rec.value() != null ? rec.value().toString() : "";
    }
    
    @Test
    void hasNext_next_returnsMappedValues() throws Exception {
        ConsumerRecord<String, String> r1 = new ConsumerRecord<>("t", 0, 0L, "k1", "v1");
        ConsumerRecord<String, String> r2 = new ConsumerRecord<>("t", 0, 1L, "k2", "v2");
        ConsumerRecords<String, String> records =
                new ConsumerRecords<>(Collections.singletonMap(new TopicPartition("t", 0), List.of(r1, r2)));
        when(consumer.poll(any(Duration.class))).thenReturn(records)
                                                .thenReturn(new ConsumerRecords<>(Collections.emptyMap()));
        
        source = new KafkaFlowSource<>(consumer, mapper, Duration.ofMillis(100));
        
        assertTrue(source.hasNext());
        assertEquals("v1", source.next());
        assertTrue(source.hasNext());
        assertEquals("v2", source.next());
        assertFalse(source.hasNext());
        
        source.close();
        assertFalse(source.hasNext());
        assertThrows(NoSuchElementException.class, source::next);
    }
    
    @Test
    void next_withoutHasNext_throws() {
        source = new KafkaFlowSource<>(consumer, mapper, Duration.ofMillis(100));
        assertThrows(NoSuchElementException.class, source::next);
    }
    
    @Test
    void close_idempotent() {
        source = new KafkaFlowSource<>(consumer, mapper, Duration.ofMillis(100));
        source.close();
        source.close();
        verify(consumer).close();
    }
    
    @Test
    void hasNext_wakeupException_throwsInterruptedException() {
        when(consumer.poll(any(Duration.class))).thenThrow(new WakeupException());
        
        source = new KafkaFlowSource<>(consumer, mapper, Duration.ofMillis(100));
        assertThrows(InterruptedException.class, source::hasNext);
    }
    
    @Test
    void close_consumerThrows_doesNotPropagate() {
        doThrow(new RuntimeException("close failed")).when(consumer).close();
        
        source = new KafkaFlowSource<>(consumer, mapper, Duration.ofMillis(100));
        assertDoesNotThrow(() -> source.close()); // 不抛异常
    }
    
    @Test
    void pollTimeout_nullUsesDefault() throws Exception {
        source = new KafkaFlowSource<>(consumer, r -> r.value().toString(), null);
        when(consumer.poll(any(Duration.class))).thenReturn(new ConsumerRecords<>(Collections.emptyMap()));
        assertFalse(source.hasNext());
    }
}
