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
 * NatsFlowSource 单元测试
 */
@ExtendWith(MockitoExtension.class)
class NatsFlowSourceTest {

    private static final String CLOSED_MESSAGE = "closed";

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
    void constructorNullSubscriptionThrows() {
        Duration timeout = Duration.ofSeconds(1);
        assertThrows(IllegalArgumentException.class, () -> new NatsFlowSource<>(null, m -> "x", timeout));
    }
    
    @Test
    void constructorNullMapperThrows() {
        Duration timeout = Duration.ofSeconds(1);
        assertThrows(IllegalArgumentException.class, () -> new NatsFlowSource<>(subscription, null, timeout));
    }
    
    @Test
    void hasNextNextReturnsMappedValues() throws Exception {
        when(message.getData()).thenReturn("v1".getBytes(StandardCharsets.UTF_8));
        when(subscription.nextMessage(any(Duration.class))).thenReturn(message)
                                                           .thenThrow(new IllegalStateException(CLOSED_MESSAGE));
        
        source = new NatsFlowSource<>(subscription, mapper, Duration.ofSeconds(1));
        
        assertTrue(source.hasNext());
        assertEquals("v1", source.next());
        assertFalse(source.hasNext());
    }
    
    @Test
    void nextWithoutHasNextThrows() throws Exception {
        when(subscription.nextMessage(any(Duration.class))).thenThrow(new IllegalStateException(CLOSED_MESSAGE));
        
        source = new NatsFlowSource<>(subscription, mapper, Duration.ofSeconds(1));
        assertFalse(source.hasNext()); // 因异常而关闭
        assertThrows(NoSuchElementException.class, source::next);
    }
    
    @Test
    void closeIdempotent() {
        source = new NatsFlowSource<>(subscription, mapper, Duration.ofSeconds(1));
        source.close();
        source.close();
        verify(subscription).unsubscribe();
    }
    
    @Test
    void closeUnsubscribeThrowsDoesNotPropagate() {
        doThrow(new RuntimeException("unsubscribe failed")).when(subscription).unsubscribe();
        
        source = new NatsFlowSource<>(subscription, mapper, Duration.ofSeconds(1));
        assertDoesNotThrow(() -> source.close()); // 不抛异常
    }
    
    @Test
    void nextMessageTimeoutNullUsesDefault() throws Exception {
        when(subscription.nextMessage(any(Duration.class))).thenThrow(new IllegalStateException(CLOSED_MESSAGE));
        
        source = new NatsFlowSource<>(subscription, mapper, null);
        assertFalse(source.hasNext());
    }
}
