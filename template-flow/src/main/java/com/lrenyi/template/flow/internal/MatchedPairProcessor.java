package com.lrenyi.template.flow.internal;

import java.util.concurrent.TimeUnit;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.context.Orchestrator;
import com.lrenyi.template.flow.exception.FlowExceptionHelper;
import com.lrenyi.template.flow.exception.FlowPhase;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.model.FailureReason;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

public class MatchedPairProcessor<T> {
    private final FlowJoiner<T> joiner;
    private final ProgressTracker progressTracker;
    private final MeterRegistry meterRegistry;
    private final FlowResourceRegistry resourceRegistry;
    
    public MatchedPairProcessor(FlowJoiner<T> joiner,
            ProgressTracker progressTracker,
            MeterRegistry meterRegistry,
            FlowResourceRegistry resourceRegistry) {
        this.joiner = joiner;
        this.progressTracker = progressTracker;
        this.meterRegistry = meterRegistry;
        this.resourceRegistry = resourceRegistry;
    }
    
    public void processMatchedPair(FlowEntry<T> partner, FlowEntry<T> entry, FlowLauncher<Object> launcher) {
        resourceRegistry.releaseGlobalStorage(1);
        long matchStartTime = System.currentTimeMillis();
        Orchestrator taskOrchestrator = launcher.getTaskOrchestrator();
        Runnable runnable = () -> {
            try (partner; entry) {
                executeMatchedPairLogicBody(partner, entry);
                long matchLatency = System.currentTimeMillis() - matchStartTime;
                Timer.builder(FlowMetricNames.MATCH_DURATION)
                     .tag(FlowMetricNames.TAG_JOB_ID, entry.getJobId())
                     .register(meterRegistry)
                     .record(matchLatency, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                FlowExceptionHelper.handleException(entry.getJobId(), null, e, FlowPhase.CONSUMPTION, "match_process_failed");
                Counter.builder(FlowMetricNames.ERRORS)
                       .tag(FlowMetricNames.TAG_ERROR_TYPE, "match_process_failed")
                       .tag(FlowMetricNames.TAG_PHASE, "CONSUMPTION")
                       .register(meterRegistry)
                       .increment();
            } finally {
                launcher.getBackpressureController().signalRelease();
            }
        };
        resourceRegistry.submitConsumerToGlobal(taskOrchestrator, 2, runnable);
    }
    
    private void executeMatchedPairLogicBody(FlowEntry<T> partner, FlowEntry<T> entry) {
        if (joiner.isMatched(partner.getData(), entry.getData())) {
            handleMatchedSuccess(partner, entry);
            return;
        }
        handleMatchedFailure(partner, entry);
    }
    
    private void handleMatchedSuccess(FlowEntry<T> partner, FlowEntry<T> entry) {
        progressTracker.onActiveEgress();
        progressTracker.onActiveEgress();
        Counter.builder(FlowMetricNames.EGRESS_ACTIVE)
               .tag(FlowMetricNames.TAG_JOB_ID, entry.getJobId())
               .register(meterRegistry)
               .increment(2);
        try {
            joiner.onSuccess(partner.getData(), entry.getData(), entry.getJobId());
        } catch (Exception e) {
            FlowExceptionHelper.handleException(entry.getJobId(), null, e, FlowPhase.CONSUMPTION, "onSuccess_failed");
        }
    }
    
    private void handleMatchedFailure(FlowEntry<T> partner, FlowEntry<T> entry) {
        try {
            joiner.onFailed(partner.getData(), partner.getJobId(), FailureReason.MISMATCH);
            joiner.onFailed(entry.getData(), entry.getJobId(), FailureReason.MISMATCH);
            Counter.builder(FlowMetricNames.EGRESS_PASSIVE)
                   .tag(FlowMetricNames.TAG_JOB_ID, partner.getJobId())
                   .tag(FlowMetricNames.TAG_REASON, "MISMATCH")
                   .register(meterRegistry)
                   .increment();
            Counter.builder(FlowMetricNames.EGRESS_PASSIVE)
                   .tag(FlowMetricNames.TAG_JOB_ID, entry.getJobId())
                   .tag(FlowMetricNames.TAG_REASON, "MISMATCH")
                   .register(meterRegistry)
                   .increment();
        } catch (Exception e) {
            FlowExceptionHelper.handleException(entry.getJobId(), null, e, FlowPhase.CONSUMPTION, "mismatch_process_failed");
        }
        progressTracker.onPassiveEgress(FailureReason.MISMATCH);
        progressTracker.onPassiveEgress(FailureReason.MISMATCH);
    }
}
