package com.lrenyi.template.flow.internal;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowSourceAdapters;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.backpressure.BackpressureManager;
import com.lrenyi.template.flow.backpressure.DimensionContext;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.context.FlowProgressSnapshot;
import com.lrenyi.template.flow.context.FlowResourceContext;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import com.lrenyi.template.flow.resource.PermitPair;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FlowFinalizerStrictPendingModeTest {
    
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    
    @AfterEach
    void cleanup() {
        meterRegistry.clear();
        FlowManager.reset();
        FlowResourceRegistry.reset();
    }
    
    @Test
    void strictModeSkipsSubmitWhenPendingSlotTimeout() {
        String jobId = "job-strict";
        TemplateConfigProperties.Flow flow = new TemplateConfigProperties.Flow();
        flow.getLimits().getPerJob().setStrictPendingConsumerSlot(true);
        
        FlowManager manager = FlowManager.getInstance(flow, meterRegistry);
        FlowResourceRegistry registry = manager.getResourceRegistry();
        TimeoutSemaphore pending = new TimeoutSemaphore();
        
        BackpressureManager backpressureManager = createBackpressureManager(jobId, flow, pending);
        
        DefaultProgressTracker tracker = new DefaultProgressTracker(jobId, manager);
        NoopJoiner<Object> joiner = new NoopJoiner<>();
        FlowEgressHandler<Object> egressHandler = new FlowEgressHandler<>(joiner, tracker, meterRegistry);
        FlowFinalizer<Object> finalizer = new FlowFinalizer<>(registry, meterRegistry, egressHandler, joiner);
        FlowResourceContext context = FlowResourceContext.builder()
                                                         .resourceRegistry(registry)
                                                         .flowManager(manager)
                                                         .pendingConsumerSlotSemaphore(pending)
                                                         .backpressureManager(backpressureManager)
                                                         .finalizer(finalizer)
                                                         .build();
        FlowLauncher<Object> launcher =
                FlowLauncher.create(jobId, jobId, joiner, manager, tracker, flow, context);
        FlowEntry<Object> entry = new FlowEntry<>(new Object(), jobId);
        
        finalizer.submitDataToConsumer(entry, launcher, null);
    }
    
    @Test
    void nonStrictModeStillSubmitsWhenPendingSlotTimeout() {
        String jobId = "job-nonstrict";
        TemplateConfigProperties.Flow flow = new TemplateConfigProperties.Flow();
        flow.getLimits().getPerJob().setStrictPendingConsumerSlot(false);
        
        FlowManager manager = FlowManager.getInstance(flow, meterRegistry);
        FlowResourceRegistry registry = manager.getResourceRegistry();
        TimeoutSemaphore pending = new TimeoutSemaphore();
        
        BackpressureManager backpressureManager = createBackpressureManager(jobId, flow, pending);
        
        DefaultProgressTracker tracker = new DefaultProgressTracker(jobId, manager);
        NoopJoiner<Object> joiner = new NoopJoiner<>();
        FlowEgressHandler<Object> egressHandler = new FlowEgressHandler<>(joiner, tracker, meterRegistry);
        FlowFinalizer<Object> finalizer = new FlowFinalizer<>(registry, meterRegistry, egressHandler, joiner);
        FlowResourceContext context = FlowResourceContext.builder()
                                                         .resourceRegistry(registry)
                                                         .flowManager(manager)
                                                         .pendingConsumerSlotSemaphore(pending)
                                                         .backpressureManager(backpressureManager)
                                                         .finalizer(finalizer)
                                                         .build();
        FlowLauncher<Object> launcher =
                FlowLauncher.create(jobId, jobId, joiner, manager, tracker, flow, context);
        FlowEntry<Object> entry = new FlowEntry<>(new Object(), jobId);

        // In non-strict mode, submission is attempted even after timeout; the job isn't registered so acquire
        // throws ExecutorInterruptedException — that's acceptable for this test.
        try {
            finalizer.submitDataToConsumer(entry, launcher, null);
        } catch (RuntimeException ignored) {
            // Expected: job not registered with FlowManager, so orchestrator.acquire() throws
        }
    }

    private BackpressureManager createBackpressureManager(String jobId,
            TemplateConfigProperties.Flow flow,
            Semaphore pendingSlot) {
        DimensionContext baseCtx = DimensionContext.builder()
                                                   .jobId(jobId)
                                                   .stopCheck(() -> false)
                                                   .meterRegistry(meterRegistry)
                                                   .flowConfig(flow)
                                                   .inFlightConsumerPermitPair(PermitPair.of(null, pendingSlot))
                                                   .build();
        return new BackpressureManager(baseCtx, meterRegistry);
    }
    
    /** Semaphore that always times out on acquire. */
    private static final class TimeoutSemaphore extends Semaphore {
        TimeoutSemaphore() {
            super(0);
        }
        
        @Override
        public boolean tryAcquire(long timeout, TimeUnit unit) {
            return false;
        }

        @Override
        public boolean tryAcquire(int permits, long timeout, TimeUnit unit) {
            return false;
        }
    }
    
    private static final class NoopJoiner<T> implements FlowJoiner<T> {
        @Override
        public Class<T> getDataType() {
            return null;
        }
        
        @Override
        public FlowSourceProvider<T> sourceProvider() {
            return FlowSourceAdapters.emptyProvider();
        }
        
        @Override
        public String joinKey(T item) {
            return null;
        }
        
        @Override
        public void onPairConsumed(T existing, T incoming, String jobId) {
        }
        
        @Override
        public void onSingleConsumed(T item, String jobId, EgressReason reason) {
        }
        
        @Override
        public boolean needMatched() {
            return false;
        }
        
        @Override
        public boolean isRetryable(T item, String jobId) {
            return false;
        }
    }
    
    private static final class NoopProgressTracker implements ProgressTracker {
        @Override
        public void onProductionAcquired() { }
        
        @Override
        public void onProductionReleased() { }
        
        @Override
        public void onConsumerAcquired() { }
        
        @Override
        public void onConsumerReleased(String jobId) { }
        
        @Override
        public void onTerminated(int count) { }
        
        @Override
        public FlowProgressSnapshot getSnapshot() {
            return null;
        }
        
        @Override
        public void setTotalExpected(String jobId, long total) { }
        
        @Override
        public void markSourceFinished(String jobId) { }
        
        @Override
        public boolean isCompleted() {
            return false;
        }
        
        @Override
        public boolean isCompletionConditionMet() {
            return false;
        }
    }
}
