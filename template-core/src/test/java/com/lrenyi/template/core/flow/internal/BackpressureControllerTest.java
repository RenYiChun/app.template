package com.lrenyi.template.core.flow.internal;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import com.lrenyi.template.core.flow.context.FlowEntry;
import com.lrenyi.template.core.flow.metrics.FlowMetrics;
import com.lrenyi.template.core.flow.storage.FlowStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BackpressureController 单元测试
 */
class BackpressureControllerTest {

    private BackpressureController controller;

    @BeforeEach
    void setUp() {
        FlowMetricsCollectorGuard.resetIfNeeded();
    }

    @Test
    void awaitSpace_whenStorageNotFull_returnsImmediately() throws InterruptedException {
        MockFlowStorage storage = new MockFlowStorage(2, 1);
        controller = new BackpressureController(storage);

        controller.awaitSpace(() -> false);

        // 不阻塞即成功
        assertTrue(storage.size() < storage.maxCacheSize());
    }

    @Test
    void awaitSpace_whenStopCheckReturnsTrue_exitsImmediately() throws InterruptedException {
        MockFlowStorage storage = new MockFlowStorage(2, 2);
        controller = new BackpressureController(storage);
        AtomicBoolean stopCheckCalled = new AtomicBoolean(false);

        long start = System.currentTimeMillis();
        controller.awaitSpace(() -> {
            stopCheckCalled.set(true);
            return true;
        });
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(stopCheckCalled.get());
        assertTrue(elapsed < 500, "Should exit immediately when stopCheck returns true, took: " + elapsed + "ms");
    }

    @Test
    void awaitSpace_whenFull_blocksUntilSignalRelease() throws InterruptedException {
        MockFlowStorage storage = new MockFlowStorage(2, 2);
        controller = new BackpressureController(storage);
        CountDownLatch producerBlocked = new CountDownLatch(1);
        CountDownLatch testDone = new CountDownLatch(1);

        Thread producer = new Thread(() -> {
            try {
                producerBlocked.countDown();
                controller.awaitSpace(() -> false);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                testDone.countDown();
            }
        });
        producer.start();

        assertTrue(producerBlocked.await(1, TimeUnit.SECONDS), "Producer should start");
        Thread.sleep(100);

        storage.setSize(1);
        controller.signalRelease();

        assertTrue(testDone.await(3, TimeUnit.SECONDS), "Producer should unblock after signalRelease");
        producer.join(1000);
    }

    @Test
    void signalRelease_wakesOneWaiter() throws InterruptedException {
        MockFlowStorage storage = new MockFlowStorage(2, 2);
        controller = new BackpressureController(storage);
        AtomicBoolean released = new AtomicBoolean(false);

        Thread producer = new Thread(() -> {
            try {
                controller.awaitSpace(() -> false);
                released.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        producer.start();

        Thread.sleep(150);
        assertFalse(released.get(), "Producer should still be blocking");

        storage.setSize(1);
        controller.signalRelease();

        producer.join(3000);
        assertTrue(released.get(), "Producer should have been released");
    }

    /**
     * Mock FlowStorage with controllable size for testing
     */
    private static class MockFlowStorage implements FlowStorage<Object> {
        private volatile long currentSize;
        private final long max;

        MockFlowStorage(long max, long initialSize) {
            this.max = max;
            this.currentSize = initialSize;
        }

        void setSize(long size) {
            this.currentSize = size;
        }

        @Override
        public boolean doDeposit(FlowEntry<Object> ctx) {
            return false;
        }

        @Override
        public long size() {
            return currentSize;
        }

        @Override
        public long maxCacheSize() {
            return max;
        }

        @Override
        public void shutdown() {
        }
    }

    /**
     * Avoid polluting FlowMetrics in tests - reset before each test if needed
     */
    private static class FlowMetricsCollectorGuard {
        static void resetIfNeeded() {
            FlowMetrics.getCollector().reset();
        }
    }
}
