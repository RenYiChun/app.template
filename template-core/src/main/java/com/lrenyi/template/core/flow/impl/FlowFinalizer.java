package com.lrenyi.template.core.flow.impl;

import com.lrenyi.template.core.flow.FlowJoiner;
import com.lrenyi.template.core.flow.ProgressTracker;
import com.lrenyi.template.core.flow.context.FlowEntry;
import com.lrenyi.template.core.flow.context.Orchestrator;
import com.lrenyi.template.core.flow.manager.FlowManager;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public record FlowFinalizer<T>(FlowManager flowManager) {
    public void submit(FlowEntry<T> entry) {
        ExecutorService executor = flowManager.getGlobalExecutor();
        String jobId = entry.getJobId();
        FlowLauncher<Object> launcher = flowManager.getActiveLauncher(jobId);
        if (launcher == null) {
            log.warn("No active launcher found for jobId: {}", jobId);
            return;
        }
        Orchestrator<Object> taskOrchestrator = launcher.getTaskOrchestrator();
        ProgressTracker tracker = taskOrchestrator.getTracker();
        
        executor.submit(() -> {
            try {
                taskOrchestrator.acquire();
            } catch (InterruptedException e) {
                FlowJoiner<Object> flowJoiner = launcher.getFlowJoiner();
                flowJoiner.onFailed(entry.getData(), jobId);
                tracker.onPassiveEgress();
                Thread.currentThread().interrupt();
                return;
            }
            try (entry) { // 利用 try-with-resources 自动管理引用计数
                if (entry.claimLogic()) {
                    // 信号：主动出口（业务成功匹配或正常消费）
                    flowManager.getProgressTracker(jobId).onActiveEgress();
                    launcher.getFlowJoiner().onConsume(entry.getData(), jobId);
                } else {
                    // 如果 claimLogic 返回 false，说明是过期或被驱逐
                    // 信号：被动出口（策略损耗）
                    flowManager.getProgressTracker(jobId).onPassiveEgress();
                }
            } catch (Throwable t) {
                log.error("Finalizer process failed for job: {}", jobId, t);
            } finally {
                taskOrchestrator.release();
                if (launcher.getBackpressureController() != null) {
                    launcher.getBackpressureController().signalRelease();
                }
            }
        });
    }
}
