package com.lrenyi.template.core.flow.internal;

import com.lrenyi.template.core.flow.context.FlowEntry;
import com.lrenyi.template.core.flow.context.Orchestrator;
import com.lrenyi.template.core.flow.exception.FlowExceptionHelper;
import com.lrenyi.template.core.flow.exception.FlowPhase;
import com.lrenyi.template.core.flow.metrics.FlowMetrics;
import com.lrenyi.template.core.flow.resource.FlowResourceRegistry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public record FlowFinalizer<T>(FlowResourceRegistry resourceRegistry) {

    /**
     * 提交 finalizer body 到全局执行器（带公平 acquire）。
     * 用于 removal/drain 路径：acquire + body + releaseWithoutSemaphore 由 FlowGlobalExecutor 统一处理。
     *
     * @param entry    被驱逐的 entry
     * @param launcher 当前 job 的 launcher（用于 release 与 signalRelease）
     */
    public void submitBodyOnly(FlowEntry<T> entry, FlowLauncher<Object> launcher) {
        Orchestrator taskOrchestrator = launcher.getTaskOrchestrator();
        String jobId = entry.getJobId();
        long startTime = System.currentTimeMillis();

        resourceRegistry.submitConsumerToGlobal(taskOrchestrator, () -> {
            try (entry) {
                if (entry.claimLogic()) {
                    taskOrchestrator.tracker().onActiveEgress();
                    try {
                        launcher.getFlowJoiner().onConsume(entry.getData(), jobId);
                        FlowMetrics.incrementCounter("consume_success");
                    } catch (Exception e) {
                        FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.CONSUMPTION);
                        FlowMetrics.recordError("onConsume_failed", jobId);
                    }
                } else {
                    taskOrchestrator.tracker().onPassiveEgress();
                }
            } catch (Throwable t) {
                FlowExceptionHelper.handleException(jobId, null, t, FlowPhase.FINALIZATION);
                FlowMetrics.recordError("finalizer_body_failed", jobId);
            } finally {
                if (launcher.getBackpressureController() != null) {
                    launcher.getBackpressureController().signalRelease();
                }
                long latency = System.currentTimeMillis() - startTime;
                FlowMetrics.recordLatency("finalize", latency);
            }
        });
    }
}
