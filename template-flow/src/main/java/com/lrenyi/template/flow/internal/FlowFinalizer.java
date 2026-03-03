package com.lrenyi.template.flow.internal;

import java.util.concurrent.TimeUnit;
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
import lombok.extern.slf4j.Slf4j;

@Slf4j
public record FlowFinalizer<T>(FlowResourceRegistry resourceRegistry, MeterRegistry meterRegistry) {
    
    /**
     * removalReason 非空表示来自 Caffeine removalListener 的驱逐（EXPIRED/SIZE/REPLACED），
     * 应计为被动出口并调用 onFailed，而非主动消费。
     */
    public void submitBodyOnly(FlowEntry<T> entry, FlowLauncher<Object> launcher) {
        Orchestrator taskOrchestrator = launcher.getTaskOrchestrator();
        String jobId = entry.getJobId();
        long startTime = System.currentTimeMillis();

        Runnable runnable = () -> {
            try (entry) {
                if (entry.claimLogic()) {
                    taskOrchestrator.tracker().onActiveEgress();
                    Counter.builder(FlowMetricNames.EGRESS_ACTIVE)
                           .tag(FlowMetricNames.TAG_JOB_ID, jobId)
                           .register(meterRegistry)
                           .increment();
                    performConsume(launcher, entry, jobId);
                } else {
                    taskOrchestrator.tracker().onPassiveEgress();
                    String removalReason = entry.getRemovalReason();
                    if (removalReason != null) {
                        // Caffeine 驱逐：EXPIRED/SIZE/REPLACED -> 指标计为被动出口
                        Counter.builder(FlowMetricNames.EGRESS_PASSIVE)
                               .tag(FlowMetricNames.TAG_JOB_ID, jobId)
                               .tag(FlowMetricNames.TAG_REASON, removalReason)
                               .register(meterRegistry)
                               .increment();
                    }
                }
            } catch (Exception t) {
                FlowExceptionHelper.handleException(jobId, null, t, FlowPhase.FINALIZATION);
                Counter.builder(FlowMetricNames.ERRORS)
                       .tag(FlowMetricNames.TAG_ERROR_TYPE, "finalizer_body_failed")
                       .tag(FlowMetricNames.TAG_PHASE, "FINALIZATION")
                       .register(meterRegistry)
                       .increment();
            } finally {
                if (launcher.getBackpressureController() != null) {
                    launcher.getBackpressureController().signalRelease();
                }
                long latency = System.currentTimeMillis() - startTime;
                Timer.builder(FlowMetricNames.FINALIZE_DURATION)
                     .tag(FlowMetricNames.TAG_JOB_ID, jobId)
                     .register(meterRegistry)
                     .record(latency, TimeUnit.MILLISECONDS);
            }
        };
        resourceRegistry.submitConsumerToGlobal(taskOrchestrator, runnable);
    }
    
    private void performConsume(FlowLauncher<Object> launcher, FlowEntry<T> entry, String jobId) {
        try {
            launcher.getFlowJoiner().onConsume(entry.getData(), jobId);
        } catch (Exception e) {
            FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.CONSUMPTION);
            Counter.builder(FlowMetricNames.ERRORS)
                   .tag(FlowMetricNames.TAG_ERROR_TYPE, "onConsume_failed")
                   .tag(FlowMetricNames.TAG_PHASE, "CONSUMPTION")
                   .register(meterRegistry)
                   .increment();
        }
    }
    
    private void performFailed(FlowLauncher<Object> launcher,
            FlowEntry<T> entry,
            String jobId,
            FailureReason failureReason) {
        try {
            launcher.getFlowJoiner().onFailed(entry.getData(), jobId, failureReason);
        } catch (Exception e) {
            FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.FINALIZATION);
            Counter.builder(FlowMetricNames.ERRORS)
                   .tag(FlowMetricNames.TAG_ERROR_TYPE, "onFailed_failed")
                   .tag(FlowMetricNames.TAG_PHASE, "FINALIZATION")
                   .register(meterRegistry)
                   .increment();
        }
    }
    
    /** Caffeine RemovalCause 映射为 FailureReason */
    private static FailureReason toFailureReason(String removalReason) {
        if (removalReason == null) {
            return FailureReason.EVICTION;
        }
        return switch (removalReason) {
            case "EXPIRED" -> FailureReason.TIMEOUT;
            case "REPLACED" -> FailureReason.REPLACE;
            default -> FailureReason.EVICTION; // SIZE, COLLECTED 等
        };
    }
}
