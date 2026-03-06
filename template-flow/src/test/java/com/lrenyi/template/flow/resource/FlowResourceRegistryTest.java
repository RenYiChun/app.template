package com.lrenyi.template.flow.resource;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import com.lrenyi.template.flow.QueueJoiner;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.context.FlowProgressSnapshot;
import com.lrenyi.template.flow.context.FlowResourceContext;
import com.lrenyi.template.flow.context.Orchestrator;
import com.lrenyi.template.flow.context.Registration;
import com.lrenyi.template.flow.executor.ExecutorInterruptedException;
import com.lrenyi.template.flow.internal.DefaultProgressTracker;
import com.lrenyi.template.flow.manager.FlowManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        config.getLimits().getGlobal().setConsumerConcurrency(16);
        
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
        config.getLimits().getGlobal().setConsumerConcurrency(8);
        
        FlowResourceRegistry registry = new FlowResourceRegistry(config, new SimpleMeterRegistry(), false);
        assertFalse(registry.isShutdown());
        
        registry.shutdown();
        assertTrue(registry.isShutdown());
        assertTrue(registry.getFlowConsumerExecutor().isShutdown());
    }
    
    @Test
    void submitConsumerToGlobalAcquireFailureShouldRollbackPendingCounter() {
        TemplateConfigProperties.Flow config = new TemplateConfigProperties.Flow();
        config.getLimits().getGlobal().setConsumerConcurrency(4);
        
        FlowResourceRegistry registry = new FlowResourceRegistry(config, new SimpleMeterRegistry(), false);
        Registration registration = new Registration("job-1", config);
        FlowResourceContext context = FlowResourceContext.builder()
                                                         .resourceRegistry(registry)
                                                         .flowManager(null)
                                                         .jobConsumerSemaphore(new java.util.concurrent.Semaphore(4))
                                                         .build();
        Orchestrator orchestrator = new Orchestrator("job-1", new NoopTracker(), registration, context);
        
        assertThrows(RuntimeException.class, () -> registry.submitConsumerToGlobal(orchestrator, 2, () -> {}));
        assertEquals(0L, registry.getGlobalPendingConsumerAdder().sum());
    }
    
    @Test
    void submitConsumerToGlobalSecondAcquireInterruptedShouldRollbackPartialAcquire() {
        TemplateConfigProperties.Flow config = new TemplateConfigProperties.Flow();
        config.getLimits().getGlobal().setConsumerConcurrency(2);
        config.getLimits().getPerJob().setConsumerConcurrency(2);
        
        FlowManager flowManager = FlowManager.getInstance(config, new SimpleMeterRegistry());
        String jobId = "job-partial-acquire";
        DefaultProgressTracker tracker = new DefaultProgressTracker(jobId, flowManager);
        QueueJoiner joiner = new QueueJoiner();
        var launcher = flowManager.createLauncher(jobId, joiner, tracker, config);
        Orchestrator orchestrator = launcher.getTaskOrchestrator();
        FlowResourceRegistry registry = flowManager.getResourceRegistry();
        
        Thread stopThread = new Thread(() -> {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (System.nanoTime() < deadline) {
                if (orchestrator.registration().getActiveCount().get() == 1) {
                    flowManager.unregister(jobId);
                    return;
                }
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            }
        });
        stopThread.start();
        
        try {
            registry.submitConsumerToGlobal(orchestrator, 2, () -> {
            });
        } catch (ExecutorInterruptedException ignored) {
        }
        Thread.interrupted();
        try {
            stopThread.join(TimeUnit.SECONDS.toMillis(1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertEquals(0L, registry.getGlobalPendingConsumerAdder().sum());
        assertEquals(0, orchestrator.registration().getActiveCount().get());
        assertEquals(2, registry.getGlobalSemaphore().availablePermits());
    }
    
    private static final class NoopTracker implements ProgressTracker {
        @Override
        public void onProductionAcquired() {
        }
        
        @Override
        public void onProductionReleased() {
        }
        
        @Override
        public void onConsumerBegin() {
        }
        
        @Override
        public void onActiveEgress() {
        }
        
        @Override
        public void onPassiveEgress() {
        }
        
        @Override
        public void onGlobalTerminated(String jobId) {
        }
        
        @Override
        public FlowProgressSnapshot getSnapshot() {
            return FlowProgressSnapshot.builder().build();
        }
        
        @Override
        public void setTotalExpected(String jobId, long total) {
        }
        
        @Override
        public void markSourceFinished(String jobId) {
        }
        
        @Override
        public boolean isCompleted() {
            return true;
        }
        
        @Override
        public boolean isCompletionConditionMet() {
            return true;
        }
    }
}
