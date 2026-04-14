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
            FlowManager flowManager) {
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
        if (!perJob.getKeyedCache().isMustMatchRetryEnabled() || !joiner.needMatched()
                || reason == EgressReason.SHUTDOWN) {
            return false;
        }
        if (flowManager != null && flowManager.isStopped(jobId)) {
            return false;
        }
        return entry.tryConsumeOneRetry();
    }
}
