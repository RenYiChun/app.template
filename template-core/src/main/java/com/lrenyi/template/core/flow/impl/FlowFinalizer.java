package com.lrenyi.template.core.flow.impl;

import com.lrenyi.template.core.flow.context.FlowEntry;
import com.lrenyi.template.core.flow.context.Orchestrator;
import com.lrenyi.template.core.flow.manager.FlowManager;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public record FlowFinalizer<T>(FlowManager flowManager) {
    
    /**
     * 仅执行 finalizer 的 body + release，不 acquire。
     * 用于 removal 路径：物理线程已先 acquire()，此处将 try(entry)、onConsume/onPassiveEgress、release() 提交到虚拟线程执行。
     *
     * @param entry    被驱逐的 entry
     * @param launcher 当前 job 的 launcher（用于 release 与 signalRelease）
     */
    public void submitBodyOnly(FlowEntry<T> entry, FlowLauncher<Object> launcher) {
        Orchestrator<Object> taskOrchestrator = launcher.getTaskOrchestrator();
        String jobId = entry.getJobId();
        flowManager.getGlobalExecutor().submit(() -> {
            try (entry) {
                if (entry.claimLogic()) {
                    flowManager.getProgressTracker(jobId).onActiveEgress();
                    launcher.getFlowJoiner().onConsume(entry.getData(), jobId);
                } else {
                    flowManager.getProgressTracker(jobId).onPassiveEgress();
                }
            } catch (Throwable t) {
                log.error("Finalizer body failed for job: {}", jobId, t);
            } finally {
                taskOrchestrator.release();
                if (launcher.getBackpressureController() != null) {
                    launcher.getBackpressureController().signalRelease();
                }
            }
        });
    }
}
