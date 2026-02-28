package com.lrenyi.template.flow.sources.nats;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.NoSuchElementException;
import io.nats.client.Message;
import io.nats.client.Subscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NatsFlowSource 单元测试
 */
@ExtendWith(MockitoExtension.class)
class NatsFlowSourceTest {
    
    @Mock
    private Subscription subscription;
    
    @Mock
    private Message message;
    
    private NatsFlowSource<String> source;
    private java.util.function.Function<Message, String> mapper;
    
    @BeforeEach
    void setUp() {
        mapper = msg -> new String(msg.getData(), StandardCharsets.UTF_8);
    }
    
    @Test
    void constructor_nullSubscription_throws() {
        assertThrows(IllegalArgumentException.class, () -> new NatsFlowSource<>(null, m -> "x", Duration.ofSeconds(1)));
    }
    
    @Test
    void constructor_nullMapper_throws() {
        assertThrows(IllegalArgumentException.class,
                     () -> new NatsFlowSource<>(subscription, null, Duration.ofSeconds(1))
        );
    }
    
    @Test
    void hasNext_next_returnsMappedValues() throws Exception {
        when(message.getData()).thenReturn("v1".getBytes(StandardCharsets.UTF_8));
        when(subscription.nextMessage(any(Duration.class))).thenReturn(message)
                                                           .thenThrow(new IllegalStateException("closed"));
        
        source = new NatsFlowSource<>(subscription, mapper, Duration.ofSeconds(1));
        
        assertTrue(source.hasNext());
        assertEquals("v1", source.next());
        assertFalse(source.hasNext());
    }
    
    @Test
    void next_withoutHasNext_throws() throws Exception {
        when(subscription.nextMessage(any(Duration.class))).thenThrow(new IllegalStateException("closed"));
        
        source = new NatsFlowSource<>(subscription, mapper, Duration.ofSeconds(1));
        assertFalse(source.hasNext()); // 因异常而关闭
        assertThrows(NoSuchElementException.class, source::next);
    }
    
    @Test
    void close_idempotent() {
        source = new NatsFlowSource<>(subscription, mapper, Duration.ofSeconds(1));
        source.close();
        source.close();
        verify(subscription).unsubscribe();
    }
    
    @Test
    void close_unsubscribeThrows_doesNotPropagate() {
        doThrow(new RuntimeException("unsubscribe failed")).when(subscription).unsubscribe();
        
        source = new NatsFlowSource<>(subscription, mapper, Duration.ofSeconds(1));
        source.close(); // 不抛异常
    }
    
    @Test
    void nextMessageTimeout_nullUsesDefault() throws Exception {
        when(subscription.nextMessage(any(Duration.class))).thenThrow(new IllegalStateException("closed"));
        
        source = new NatsFlowSource<>(subscription, mapper, null);
        assertFalse(source.hasNext());
    }
}
