package com.lrenyi.template.flow.storage;

import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.internal.FlowEgressHandler;
import com.lrenyi.template.flow.internal.FlowFinalizer;
import com.lrenyi.template.flow.internal.FlowLauncher;
import com.lrenyi.template.flow.internal.MatchRetryCoordinator;
import com.lrenyi.template.flow.internal.RetryHandler;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.model.EgressReason;
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
    private final FlowEgressHandler<T> egressHandler;

    protected AbstractEgressFlowStorage(FlowJoiner<T> joiner,
            FlowFinalizer<T> finalizer,
            ProgressTracker progressTracker,
            MeterRegistry meterRegistry,
            FlowEgressHandler<T> egressHandler) {
        this.joiner = joiner;
        this.finalizer = finalizer;
        this.progressTracker = progressTracker;
        this.resourceRegistry = finalizer.resourceRegistry();
        this.meterRegistry = meterRegistry;
        this.egressHandler = egressHandler;
    }
    
    protected final void handleEgress(String key, FlowEntry<T> entry, EgressReason reason, boolean skipRetry) {
        if (entry == null) {
            return;
        }
        if (reason == null) {
            entry.close();
            return;
        }
        if (skipRetry) {
            handlePassiveFailure(entry, reason);
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
            finalizer.submitDataToConsumer(entry, launcher, reason);
        } else {
            RetryHandler<T> retryHandler = getRetryHandler(entry, launcher);
            if (!retryHandler.tryHandleRetry(key, entry, reason, launcher)) {
                finalizer.submitDataToConsumer(entry, launcher, reason);
            }
        }
    }
    
    @Override
    public void handlePassiveFailure(FlowEntry<T> entry, EgressReason reason) {
        ActiveLauncherLookup launcherLookup = resourceRegistry.getLauncherLookup();
        FlowLauncher<Object> launcher = null;
        if (launcherLookup != null && entry != null) {
            launcher = launcherLookup.getActiveLauncher(entry.getJobId());
        }
        if (launcher != null) {
            finalizer.submitDataToConsumer(entry, launcher, reason);
        } else {
            try (entry) {
                egressHandler.performSingleConsumed(entry, reason);
            }
            progressTracker.onTerminated(1);
        }
    }
    
    protected final FlowEgressHandler<T> egressHandler() {
        return egressHandler;
    }

    protected final FlowFinalizer<T> finalizer() {
        return finalizer;
    }
    
    private @NonNull RetryHandler<T> getRetryHandler(FlowEntry<T> entry, FlowLauncher<Object> launcher) {
        TemplateConfigProperties.Flow.PerJob perJob = launcher.getFlow().getLimits().getPerJob();
        long backoffMill = launcher.getFlow().getLimits().getPerJob().getKeyedCache().getMustMatchRetryBackoffMill();
        MatchRetryCoordinator<T> coordinator =
                new MatchRetryCoordinator<>(entry.getJobId(), perJob, joiner, launcher.getFlowManager());
        return new RetryHandler<>(coordinator, this, backoffMill);
    }
    
    protected final FlowJoiner<T> joiner() {
        return joiner;
    }
    
    protected final FlowResourceRegistry resourceRegistry() {
        return resourceRegistry;
    }
    
    protected final MeterRegistry meterRegistry() {
        return meterRegistry;
    }
}
