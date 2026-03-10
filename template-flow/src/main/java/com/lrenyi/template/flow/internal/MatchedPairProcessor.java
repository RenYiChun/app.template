package com.lrenyi.template.flow.internal;

import java.util.concurrent.TimeoutException;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.backpressure.DimensionLease;
import com.lrenyi.template.flow.backpressure.dimension.InFlightConsumerDimension;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.context.Orchestrator;
import com.lrenyi.template.flow.exception.FlowExceptionHelper;
import com.lrenyi.template.flow.exception.FlowPhase;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MatchedPairProcessor<T> {
    private final FlowJoiner<T> joiner;
    private final FlowEgressHandler<T> egressHandler;
    private final MeterRegistry meterRegistry;
    private final FlowResourceRegistry resourceRegistry;

    public MatchedPairProcessor(FlowJoiner<T> joiner,
            FlowEgressHandler<T> egressHandler,
            MeterRegistry meterRegistry,
            FlowResourceRegistry resourceRegistry) {
        this.joiner = joiner;
        this.egressHandler = egressHandler;
        this.meterRegistry = meterRegistry;
        this.resourceRegistry = resourceRegistry;
    }

    public void processMatchedPair(FlowEntry<T> partner, FlowEntry<T> entry, FlowLauncher<Object> launcher) {
        partner.closeStorageLease();
        long matchStartTime = System.currentTimeMillis();
        Orchestrator taskOrchestrator = launcher.getTaskOrchestrator();
        
        // Acquire 2 in-flight-consumer slots (one per entry in the pair)
        DimensionLease slotLease1 = acquireSlot(launcher, entry.getJobId());
        DimensionLease slotLease2 = acquireSlot(launcher, entry.getJobId());

        Runnable runnable = () -> {
            try {
                try (partner; entry) {
                    executeMatchedPairLogicBody(partner, entry);
                    long matchLatency = System.currentTimeMillis() - matchStartTime;
                    Timer.builder(FlowMetricNames.MATCH_DURATION)
                         .tag(FlowMetricNames.TAG_JOB_ID, entry.getJobId())
                         .register(meterRegistry)
                         .record(matchLatency, java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    FlowExceptionHelper.handleException(entry.getJobId(),
                                                        null,
                                                        e,
                                                        FlowPhase.CONSUMPTION,
                                                        "match_process_failed"
                    );
                    Counter.builder(FlowMetricNames.ERRORS)
                           .tag(FlowMetricNames.TAG_ERROR_TYPE, "match_process_failed")
                           .tag(FlowMetricNames.TAG_PHASE, "CONSUMPTION")
                           .register(meterRegistry)
                           .increment();
                }
            } finally {
                slotLease1.close();
                slotLease2.close();
            }
        };
        resourceRegistry.submitConsumer(taskOrchestrator, 2, runnable);
    }
    
    private DimensionLease acquireSlot(FlowLauncher<Object> launcher, String jobId) {
        try {
            return launcher.getBackpressureManager().acquire(InFlightConsumerDimension.ID, null);
        } catch (TimeoutException e) {
            log.warn("In-flight-consumer slot acquire timeout (pair), jobId={}, submitting anyway", jobId);
            return DimensionLease.noop(InFlightConsumerDimension.ID);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            FlowExceptionHelper.handleException(jobId,
                                                null,
                                                e,
                                                FlowPhase.CONSUMPTION,
                                                "pending_slot_acquire_interrupted"
            );
            return DimensionLease.noop(InFlightConsumerDimension.ID);
        }
    }
    
    private void executeMatchedPairLogicBody(FlowEntry<T> partner, FlowEntry<T> entry) {
        if (!partner.claimLogic() || !entry.claimLogic()) {
            return;
        }
        if (joiner.isMatched(partner.getData(), entry.getData())) {
            handleMatchedSuccess(partner, entry);
            return;
        }
        handleMatchedFailure(partner, entry);
    }
    
    private void handleMatchedSuccess(FlowEntry<T> partner, FlowEntry<T> entry) {
        egressHandler.performPairConsumed(partner, entry);
    }

    private void handleMatchedFailure(FlowEntry<T> partner, FlowEntry<T> entry) {
        egressHandler.performSingleConsumed(partner, EgressReason.MISMATCH);
        egressHandler.performSingleConsumed(entry, EgressReason.MISMATCH);
    }
}
