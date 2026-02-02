package com.lrenyi.template.core.flow.impl;

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
     * 仅执行 finalizer 的 body + release，不 acquire。
     * 用于 removal 路径：物理线程已先 acquire()，此处将 try(entry)、onConsume/onPassiveEgress、release() 提交到虚拟线程执行。
     *
     * @param entry    被驱逐的 entry
     * @param launcher 当前 job 的 launcher（用于 release 与 signalRelease）
     */
    public void submitBodyOnly(FlowEntry<T> entry, FlowLauncher<Object> launcher) {
        Orchestrator taskOrchestrator = launcher.getTaskOrchestrator();
        String jobId = entry.getJobId();
        long startTime = System.currentTimeMillis();
        
        // 从 resourceRegistry 获取全局执行器
        resourceRegistry.getGlobalExecutor().submit(() -> {
            try (entry) {
                if (entry.claimLogic()) {
                    // 通过 launcher 获取 tracker
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
                taskOrchestrator.release();
                if (launcher.getBackpressureController() != null) {
                    launcher.getBackpressureController().signalRelease();
                }
                long latency = System.currentTimeMillis() - startTime;
                FlowMetrics.recordLatency("finalize", latency);
            }
        });
    }
}
