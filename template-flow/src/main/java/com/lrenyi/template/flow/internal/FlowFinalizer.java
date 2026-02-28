package com.lrenyi.template.flow.internal;

import java.util.concurrent.TimeUnit;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.context.Orchestrator;
import com.lrenyi.template.flow.exception.FlowExceptionHelper;
import com.lrenyi.template.flow.exception.FlowPhase;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public record FlowFinalizer<T>(FlowResourceRegistry resourceRegistry, MeterRegistry meterRegistry) {
    
    public void submitBodyOnly(FlowEntry<T> entry, FlowLauncher<Object> launcher) {
        Orchestrator taskOrchestrator = launcher.getTaskOrchestrator();
        String jobId = entry.getJobId();
        long startTime = System.currentTimeMillis();
        
        resourceRegistry.submitConsumerToGlobal(taskOrchestrator, () -> {
                                                    try (entry) {
                                                        if (entry.claimLogic()) {
                                                            taskOrchestrator.tracker().onActiveEgress();
                                                            Counter.builder(FlowMetricNames.EGRESS_ACTIVE)
                                                                   .tag(FlowMetricNames.TAG_JOB_ID, jobId)
                                                                   .register(meterRegistry)
                                                                   .increment();
                                                            try {
                                                                launcher.getFlowJoiner().onConsume(entry.getData(),
                                                                                                   jobId);
                                                            } catch (Exception e) {
                                                                FlowExceptionHelper.handleException(jobId, null, e,
                                                                                                    FlowPhase.CONSUMPTION);
                                                                Counter.builder(FlowMetricNames.ERRORS)
                                                                       .tag(FlowMetricNames.TAG_ERROR_TYPE,
                                                                            "onConsume_failed")
                                                                       .tag(FlowMetricNames.TAG_PHASE, "CONSUMPTION")
                                                                       .register(meterRegistry)
                                                                       .increment();
                                                            }
                                                        } else {
                                                            taskOrchestrator.tracker().onPassiveEgress();
                                                        }
                                                    } catch (Throwable t) {
                                                        FlowExceptionHelper.handleException(jobId, null, t,
                                                                                            FlowPhase.FINALIZATION);
                                                        Counter.builder(FlowMetricNames.ERRORS)
                                                               .tag(FlowMetricNames.TAG_ERROR_TYPE,
                                                                    "finalizer_body_failed")
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
                                                }
        );
    }
}
