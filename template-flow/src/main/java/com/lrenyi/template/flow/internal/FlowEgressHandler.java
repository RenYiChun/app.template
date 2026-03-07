package com.lrenyi.template.flow.internal;

import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.exception.FlowExceptionHelper;
import com.lrenyi.template.flow.exception.FlowPhase;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.model.EgressReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 统一出口记账：joiner 回调、EGRESS_ACTIVE/PASSIVE 计数、progressTracker 的唯一记账处。
 * 不负责 submit、claimLogic、signalRelease；不持有/关闭 entry，由调用方负责 try-with-resources。
 */
@Slf4j
public final class FlowEgressHandler<T> {
    private final FlowJoiner<T> joiner;
    private final ProgressTracker progressTracker;
    private final MeterRegistry meterRegistry;
    
    public FlowEgressHandler(FlowJoiner<T> joiner, ProgressTracker progressTracker, MeterRegistry meterRegistry) {
        this.joiner = joiner;
        this.progressTracker = progressTracker;
        this.meterRegistry = meterRegistry;
    }
    
    /**
     * 配对消费：onPairConsumed、EGRESS_ACTIVE、progressTracker。
     * 调用方负责 entry 生命周期（如 runnable 内 try (partner; entry)）。
     */
    public void performPairConsumed(FlowEntry<T> partner, FlowEntry<T> entry) {
        String jobId = entry.getJobId();
        if (log.isDebugEnabled()) {
            log.debug("Pair consumed start, jobId={}", jobId);
        }
        try {
            joiner.onPairConsumed(partner.getData(), entry.getData(), jobId);
        } catch (Exception e) {
            FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.CONSUMPTION, "onPairConsumed_failed");
        }
        try {
            progressTracker.onActiveEgress();
            progressTracker.onActiveEgress();
        } catch (Exception e) {
            FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.CONSUMPTION, "progress_tracker_pair_failed");
        }
        Counter.builder(FlowMetricNames.EGRESS_ACTIVE)
               .tag(FlowMetricNames.TAG_JOB_ID, jobId)
               .register(meterRegistry)
               .increment(2);
        if (log.isDebugEnabled()) {
            log.debug("Pair consumed finished, jobId={}", jobId);
        }
    }
    
    /**
     * 单条消费：onSingleConsumed、EGRESS_ACTIVE/PASSIVE（按 reason）、progressTracker。
     * 仅当 reason 为被动原因时调用 onPassiveEgress(reason)，SINGLE_CONSUMED 走 onActiveEgress。
     * 调用方负责 entry 生命周期。
     */
    public void performSingleConsumed(FlowEntry<T> entry, EgressReason reason) {
        String jobId = entry.getJobId();
        if (log.isDebugEnabled()) {
            log.debug("Single consumed start, jobId={}, reason={}", jobId, reason);
        }
        try {
            joiner.onSingleConsumed(entry.getData(), jobId, reason);
        } catch (Exception e) {
            FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.CONSUMPTION, "onSingleConsumed_failed");
        }
        boolean passive = reason != null && reason.isPassive();
        try {
            if (passive) {
                progressTracker.onPassiveEgress(reason);
            } else {
                progressTracker.onActiveEgress();
            }
        } catch (Exception e) {
            FlowExceptionHelper.handleException(jobId,
                                                null,
                                                e,
                                                FlowPhase.CONSUMPTION,
                                                "progress_tracker_single_failed"
            );
        }
        if (passive) {
            Counter.builder(FlowMetricNames.EGRESS_PASSIVE)
                   .tag(FlowMetricNames.TAG_JOB_ID, jobId)
                   .tag(FlowMetricNames.TAG_REASON, reason.name())
                   .register(meterRegistry)
                   .increment();
            if (reason == EgressReason.TIMEOUT
                    || reason == EgressReason.EVICTION
                    || reason == EgressReason.REJECT
                    || reason == EgressReason.MISMATCH) {
                log.warn("Passive egress occurred, jobId={}, reason={}", jobId, reason);
            }
        } else {
            Counter.builder(FlowMetricNames.EGRESS_ACTIVE)
                   .tag(FlowMetricNames.TAG_JOB_ID, jobId)
                   .register(meterRegistry)
                   .increment();
        }
        if (log.isDebugEnabled()) {
            log.debug("Single consumed finished, jobId={}, reason={}, passive={}", jobId, reason, passive);
        }
    }
}
