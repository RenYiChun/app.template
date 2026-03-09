package com.lrenyi.template.flow.internal;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.storage.FlowStorage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackpressureControllerTest {
    
    private static final String TEST_JOB = "test-job";
    
    private BackpressureController controller;
    
    @Test
    void awaitSpaceWhenStorageNotFullReturnsImmediately() throws InterruptedException, TimeoutException {
        MockFlowStorage storage = new MockFlowStorage(2, 1);
        TemplateConfigProperties.Flow flow = new TemplateConfigProperties.Flow();
        controller = new BackpressureController(storage,
                new SimpleMeterRegistry(),
                TEST_JOB,
                flow);
        
        controller.awaitSpace(() -> false);
        
        assertTrue(storage.size() < storage.maxCacheSize());
    }
    
    @Test
    void awaitSpaceWhenStopCheckReturnsTrueExitsImmediately() throws InterruptedException, TimeoutException {
        MockFlowStorage storage = new MockFlowStorage(2, 2);
        TemplateConfigProperties.Flow flow = new TemplateConfigProperties.Flow();
        controller = new BackpressureController(storage,
                new SimpleMeterRegistry(),
                TEST_JOB,
                flow);
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
    void awaitSpaceWhenFullBlocksUntilSignalRelease() throws InterruptedException {
        MockFlowStorage storage = new MockFlowStorage(2, 2);
        TemplateConfigProperties.Flow flow = new TemplateConfigProperties.Flow();
        controller = new BackpressureController(storage,
                new SimpleMeterRegistry(),
                TEST_JOB,
                flow);
        CountDownLatch producerBlocked = new CountDownLatch(1);
        CountDownLatch testDone = new CountDownLatch(1);
        
        Thread producer = new Thread(() -> {
            try {
                producerBlocked.countDown();
                controller.awaitSpace(() -> false);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (TimeoutException ignored) {
            } finally {
                testDone.countDown();
            }
        });
        producer.start();
        
        assertTrue(producerBlocked.await(1, TimeUnit.SECONDS), "Producer should start");
        
        storage.setSize(1);
        controller.signalRelease();
        
        assertTrue(testDone.await(3, TimeUnit.SECONDS), "Producer should unblock after signalRelease");
        producer.join(1000);
    }
    
    @Test
    void signalReleaseWakesOneWaiter() throws InterruptedException {
        MockFlowStorage storage = new MockFlowStorage(2, 2);
        TemplateConfigProperties.Flow flow = new TemplateConfigProperties.Flow();
        controller = new BackpressureController(storage,
                new SimpleMeterRegistry(),
                TEST_JOB,
                flow);
        AtomicBoolean released = new AtomicBoolean(false);
        
        CountDownLatch started = new CountDownLatch(1);
        Thread producer = new Thread(() -> {
            try {
                started.countDown();
                controller.awaitSpace(() -> false);
                released.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (TimeoutException ignored) {
            }
        });
        producer.start();
        assertTrue(started.await(1, TimeUnit.SECONDS));
        assertFalse(released.get(), "Producer should still be blocking");
        
        storage.setSize(1);
        controller.signalRelease();
        
        producer.join(3000);
        assertTrue(released.get(), "Producer should have been released");
    }
    
    @Test
    void awaitSpaceWhenMaxWaitExceededThrowsTimeoutException() {
        MockFlowStorage storage = new MockFlowStorage(2, 2);
        TemplateConfigProperties.Flow flow = new TemplateConfigProperties.Flow();
        controller = new BackpressureController(storage,
                new SimpleMeterRegistry(),
                TEST_JOB,
                flow);
        
        assertThrows(TimeoutException.class, () -> controller.awaitSpace(() -> false, 50));
    }
    
    private static class MockFlowStorage implements FlowStorage<Object> {
        private final long max;
        private volatile long currentSize;
        
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
            //ignore
        }
    }
}
