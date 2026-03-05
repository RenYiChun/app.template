package com.lrenyi.template.flow.storage;

import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.internal.FlowFinalizer;
import com.lrenyi.template.flow.internal.FlowLauncher;
import com.lrenyi.template.flow.internal.MatchRetryCoordinator;
import com.lrenyi.template.flow.internal.RetryHandler;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.model.FailureReason;
import com.lrenyi.template.flow.resource.ActiveLauncherLookup;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.NonNull;

public abstract class AbstractEgressFlowStorage<T> implements RetryStorageAdapter<T> {
    private final FlowJoiner<T> joiner;
    private final FlowFinalizer<T> finalizer;
    private final ProgressTracker progressTracker;
    private final FlowResourceRegistry resourceRegistry;
    private final MeterRegistry meterRegistry;
    
    protected AbstractEgressFlowStorage(FlowJoiner<T> joiner,
            FlowFinalizer<T> finalizer,
            ProgressTracker progressTracker,
            MeterRegistry meterRegistry) {
        this.joiner = joiner;
        this.finalizer = finalizer;
        this.progressTracker = progressTracker;
        this.resourceRegistry = finalizer.resourceRegistry();
        this.meterRegistry = meterRegistry;
    }
    
    protected final void handleEgress(String key, FlowEntry<T> entry, FailureReason reason) {
        if (entry == null) {
            return;
        }
        if (reason == null) {
            entry.close();
            return;
        }
        ActiveLauncherLookup launcherLookup = resourceRegistry.getLauncherLookup();
        if (launcherLookup == null) {
            Counter.builder(FlowMetricNames.ERRORS)
                   .tag(FlowMetricNames.TAG_ERROR_TYPE, "flow_manager_unavailable")
                   .tag(FlowMetricNames.TAG_PHASE, "FINALIZATION")
                   .register(meterRegistry)
                   .increment();
            handlePassiveFailure(entry, reason);
            return;
        }
        FlowLauncher<Object> launcher = launcherLookup.getActiveLauncher(entry.getJobId());
        try {
            if (launcher == null) {
                Counter.builder(FlowMetricNames.ERRORS)
                       .tag(FlowMetricNames.TAG_ERROR_TYPE, "flow_launcher_unavailable")
                       .tag(FlowMetricNames.TAG_PHASE, "FINALIZATION")
                       .register(meterRegistry)
                       .increment();
                handlePassiveFailure(entry, reason);
                return;
            }
            if (entry.getRetryRemaining() == -1) {
                finalizer.submitBodyOnly(entry, launcher);
            } else {
                RetryHandler<T> retryHandler = getRetryHandler(entry, launcher);
                if (!retryHandler.tryHandleRetry(key, entry, reason, launcher)) {
                    finalizer.submitBodyOnly(entry, launcher);
                }
            }
        } finally {
            if (launcher != null) {
                launcher.getBackpressureController().signalRelease();
            }
        }
    }
    
    private @NonNull RetryHandler<T> getRetryHandler(FlowEntry<T> entry, FlowLauncher<Object> launcher) {
        TemplateConfigProperties.Flow.PerJob perJob = launcher.getFlow().getLimits().getPerJob();
        long backoffMill = launcher.getFlow().getLimits().getPerJob().getMustMatchRetryBackoffMill();
        MatchRetryCoordinator<T> coordinator =
                new MatchRetryCoordinator<>(entry.getJobId(), perJob, joiner, launcher.getFlowManager(), meterRegistry);
        return new RetryHandler<>(coordinator, this, backoffMill);
    }
    
    protected final FlowJoiner<T> joiner() {
        return joiner;
    }
    
    protected final ProgressTracker progressTracker() {
        return progressTracker;
    }
    
    protected final FlowResourceRegistry resourceRegistry() {
        return resourceRegistry;
    }
    
    protected final MeterRegistry meterRegistry() {
        return meterRegistry;
    }
}
