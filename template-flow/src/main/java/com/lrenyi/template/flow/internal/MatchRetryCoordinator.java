package com.lrenyi.template.flow.internal;

import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.model.EgressReason;

public class MatchRetryCoordinator<T> {
    private final String jobId;
    private final TemplateConfigProperties.Flow.PerJob perJob;
    private final FlowJoiner<T> joiner;
    private final FlowManager flowManager;
    
    public MatchRetryCoordinator(String jobId,
            TemplateConfigProperties.Flow.PerJob perJob,
            FlowJoiner<T> joiner,
            FlowManager flowManager,
            io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        this.jobId = jobId;
        this.perJob = perJob;
        this.joiner = joiner;
        this.flowManager = flowManager;
    }
    
    public void initRetryRemainingIfNecessary(FlowEntry<T> entry) {
        int retryRemaining = -1;
        TemplateConfigProperties.Flow.KeyedCache cache = perJob.getKeyedCache();
        if (cache.isMustMatchRetryEnabled()
                && joiner.needMatched()
                && joiner.isRetryable(entry.getData(), jobId)) {
            retryRemaining = cache.getMustMatchRetryMaxTimes();
        }
        entry.initRetryRemaining(retryRemaining);
    }
    
    public boolean tryConsumeRetry(EgressReason reason, FlowEntry<T> entry) {
        if (!perJob.getKeyedCache().isMustMatchRetryEnabled() || !joiner.needMatched()) {
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
            return false;
        }
        return true;
    }
    
    public void onRetrySucceeded(EgressReason reason) {
    }
    
    private boolean supportReason(EgressReason reason) {
        if (reason == EgressReason.TIMEOUT) {
            return perJob.getKeyedCache().isMustMatchRetryOnTimeout();
        }
        if (reason == EgressReason.EVICTION) {
            return perJob.getKeyedCache().isMustMatchRetryOnEviction();
        }
        return false;
    }
}
