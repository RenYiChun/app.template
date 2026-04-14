package com.lrenyi.template.flow.internal;

import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowSourceAdapters;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.model.EgressReason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchRetryCoordinatorTest {

    @Test
    void initRetryRemainingWhenRetryable() {
        TemplateConfigProperties.Flow.PerJob perJob = new TemplateConfigProperties.Flow.PerJob();
        TemplateConfigProperties.Flow.KeyedCache cache = perJob.getKeyedCache();
        cache.setMustMatchRetryEnabled(true);
        cache.setMustMatchRetryMaxTimes(3);
        TestJoiner joiner = new TestJoiner(true, true);
        MatchRetryCoordinator<String> coordinator = new MatchRetryCoordinator<>("job-a", perJob, joiner, null);

        FlowEntry<String> entry = new FlowEntry<>("a", "job-a");
        coordinator.initRetryRemainingIfNecessary(entry);
        assertEquals(3, entry.getRetryRemaining());
    }

    @Test
    void tryConsumeRetryByReasonAndExhaust() {
        TemplateConfigProperties.Flow.PerJob perJob = new TemplateConfigProperties.Flow.PerJob();
        TemplateConfigProperties.Flow.KeyedCache cache = perJob.getKeyedCache();
        cache.setMustMatchRetryEnabled(true);
        cache.setMustMatchRetryMaxTimes(2);
        MatchRetryCoordinator<String> coordinator = new MatchRetryCoordinator<>("job-b",
                                                                                 perJob,
                                                                                new TestJoiner(true, true),
                                                                                null
        );

        FlowEntry<String> entry = new FlowEntry<>("b", "job-b");
        coordinator.initRetryRemainingIfNecessary(entry);
        assertTrue(coordinator.tryConsumeRetry(EgressReason.TIMEOUT, entry));
        assertTrue(coordinator.tryConsumeRetry(EgressReason.EVICTION, entry));
        assertFalse(coordinator.tryConsumeRetry(EgressReason.EVICTION, entry));
    }

    private record TestJoiner(boolean needMatched, boolean retryable) implements FlowJoiner<String> {

        @Override
        public boolean needMatched() {
            return needMatched;
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
        public boolean isRetryable(String item, String jobId) {
            return retryable;
        }
    }
}
