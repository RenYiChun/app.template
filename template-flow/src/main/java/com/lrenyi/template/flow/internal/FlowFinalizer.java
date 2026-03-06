package com.lrenyi.template.flow.internal;

import java.util.concurrent.TimeUnit;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.context.Orchestrator;
import com.lrenyi.template.flow.exception.FlowExceptionHelper;
import com.lrenyi.template.flow.exception.FlowPhase;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public record FlowFinalizer<T>(FlowResourceRegistry resourceRegistry, MeterRegistry meterRegistry,
                               FlowEgressHandler<T> egressHandler) {

    /**
     * removalReason 非空表示来自 Caffeine removalListener 的驱逐（EXPIRED/SIZE/REPLACED），
     * 应计为被动出口并调用 onFailed，而非主动消费。
     */
    public void submitBodyOnly(FlowEntry<T> entry, FlowLauncher<Object> launcher) {
        Orchestrator taskOrchestrator = launcher.getTaskOrchestrator();
        String jobId = entry.getJobId();
        long startTime = System.currentTimeMillis();

        Runnable runnable = () -> {
            boolean didFinalize = false;
            try (entry) {
                if (entry.claimLogic()) {
                    egressHandler.performSingleConsumed(entry, EgressReason.SINGLE_CONSUMED);
                    didFinalize = true;
                } else {
                    // claimLogic() 返回 false 表示已被其它路径（配对/驱逐）抢占并处理，计数已在彼处完成，此处无需再计
                    log.info("Entry {} claimed by other path, skipping finalizer", entry.getJobId());
                }
            } catch (Exception t) {
                FlowExceptionHelper.handleException(jobId, null, t, FlowPhase.FINALIZATION, "finalizer_body_failed");
            } finally {
                if (launcher.getBackpressureController() != null) {
                    launcher.getBackpressureController().signalRelease();
                }
                if (didFinalize) {
                    long latency = System.currentTimeMillis() - startTime;
                    Timer.builder(FlowMetricNames.FINALIZE_DURATION)
                         .tag(FlowMetricNames.TAG_JOB_ID, jobId)
                         .register(meterRegistry)
                         .record(latency, TimeUnit.MILLISECONDS);
                }
            }
        };
        resourceRegistry.submitConsumerToGlobal(taskOrchestrator, runnable);
    }
}
