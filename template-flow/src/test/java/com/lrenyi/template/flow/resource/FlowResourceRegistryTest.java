package com.lrenyi.template.flow.resource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.TemplateConfigProperties.Flow.BackpressureBlockingMode;
import com.lrenyi.template.flow.PairItem;
import com.lrenyi.template.flow.QueueJoiner;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.backpressure.DimensionLease;
import com.lrenyi.template.flow.backpressure.dimension.ConsumerConcurrencyDimension;
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
    }

    @Test
    void getInstanceDifferentGlobalLimitRecreatesInstance() {
        TemplateConfigProperties.Flow config = new TemplateConfigProperties.Flow();
        config.getLimits().getGlobal().setConsumerThreads(8);
        config.getLimits().getGlobal().setProducerThreads(16);

        FlowResourceRegistry first = FlowResourceRegistry.getInstance(config, new SimpleMeterRegistry());

        TemplateConfigProperties.Flow changed = new TemplateConfigProperties.Flow();
        changed.getLimits().getGlobal().setConsumerThreads(8);
        changed.getLimits().getGlobal().setProducerThreads(32);

        FlowResourceRegistry second = FlowResourceRegistry.getInstance(changed, new SimpleMeterRegistry());

        assertNotNull(second);
        assertNotSame(first, second);
    }

    @Test
    void submitConsumerToGlobalAcquireFailureShouldNotLeakPendingCounterOrConsumerSlot() throws Exception {
        TemplateConfigProperties.Flow config = new TemplateConfigProperties.Flow();
        config.getLimits().getGlobal().setConsumerThreads(1);
        config.getLimits().getPerJob().setConsumerThreads(1);
        config.setConsumerAcquireBlockingMode(BackpressureBlockingMode.BLOCK_WITH_TIMEOUT);
        config.setConsumerAcquireTimeoutMill(500);

        FlowManager flowManager = FlowManager.getInstance(config, new SimpleMeterRegistry());
        FlowResourceRegistry registry = flowManager.getResourceRegistry();
        String jobId = "job-acquire-fail";
        QueueJoiner joiner = new QueueJoiner();
        NoopTracker tracker = new NoopTracker();
        FlowEgressHandler<PairItem> blockingHandler = new FlowEgressHandler<>(joiner, tracker,
                flowManager.getMeterRegistry());
        FlowFinalizer<PairItem> finalizer = new FlowFinalizer<>(registry,
                flowManager.getMeterRegistry(),
                blockingHandler,
                joiner);

        var launcher = flowManager.createLauncher(jobId, joiner, tracker, config);
        FlowEntry<PairItem> entry = new FlowEntry<>(new PairItem("1"), jobId);
        try (DimensionLease ignored = launcher.getBackpressureManager()
                .acquire(ConsumerConcurrencyDimension.ID, null, 1)) {
            finalizer.submitDataToConsumer(entry, launcher, null);
        }
        assertEquals(1, registry.getGlobalSemaphore().availablePermits(),
                "消费超时后，不应泄漏全局 consumerConcurrency 槽位");
    }

    @Test
    void submitConsumerFailureShouldReleaseInFlightConsumerSlot() throws Exception {
        TemplateConfigProperties.Flow config = new TemplateConfigProperties.Flow();
        config.getLimits().getGlobal().setConsumerThreads(1);
        config.getLimits().getPerJob().setConsumerThreads(1);
        config.setConsumerAcquireBlockingMode(BackpressureBlockingMode.BLOCK_WITH_TIMEOUT);
        config.setConsumerAcquireTimeoutMill(500);

        FlowManager flowManager = FlowManager.getInstance(config, new SimpleMeterRegistry());
        FlowResourceRegistry registry = flowManager.getResourceRegistry();
        String jobId = "job-slot-rollback";
        QueueJoiner joiner = new QueueJoiner();
        NoopTracker tracker = new NoopTracker();
        FlowEgressHandler<PairItem> blockingHandler = new FlowEgressHandler<>(joiner,
                                                                              tracker,
                                                                              flowManager.getMeterRegistry());
        FlowFinalizer<PairItem> finalizer = new FlowFinalizer<>(registry,
                                                                flowManager.getMeterRegistry(),
                                                                blockingHandler,
                                                                joiner);

        var launcher = flowManager.createLauncher(jobId, joiner, tracker, config);
        FlowEntry<PairItem> entry = new FlowEntry<>(new PairItem("2"), jobId);

        try (DimensionLease ignored = launcher.getBackpressureManager()
                .acquire(ConsumerConcurrencyDimension.ID, null, 1)) {
            assertEquals(0, registry.getGlobalSemaphore().availablePermits(),
                    "仅占用 consumerConcurrency 时，全局 consumer 槽位应被占满");
            finalizer.submitDataToConsumer(entry, launcher, null);
        }
        assertEquals(1, registry.getGlobalSemaphore().availablePermits(),
                "提交超时回滚后，全局 consumer 槽位必须完全释放");
    }

    @Test
    void trackerReleaseFailureShouldStillRollbackPendingCounterAndSlots() throws InterruptedException {
        TemplateConfigProperties.Flow config = new TemplateConfigProperties.Flow();
        config.getLimits().getGlobal().setConsumerThreads(1);
        config.getLimits().getPerJob().setConsumerThreads(1);

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
        assertEquals(1, registry.getGlobalSemaphore().availablePermits());
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

}
