package com.lrenyi.template.flow.sources.nats;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import io.nats.client.Subscription;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

/**
 * NatsFlowSourceProvider 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class NatsFlowSourceProviderTest {

    @Mock
    private Subscription subscriptionA;

    @Mock
    private Subscription subscriptionB;

    @Test
    void constructorRejectsEmptySubscriptions() {
        assertThrows(IllegalArgumentException.class,
                     () -> new NatsFlowSourceProvider<String>(List.<Subscription>of(), msg -> "ok",
                                                              Duration.ofSeconds(1)));
    }

    @Test
    void constructorRejectsNullMapper() {
        assertThrows(IllegalArgumentException.class,
                     () -> new NatsFlowSourceProvider<String>(List.of(subscriptionA), null, Duration.ofSeconds(1)));
    }

    @Test
    void providerReturnsSubSourcesInOrder() throws Exception {
        NatsFlowSourceProvider<String> provider =
                new NatsFlowSourceProvider<>(List.of(subscriptionA, subscriptionB), msg -> "ok",
                                             Duration.ofMillis(100));

        assertTrue(provider.hasNextSubSource());
        assertNotNull(provider.nextSubSource());
        assertTrue(provider.hasNextSubSource());
        assertNotNull(provider.nextSubSource());
        assertFalse(provider.hasNextSubSource());
        assertThrows(NoSuchElementException.class, provider::nextSubSource);
    }

    @Test
    void closeIsBestEffortAndIdempotent() {
        NatsFlowSourceProvider<String> provider =
                new NatsFlowSourceProvider<>(List.of(subscriptionA, subscriptionB), msg -> "ok",
                                             Duration.ofMillis(100));

        assertDoesNotThrow(provider::close);
        assertDoesNotThrow(provider::close);

        verify(subscriptionA).unsubscribe();
        verify(subscriptionB).unsubscribe();
    }

    @Test
    void constructorNullSubscriptionListFailsFast() {
        assertThrows(IllegalArgumentException.class,
                     () -> new NatsFlowSourceProvider<String>(null, msg -> "ok", Duration.ofSeconds(1)));
    }
}
