package com.lrenyi.template.flow.internal;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import com.lrenyi.template.flow.context.FlowEntry;
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
    
    private static final long PENDING_SLOT_ACQUIRE_TIMEOUT_MS = 30_000L;

    /**
     * removalReason 非空表示来自 Caffeine removalListener 的驱逐（EXPIRED/SIZE/REPLACED），
     * 应计为被动出口并调用 onFailed，而非主动消费。
     */
    public void submitDataToConsumer(FlowEntry<T> entry, FlowLauncher<Object> launcher) {
        String jobId = entry.getJobId();
        long startTime = System.currentTimeMillis();
        Semaphore slotSemaphore = launcher.getResourceContext().getPendingConsumerSlotSemaphore();
        boolean slotAcquired = false;
        if (slotSemaphore != null) {
            try {
                slotAcquired = slotSemaphore.tryAcquire(PENDING_SLOT_ACQUIRE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (!slotAcquired) {
                    log.warn("Pending consumer slot acquire timeout, jobId={}, timeoutMs={}, submitting anyway (limit "
                                     + "may be exceeded)", jobId, PENDING_SLOT_ACQUIRE_TIMEOUT_MS
                    );
                }
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
        }
        
        final boolean releaseSlot = slotAcquired;
        final Semaphore slotToRelease = slotSemaphore;
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
            } finally {
                if (releaseSlot) {
                    slotToRelease.release();
                }
            }
        };
        resourceRegistry.submitConsumer(launcher.getTaskOrchestrator(), runnable);
    }
}
