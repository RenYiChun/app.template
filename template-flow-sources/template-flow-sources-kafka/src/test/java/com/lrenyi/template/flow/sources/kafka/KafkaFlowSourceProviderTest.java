package com.lrenyi.template.flow.sources.kafka;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import org.apache.kafka.clients.consumer.KafkaConsumer;
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
 * KafkaFlowSourceProvider 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class KafkaFlowSourceProviderTest {

    @Mock
    private KafkaConsumer<String, String> consumerA;

    @Mock
    private KafkaConsumer<String, String> consumerB;

    @Test
    void constructorRejectsEmptyConsumers() {
        assertThrows(IllegalArgumentException.class,
                     () -> new KafkaFlowSourceProvider<String>(List.<KafkaConsumer<?, ?>>of(),
                                                                 rec -> String.valueOf(rec.value()),
                                                                 Duration.ofSeconds(1)));
    }

    @Test
    void constructorRejectsNullMapper() {
        assertThrows(IllegalArgumentException.class,
                     () -> new KafkaFlowSourceProvider<String>(List.of(consumerA), null, Duration.ofSeconds(1)));
    }

    @Test
    void providerReturnsSubSourcesInOrder() throws Exception {
        KafkaFlowSourceProvider<String> provider =
                new KafkaFlowSourceProvider<>(List.of(consumerA, consumerB), rec -> String.valueOf(rec.value()),
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
        KafkaFlowSourceProvider<String> provider =
                new KafkaFlowSourceProvider<>(List.of(consumerA, consumerB), rec -> String.valueOf(rec.value()),
                                              Duration.ofMillis(100));

        assertDoesNotThrow(provider::close);
        assertDoesNotThrow(provider::close);

        verify(consumerA).close();
        verify(consumerB).close();
    }

    @Test
    void constructorNullConsumerListFailsFast() {
        assertThrows(IllegalArgumentException.class,
                     () -> new KafkaFlowSourceProvider<String>(null, rec -> String.valueOf(rec.value()),
                                                                Duration.ofSeconds(1)));
    }
}
