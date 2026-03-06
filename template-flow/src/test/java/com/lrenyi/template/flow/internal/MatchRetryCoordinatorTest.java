package com.lrenyi.template.flow.internal;

import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowSourceAdapters;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.model.EgressReason;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchRetryCoordinatorTest {
    
    @Test
    void initRetryRemainingWhenRetryable() {
        TemplateConfigProperties.Flow.PerJob perJob = new TemplateConfigProperties.Flow.PerJob();
        perJob.setMustMatchRetryEnabled(true);
        perJob.setMustMatchRetryMaxTimes(3);
        TestJoiner joiner = new TestJoiner(true, true);
        MatchRetryCoordinator<String> coordinator = new MatchRetryCoordinator<>("job-a",
                                                                                 perJob,
                                                                                 joiner,
                                                                                 null,
                                                                                 new SimpleMeterRegistry()
        );
        
        FlowEntry<String> entry = new FlowEntry<>("a", "job-a");
        coordinator.initRetryRemainingIfNecessary(entry);
        assertEquals(3, entry.getRetryRemaining());
    }
    
    @Test
    void tryConsumeRetryByReasonAndExhaust() {
        TemplateConfigProperties.Flow.PerJob perJob = new TemplateConfigProperties.Flow.PerJob();
        perJob.setMustMatchRetryEnabled(true);
        perJob.setMustMatchRetryMaxTimes(1);
        perJob.setMustMatchRetryOnEviction(true);
        perJob.setMustMatchRetryOnTimeout(false);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MatchRetryCoordinator<String> coordinator = new MatchRetryCoordinator<>("job-b",
                                                                                 perJob,
                                                                                 new TestJoiner(true, true),
                                                                                 null,
                                                                                 registry
        );
        
        FlowEntry<String> entry = new FlowEntry<>("b", "job-b");
        coordinator.initRetryRemainingIfNecessary(entry);
        assertFalse(coordinator.tryConsumeRetry(EgressReason.TIMEOUT, entry));
        assertTrue(coordinator.tryConsumeRetry(EgressReason.EVICTION, entry));
        assertFalse(coordinator.tryConsumeRetry(EgressReason.EVICTION, entry));
        
        double attempted = registry.get(FlowMetricNames.MATCH_RETRY_ATTEMPTED)
                                   .tag(FlowMetricNames.TAG_JOB_ID, "job-b")
                                   .tag(FlowMetricNames.TAG_REASON, EgressReason.EVICTION.name())
                                   .counter()
                                   .count();
        double exhausted = registry.get(FlowMetricNames.MATCH_RETRY_EXHAUSTED)
                                   .tag(FlowMetricNames.TAG_JOB_ID, "job-b")
                                   .tag(FlowMetricNames.TAG_REASON, EgressReason.EVICTION.name())
                                   .counter()
                                   .count();
        assertEquals(1.0, attempted);
        assertEquals(1.0, exhausted);
    }
    
    private static class TestJoiner implements FlowJoiner<String> {
        private final boolean needMatched;
        private final boolean retryable;
        
        private TestJoiner(boolean needMatched, boolean retryable) {
            this.needMatched = needMatched;
            this.retryable = retryable;
        }
        
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
            return needMatched;
        }
        
        @Override
        public boolean isRetryable(String item, String jobId) {
            return retryable;
        }
    }
}

