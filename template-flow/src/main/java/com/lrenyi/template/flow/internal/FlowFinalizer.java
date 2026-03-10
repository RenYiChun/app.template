package com.lrenyi.template.flow.internal;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.backpressure.DimensionLease;
import com.lrenyi.template.flow.backpressure.dimension.InFlightConsumerDimension;
import com.lrenyi.template.flow.context.FlowEntry;
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
public record FlowFinalizer<T>(FlowResourceRegistry resourceRegistry, MeterRegistry meterRegistry,
                               FlowEgressHandler<T> egressHandler, FlowJoiner<T> joiner) {

    /**
     * 将数据提交至消费端，通过 BackpressureManager 获取 in-flight-consumer 槽位租约。
     * 租约在消费任务完成后由 close() 释放。
     */
    public void submitDataToConsumer(FlowEntry<T> entry, FlowLauncher<Object> launcher) {
        String jobId = entry.getJobId();
        long startTime = System.currentTimeMillis();
        TemplateConfigProperties.Flow.PerJob perJob = launcher.getFlow().getLimits().getPerJob();
        boolean strictPending = perJob.isStrictPendingConsumerSlot();
        
        DimensionLease slotLease;
        try {
            slotLease = launcher.getBackpressureManager().acquire(InFlightConsumerDimension.ID, null);
        } catch (TimeoutException e) {
            Counter.builder(FlowMetricNames.FINALIZER_PENDING_SLOT_ACQUIRE_TIMEOUT)
                   .tag(FlowMetricNames.TAG_JOB_ID, jobId)
                   .register(meterRegistry)
                   .increment();
            if (strictPending) {
                Counter.builder(FlowMetricNames.FINALIZER_SUBMIT_SKIPPED)
                       .tag(FlowMetricNames.TAG_JOB_ID, jobId)
                       .register(meterRegistry)
                       .increment();
                return;
            }
            // Non-strict: proceed without slot
            slotLease = DimensionLease.noop(InFlightConsumerDimension.ID);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            FlowExceptionHelper.handleException(jobId,
                                                null,
                                                e,
                                                FlowPhase.FINALIZATION,
                                                "pending_slot_acquire_interrupted"
            );
            return;
        }
        
        final DimensionLease leaseToClose = slotLease;
        Runnable runnable = () -> {
            try {
                boolean didFinalize = false;
                try (entry) {
                    if (entry.claimLogic()) {
                        egressHandler.performSingleConsumed(entry, EgressReason.SINGLE_CONSUMED);
                        didFinalize = true;
                    } else {
                        log.info("Entry {} claimed by other path, skipping finalizer", entry.getJobId());
                    }
                } catch (Exception t) {
                    FlowExceptionHelper.handleException(jobId,
                                                        null,
                                                        t,
                                                        FlowPhase.FINALIZATION,
                                                        "finalizer_body_failed"
                    );
                } finally {
                    if (didFinalize) {
                        long latency = System.currentTimeMillis() - startTime;
                        Timer.builder(FlowMetricNames.FINALIZE_DURATION)
                             .tag(FlowMetricNames.TAG_JOB_ID, jobId)
                             .register(meterRegistry)
                             .record(latency, java.util.concurrent.TimeUnit.MILLISECONDS);
                    }
                }
            } finally {
                leaseToClose.close();
            }
        };
        resourceRegistry.submitConsumer(launcher.getTaskOrchestrator(), runnable);
    }
    
    /**
     * 将配对数据提交至消费端。partner 与 entry 具有相同 joinKey，由 joiner.isMatched 判定是否配对成功。
     * 占用 2 个 in-flight-consumer 槽位，消费并发许可为 2。
     */
    public void submitPairDataToConsumer(FlowEntry<T> partner, FlowEntry<T> entry, FlowLauncher<Object> launcher) {
        partner.closeStorageLease();
        String jobId = entry.getJobId();
        long matchStartTime = System.currentTimeMillis();
        TemplateConfigProperties.Flow.PerJob perJob = launcher.getFlow().getLimits().getPerJob();
        boolean strictPending = perJob.isStrictPendingConsumerSlot();
        
        DimensionLease slotLease;
        try {
            slotLease = launcher.getBackpressureManager().acquire(InFlightConsumerDimension.ID, null, 2);
        } catch (TimeoutException e) {
            Counter.builder(FlowMetricNames.FINALIZER_PENDING_SLOT_ACQUIRE_TIMEOUT)
                   .tag(FlowMetricNames.TAG_JOB_ID, jobId)
                   .register(meterRegistry)
                   .increment();
            if (strictPending) {
                Counter.builder(FlowMetricNames.FINALIZER_SUBMIT_SKIPPED)
                       .tag(FlowMetricNames.TAG_JOB_ID, jobId)
                       .register(meterRegistry)
                       .increment();
                return;
            }
            slotLease = DimensionLease.noop(InFlightConsumerDimension.ID);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            FlowExceptionHelper.handleException(jobId,
                                                null,
                                                e,
                                                FlowPhase.FINALIZATION,
                                                "pending_slot_acquire_interrupted"
            );
            return;
        }
        
        final DimensionLease leaseToClose = slotLease;
        Runnable runnable = () -> {
            try {
                try (partner; entry) {
                    if (!partner.claimLogic() || !entry.claimLogic()) {
                        return;
                    }
                    if (joiner.isMatched(partner.getData(), entry.getData())) {
                        egressHandler.performPairConsumed(partner, entry);
                    } else {
                        egressHandler.performSingleConsumed(partner, EgressReason.MISMATCH);
                        egressHandler.performSingleConsumed(entry, EgressReason.MISMATCH);
                    }
                    long matchLatency = System.currentTimeMillis() - matchStartTime;
                    Timer.builder(FlowMetricNames.MATCH_DURATION)
                         .tag(FlowMetricNames.TAG_JOB_ID, jobId)
                         .register(meterRegistry)
                         .record(matchLatency, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.CONSUMPTION, "match_process_failed");
                    Counter.builder(FlowMetricNames.ERRORS)
                           .tag(FlowMetricNames.TAG_ERROR_TYPE, "match_process_failed")
                           .tag(FlowMetricNames.TAG_PHASE, "CONSUMPTION")
                           .register(meterRegistry)
                           .increment();
                }
            } finally {
                leaseToClose.close();
            }
        };
        resourceRegistry.submitConsumer(launcher.getTaskOrchestrator(), 2, runnable);
    }
}
