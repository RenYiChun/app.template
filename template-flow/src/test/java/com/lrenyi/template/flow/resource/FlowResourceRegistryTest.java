package com.lrenyi.template.flow.resource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.TemplateConfigProperties.Flow.BackpressureBlockingMode;
import com.lrenyi.template.flow.PairItem;
import com.lrenyi.template.flow.QueueJoiner;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.context.FlowProgressSnapshot;
import com.lrenyi.template.flow.internal.FlowEgressHandler;
import com.lrenyi.template.flow.internal.FlowFinalizer;
import com.lrenyi.template.flow.manager.FlowManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowResourceRegistryTest {

    @AfterEach
    void tearDown() {
        FlowManager.reset();
        FlowResourceRegistry.reset();
    }

    @Test
    void testConstructorCreatesInitializedInstance() {
        TemplateConfigProperties.Flow config = new TemplateConfigProperties.Flow();
        config.getLimits().getGlobal().setConsumerThreads(16);

        FlowResourceRegistry registry = new FlowResourceRegistry(config, new SimpleMeterRegistry(), true);

        assertTrue(registry.isInitialized());
        assertNotNull(registry.getGlobalSemaphore());
        assertNotNull(registry.getFlowConsumerExecutor());
        assertNotNull(registry.getStorageEgressExecutor());
        assertNotNull(registry.getCacheRemovalExecutor());
        assertNotNull(registry.getFairLock());
        assertNotNull(registry.getPermitReleased());
        assertNotNull(registry.getFlowCacheManager());

        try {
            registry.shutdown();
        } catch (ResourceShutdownException e) {
            // ignore
        }
        assertTrue(registry.isShutdown());
    }

    @Test
    void testConstructorShutdownStopsExecutors() throws ResourceShutdownException {
        TemplateConfigProperties.Flow config = new TemplateConfigProperties.Flow();
        config.getLimits().getGlobal().setConsumerThreads(8);

        FlowResourceRegistry registry = new FlowResourceRegistry(config, new SimpleMeterRegistry(), false);
        assertFalse(registry.isShutdown());

        registry.shutdown();
        assertTrue(registry.isShutdown());
        assertTrue(registry.getFlowConsumerExecutor().isShutdown());
        assertTrue(registry.getFlowProducerExecutor().isShutdown());
    }

    @Test
    void getInstanceDifferentGlobalLimitRecreatesInstance() {
        TemplateConfigProperties.Flow config = new TemplateConfigProperties.Flow();
        config.getLimits().getGlobal().setConsumerThreads(8);
        config.getLimits().getGlobal().setInFlightProduction(16);

        FlowResourceRegistry first = FlowResourceRegistry.getInstance(config, new SimpleMeterRegistry());

        TemplateConfigProperties.Flow changed = new TemplateConfigProperties.Flow();
        changed.getLimits().getGlobal().setConsumerThreads(8);
        changed.getLimits().getGlobal().setInFlightProduction(32);

        FlowResourceRegistry second = FlowResourceRegistry.getInstance(changed, new SimpleMeterRegistry());

        assertNotNull(second);
        assertNotSame(first, second);
    }

    @Test
    void submitConsumerToGlobalAcquireFailureShouldRollbackPendingCounter() throws InterruptedException {
        TemplateConfigProperties.Flow config = new TemplateConfigProperties.Flow();
        config.getLimits().getGlobal().setConsumerThreads(1);
        config.getLimits().getGlobal().setInFlightConsumer(4);
        config.getLimits().getPerJob().setConsumerThreads(1);
        config.getLimits().getPerJob().setInFlightConsumer(4);
        config.setConsumerAcquireBlockingMode(BackpressureBlockingMode.BLOCK_WITH_TIMEOUT);
        config.setConsumerAcquireTimeoutMill(500);

        FlowManager flowManager = FlowManager.getInstance(config, new SimpleMeterRegistry());
        FlowResourceRegistry registry = flowManager.getResourceRegistry();
        String jobId = "job-acquire-fail";
        QueueJoiner joiner = new QueueJoiner();
        CountDownLatch acquiredLatch = new CountDownLatch(1);
        BlockingTracker blockingTracker = new BlockingTracker(acquiredLatch);
        FlowEgressHandler<PairItem> blockingHandler = new FlowEgressHandler<>(joiner, blockingTracker,
                flowManager.getMeterRegistry());
        FlowFinalizer<PairItem> finalizer = new FlowFinalizer<>(registry,
                flowManager.getMeterRegistry(),
                blockingHandler,
                joiner);

        var launcher = flowManager.createLauncher(jobId, joiner, blockingTracker, config);
        FlowEntry<PairItem> entry1 = new FlowEntry<>(new PairItem("1"), jobId);
        FlowEntry<PairItem> entry2 = new FlowEntry<>(new PairItem("2"), jobId);

        finalizer.submitDataToConsumer(entry1, launcher, null);
        assertTrue(acquiredLatch.await(2, TimeUnit.SECONDS), "First task should acquire and block");
        Thread.sleep(300);

        assertThrows(RuntimeException.class, () -> finalizer.submitDataToConsumer(entry2, launcher, null));
        assertEquals(1L, registry.getGlobalPendingConsumerAdder().sum(),
                "Second's increment was rolled back; first task still holds 1 until it completes");
    }

    @Test
    void submitConsumerFailureShouldReleaseInFlightConsumerSlot() throws InterruptedException {
        TemplateConfigProperties.Flow config = new TemplateConfigProperties.Flow();
        config.getLimits().getGlobal().setConsumerThreads(1);
        config.getLimits().getGlobal().setInFlightConsumer(4);
        config.getLimits().getPerJob().setConsumerThreads(1);
        config.getLimits().getPerJob().setInFlightConsumer(4);
        config.setConsumerAcquireBlockingMode(BackpressureBlockingMode.BLOCK_WITH_TIMEOUT);
        config.setConsumerAcquireTimeoutMill(500);

        FlowManager flowManager = FlowManager.getInstance(config, new SimpleMeterRegistry());
        FlowResourceRegistry registry = flowManager.getResourceRegistry();
        String jobId = "job-slot-rollback";
        QueueJoiner joiner = new QueueJoiner();
        CountDownLatch startedLatch = new CountDownLatch(1);
        CountDownLatch releaseLatch = new CountDownLatch(1);
        BlockingJoiner blockingJoiner = new BlockingJoiner(startedLatch, releaseLatch);
        NoopTracker tracker = new NoopTracker();
        FlowEgressHandler<PairItem> blockingHandler = new FlowEgressHandler<>(blockingJoiner,
                                                                              tracker,
                                                                              flowManager.getMeterRegistry());
        FlowFinalizer<PairItem> finalizer = new FlowFinalizer<>(registry,
                                                                flowManager.getMeterRegistry(),
                                                                blockingHandler,
                                                                blockingJoiner);

        var launcher = flowManager.createLauncher(jobId, blockingJoiner, tracker, config);
        FlowEntry<PairItem> entry1 = new FlowEntry<>(new PairItem("1"), jobId);
        FlowEntry<PairItem> entry2 = new FlowEntry<>(new PairItem("2"), jobId);

        finalizer.submitDataToConsumer(entry1, launcher, null);
        assertTrue(startedLatch.await(2, TimeUnit.SECONDS), "First task should hold the consumer slot");
        assertEquals(3, registry.getGlobalInFlightConsumerSemaphore().availablePermits(),
                     "First task should occupy exactly one in-flight-consumer slot");

        assertThrows(RuntimeException.class, () -> finalizer.submitDataToConsumer(entry2, launcher, null));
        assertEquals(3, registry.getGlobalInFlightConsumerSemaphore().availablePermits(),
                     "Failed second submission must roll back the extra in-flight-consumer slot");

        releaseLatch.countDown();
    }

    @Test
    void trackerReleaseFailureShouldStillRollbackPendingCounterAndSlots() throws InterruptedException {
        TemplateConfigProperties.Flow config = new TemplateConfigProperties.Flow();
        config.getLimits().getGlobal().setConsumerThreads(1);
        config.getLimits().getGlobal().setInFlightConsumer(4);
        config.getLimits().getPerJob().setConsumerThreads(1);
        config.getLimits().getPerJob().setInFlightConsumer(4);

        FlowManager flowManager = FlowManager.getInstance(config, new SimpleMeterRegistry());
        FlowResourceRegistry registry = flowManager.getResourceRegistry();
        String jobId = "job-release-fail";
        QueueJoiner joiner = new QueueJoiner();
        CountDownLatch releasedLatch = new CountDownLatch(1);
        ThrowingReleaseTracker tracker = new ThrowingReleaseTracker(releasedLatch);
        FlowEgressHandler<PairItem> handler = new FlowEgressHandler<>(joiner, tracker, flowManager.getMeterRegistry());
        FlowFinalizer<PairItem> finalizer = new FlowFinalizer<>(registry,
                                                                flowManager.getMeterRegistry(),
                                                                handler,
                                                                joiner);

        var launcher = flowManager.createLauncher(jobId, joiner, tracker, config);
        finalizer.submitDataToConsumer(new FlowEntry<>(new PairItem("1"), jobId), launcher, null);

        assertTrue(releasedLatch.await(2, TimeUnit.SECONDS), "Tracker release callback should be invoked");
        Thread.sleep(200);
        assertEquals(0L, registry.getGlobalPendingConsumerAdder().sum());
        assertEquals(4, registry.getGlobalInFlightConsumerSemaphore().availablePermits());
    }

    private static final class BlockingTracker implements ProgressTracker {
        private final CountDownLatch acquiredLatch;
        private final CountDownLatch blockLatch = new CountDownLatch(1);

        BlockingTracker(CountDownLatch acquiredLatch) {
            this.acquiredLatch = acquiredLatch;
        }

        @Override
        public void onConsumerAcquired() {
        }

        @Override
        public void onConsumerReleased(String jobId) {
            acquiredLatch.countDown();
            try {
                blockLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onTerminated(int count) {
        }

        @Override
        public void onProductionAcquired() {
        }

        @Override
        public void onProductionReleased() {
        }

        @Override
        public FlowProgressSnapshot getSnapshot() {
            return FlowProgressSnapshot.builder().build();
        }

        @Override
        public void setTotalExpected(String jobId, long total) {
        }

        @Override
        public void markSourceFinished(String jobId, boolean status) {
        }

        @Override
        public boolean isCompleted(boolean status) {
            return false;
        }

        @Override
        public boolean isCompletionConditionMet() {
            return false;
        }
    }

    private static class NoopTracker implements ProgressTracker {
        @Override
        public void onProductionAcquired() {
        }

        @Override
        public void onProductionReleased() {
        }

        @Override
        public void onConsumerAcquired() {
        }

        @Override
        public void onConsumerReleased(String jobId) {
        }

        @Override
        public void onTerminated(int count) {
        }

        @Override
        public FlowProgressSnapshot getSnapshot() {
            return FlowProgressSnapshot.builder().build();
        }

        @Override
        public void setTotalExpected(String jobId, long total) {
        }

        @Override
        public void markSourceFinished(String jobId, boolean status) {
        }

        @Override
        public boolean isCompleted(boolean status) {
            return true;
        }

        @Override
        public boolean isCompletionConditionMet() {
            return true;
        }
    }

    private static final class ThrowingReleaseTracker extends NoopTracker {
        private final CountDownLatch releasedLatch;

        private ThrowingReleaseTracker(CountDownLatch releasedLatch) {
            this.releasedLatch = releasedLatch;
        }

        @Override
        public void onConsumerReleased(String jobId) {
            releasedLatch.countDown();
            throw new IllegalStateException("tracker failed");
        }
    }

    private static final class BlockingJoiner extends QueueJoiner {
        private final CountDownLatch startedLatch;
        private final CountDownLatch releaseLatch;

        private BlockingJoiner(CountDownLatch startedLatch, CountDownLatch releaseLatch) {
            this.startedLatch = startedLatch;
            this.releaseLatch = releaseLatch;
        }

        @Override
        public void onSingleConsumed(PairItem item, String jobId, com.lrenyi.template.flow.model.EgressReason reason) {
            startedLatch.countDown();
            try {
                releaseLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }
}
