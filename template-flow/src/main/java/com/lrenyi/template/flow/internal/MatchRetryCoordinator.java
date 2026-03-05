package com.lrenyi.template.flow.internal;

import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.model.FailureReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

public class MatchRetryCoordinator<T> {
    private final String jobId;
    private final TemplateConfigProperties.Flow.PerJob perJob;
    private final FlowJoiner<T> joiner;
    private final FlowManager flowManager;
    private final MeterRegistry meterRegistry;
    
    public MatchRetryCoordinator(String jobId,
            TemplateConfigProperties.Flow.PerJob perJob,
            FlowJoiner<T> joiner,
            FlowManager flowManager,
            MeterRegistry meterRegistry) {
        this.jobId = jobId;
        this.perJob = perJob;
        this.joiner = joiner;
        this.flowManager = flowManager;
        this.meterRegistry = meterRegistry;
    }
    
    public void initRetryRemainingIfNecessary(FlowEntry<T> entry) {
        int retryRemaining = -1;
        if (perJob.isMustMatchRetryEnabled()
                && joiner.needMatched()
                && joiner.isRetryable(entry.getData(), jobId)) {
            retryRemaining = perJob.getMustMatchRetryMaxTimes();
        }
        entry.initRetryRemaining(retryRemaining);
    }
    
    public boolean tryConsumeRetry(FailureReason reason, FlowEntry<T> entry) {
        if (!perJob.isMustMatchRetryEnabled() || !joiner.needMatched()) {
            return false;
        }
        if (flowManager != null && flowManager.isStopped(jobId)) {
            return false;
        }
        if (!supportReason(reason)) {
            return false;
        }
        boolean consumed = entry.tryConsumeOneRetry();
        if (!consumed) {
            increment(FlowMetricNames.MATCH_RETRY_EXHAUSTED, reason);
            return false;
        }
        increment(FlowMetricNames.MATCH_RETRY_ATTEMPTED, reason);
        return true;
    }
    
    public void onRetrySucceeded(FailureReason reason) {
        increment(FlowMetricNames.MATCH_RETRY_SUCCEEDED, reason);
    }
    
    private boolean supportReason(FailureReason reason) {
        if (reason == FailureReason.TIMEOUT) {
            return perJob.isMustMatchRetryOnTimeout();
        }
        if (reason == FailureReason.EVICTION) {
            return perJob.isMustMatchRetryOnEviction();
        }
        return false;
    }
    
    private void increment(String metricName, FailureReason reason) {
        Counter.builder(metricName)
               .tag(FlowMetricNames.TAG_JOB_ID, jobId)
               .tag(FlowMetricNames.TAG_REASON, reason.name())
               .register(meterRegistry)
               .increment();
    }
}
