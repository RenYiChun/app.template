package com.lrenyi.template.flow.internal;

import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowSourceAdapters;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.model.PreRetryResult;
import com.lrenyi.template.flow.storage.RetryStorageAdapter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryHandlerTest {
    
    @Test
    void shouldReturnFalseWhenRetryNotConsumed() {
        FlowEntry<String> entry = new FlowEntry<>("a", "job-a");
        MatchRetryCoordinator<String> coordinator = createCoordinator("job-a", 0);
        coordinator.initRetryRemainingIfNecessary(entry);
        TestAdapter adapter = new TestAdapter();
        RetryHandler<String> handler = new RetryHandler<>(coordinator, adapter, 0);
        
        boolean handled = handler.tryHandleRetry("k", entry, EgressReason.TIMEOUT, null);
        
        assertFalse(handled);
        assertEquals(0, adapter.preRetryCalls);
    }
    
    @Test
    void shouldHandleByPreRetry() {
        FlowEntry<String> entry = new FlowEntry<>("a", "job-a");
        MatchRetryCoordinator<String> coordinator = createCoordinator("job-a", 1);
        coordinator.initRetryRemainingIfNecessary(entry);
        TestAdapter adapter = new TestAdapter();
        adapter.preRetryResult = PreRetryResult.HANDLED;
        RetryHandler<String> handler = new RetryHandler<>(coordinator, adapter, 0);
        
        boolean handled = handler.tryHandleRetry("k", entry, EgressReason.TIMEOUT, null);
        
        assertTrue(handled);
        assertEquals(1, adapter.preRetryCalls);
        assertEquals(0, adapter.tryRequeueCalls);
        assertEquals(0, adapter.handlePassiveFailureCalls);
    }
    
    @Test
    void shouldHandleByRequeue() {
        FlowEntry<String> entry = new FlowEntry<>("a", "job-a");
        MatchRetryCoordinator<String> coordinator = createCoordinator("job-a", 1);
        coordinator.initRetryRemainingIfNecessary(entry);
        TestAdapter adapter = new TestAdapter();
        adapter.preRetryResult = PreRetryResult.PROCEED_TO_REQUEUE;
        adapter.tryRequeueReturn = true;
        RetryHandler<String> handler = new RetryHandler<>(coordinator, adapter, 0);
        
        boolean handled = handler.tryHandleRetry("k", entry, EgressReason.TIMEOUT, null);
        
        assertTrue(handled);
        assertEquals(1, adapter.tryRequeueCalls);
        assertEquals(0, adapter.handlePassiveFailureCalls);
    }
    
    @Test
    void shouldFallbackToPassiveFailureWhenRequeueFailed() {
        FlowEntry<String> entry = new FlowEntry<>("a", "job-a");
        MatchRetryCoordinator<String> coordinator = createCoordinator("job-a", 1);
        coordinator.initRetryRemainingIfNecessary(entry);
        TestAdapter adapter = new TestAdapter();
        adapter.preRetryResult = PreRetryResult.PROCEED_TO_REQUEUE;
        adapter.tryRequeueReturn = false;
        RetryHandler<String> handler = new RetryHandler<>(coordinator, adapter, 0);
        
        boolean handled = handler.tryHandleRetry("k", entry, EgressReason.TIMEOUT, null);
        
        assertTrue(handled);
        assertEquals(1, adapter.tryRequeueCalls);
        assertEquals(1, adapter.handlePassiveFailureCalls);
        assertEquals(EgressReason.TIMEOUT, adapter.lastReason);
    }
    
    private MatchRetryCoordinator<String> createCoordinator(String jobId, int maxRetryTimes) {
        TemplateConfigProperties.Flow.PerJob perJob = new TemplateConfigProperties.Flow.PerJob();
        perJob.setMustMatchRetryEnabled(true);
        perJob.setMustMatchRetryMaxTimes(maxRetryTimes);
        perJob.setMustMatchRetryOnTimeout(true);
        return new MatchRetryCoordinator<>(jobId, perJob, new TestJoiner(), null, new SimpleMeterRegistry());
    }
    
    private static final class TestAdapter implements RetryStorageAdapter<String> {
        private int preRetryCalls;
        private int tryRequeueCalls;
        private int handlePassiveFailureCalls;
        private PreRetryResult preRetryResult = PreRetryResult.PROCEED_TO_REQUEUE;
        private boolean tryRequeueReturn;
        private EgressReason lastReason;
        
        @Override
        public PreRetryResult preRetry(String key, FlowEntry<String> entry, FlowLauncher<Object> launcher) {
            preRetryCalls++;
            return preRetryResult;
        }
        
        @Override
        public boolean tryRequeue(FlowEntry<String> entry) {
            tryRequeueCalls++;
            return tryRequeueReturn;
        }
        
        @Override
        public void handlePassiveFailure(FlowEntry<String> entry, EgressReason reason) {
            handlePassiveFailureCalls++;
            lastReason = reason;
        }
    }
    
    private static final class TestJoiner implements FlowJoiner<String> {
        @Override
        public Class<String> getDataType() {
            return String.class;
        }
        
        @Override
        public FlowSourceProvider<String> sourceProvider() {
            return FlowSourceAdapters.emptyProvider();
        }
        
        @Override
        public String joinKey(String item) {
            return item;
        }
        
        @Override
        public void onPairConsumed(String existing, String incoming, String jobId) {
        
        }
        
        @Override
        public void onSingleConsumed(String item, String jobId, EgressReason reason) {
        
        }
        
        @Override
        public boolean needMatched() {
            return true;
        }
        
        @Override
        public boolean isRetryable(String item, String jobId) {
            return true;
        }
    }
}
