package com.lrenyi.template.flow.internal;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowSourceAdapters;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.context.FlowProgressSnapshot;
import com.lrenyi.template.flow.context.FlowResourceContext;
import com.lrenyi.template.flow.context.Registration;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        
        NoopProgressTracker tracker = new NoopProgressTracker();
        FlowResourceContext context = FlowResourceContext.builder()
                                                         .resourceRegistry(registry)
                                                         .flowManager(manager)
                                                         .pendingConsumerSlotSemaphore(pending)
                                                         .build();
        Registration registration = new Registration(jobId, flow);
        NoopJoiner<Object> joiner = new NoopJoiner<>();
        FlowLauncher<Object> launcher =
                FlowLauncher.create(jobId, joiner, manager, tracker, registration, context);
        
        FlowEgressHandler<Object> egressHandler = new FlowEgressHandler<>(joiner, tracker, meterRegistry);
        FlowFinalizer<Object> finalizer = new FlowFinalizer<>(registry, meterRegistry, egressHandler);
        FlowEntry<Object> entry = new FlowEntry<>(new Object(), jobId);
        
        double beforeTimeout = getCounter(FlowMetricNames.FINALIZER_PENDING_SLOT_ACQUIRE_TIMEOUT, jobId);
        double beforeSkipped = getCounter(FlowMetricNames.FINALIZER_SUBMIT_SKIPPED, jobId);
        
        finalizer.submitDataToConsumer(entry, launcher);
        
        double afterTimeout = getCounter(FlowMetricNames.FINALIZER_PENDING_SLOT_ACQUIRE_TIMEOUT, jobId);
        double afterSkipped = getCounter(FlowMetricNames.FINALIZER_SUBMIT_SKIPPED, jobId);
        assertEquals(beforeTimeout + 1D, afterTimeout);
        assertEquals(beforeSkipped + 1D, afterSkipped);
    }
    
    @Test
    void nonStrictModeStillSubmitsWhenPendingSlotTimeout() {
        String jobId = "job-nonstrict";
        TemplateConfigProperties.Flow flow = new TemplateConfigProperties.Flow();
        flow.getLimits().getPerJob().setStrictPendingConsumerSlot(false);
        
        FlowManager manager = FlowManager.getInstance(flow, meterRegistry);
        FlowResourceRegistry registry = manager.getResourceRegistry();
        TimeoutSemaphore pending = new TimeoutSemaphore();
        
        NoopProgressTracker tracker = new NoopProgressTracker();
        FlowResourceContext context = FlowResourceContext.builder()
                                                         .resourceRegistry(registry)
                                                         .flowManager(manager)
                                                         .pendingConsumerSlotSemaphore(pending)
                                                         .build();
        Registration registration = new Registration(jobId, flow);
        NoopJoiner<Object> joiner = new NoopJoiner<>();
        FlowLauncher<Object> launcher =
                FlowLauncher.create(jobId, joiner, manager, tracker, registration, context);
        
        FlowEgressHandler<Object> egressHandler = new FlowEgressHandler<>(joiner, tracker, meterRegistry);
        FlowFinalizer<Object> finalizer = new FlowFinalizer<>(registry, meterRegistry, egressHandler);
        FlowEntry<Object> entry = new FlowEntry<>(new Object(), jobId);
        
        double beforeTimeout = getCounter(FlowMetricNames.FINALIZER_PENDING_SLOT_ACQUIRE_TIMEOUT, jobId);
        double beforeSkipped = getCounter(FlowMetricNames.FINALIZER_SUBMIT_SKIPPED, jobId);
        
        finalizer.submitDataToConsumer(entry, launcher);
        
        double afterTimeout = getCounter(FlowMetricNames.FINALIZER_PENDING_SLOT_ACQUIRE_TIMEOUT, jobId);
        double afterSkipped = getCounter(FlowMetricNames.FINALIZER_SUBMIT_SKIPPED, jobId);
        assertEquals(beforeTimeout + 1D, afterTimeout);
        assertEquals(beforeSkipped, afterSkipped, "非严格模式下不应增加 submit_skipped 指标");
    }
    
    private double getCounter(String name, String jobId) {
        var counter = meterRegistry.find(name).tag(FlowMetricNames.TAG_JOB_ID, jobId).counter();
        return counter == null ? 0D : counter.count();
    }
    
    private static final class TimeoutSemaphore extends Semaphore {
        TimeoutSemaphore() {
            super(0);
        }
        
        @Override
        public boolean tryAcquire(long timeout, TimeUnit unit) {
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
        public void onActiveEgress() { }
        
        @Override
        public void onPassiveEgress(EgressReason reason) { }
        
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


