package com.lrenyi.template.flow.internal;

import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/**
 * 默认下游压力评估器实现。
 * 基于消费许可与 pending 指标判断是否允许 soft timeout drain。
 */
public final class DefaultDownstreamPressureEvaluator implements DownstreamPressureEvaluator {

    private final IntSupplier jobConsumerAvailablePermits;
    private final IntSupplier globalConsumerAvailablePermits;
    private final LongSupplier perJobPendingCount;
    private final int perJobPendingLimit;
    private final LongSupplier globalPendingCount;
    private final int globalPendingLimit;
    private final long deferInitialMs;
    private final long deferMaxMs;

    public DefaultDownstreamPressureEvaluator(
            IntSupplier jobConsumerAvailablePermits,
            IntSupplier globalConsumerAvailablePermits,
            LongSupplier perJobPendingCount,
            int perJobPendingLimit,
            LongSupplier globalPendingCount,
            int globalPendingLimit,
            long deferInitialMs,
            long deferMaxMs
    ) {
        this.jobConsumerAvailablePermits = jobConsumerAvailablePermits;
        this.globalConsumerAvailablePermits = globalConsumerAvailablePermits;
        this.perJobPendingCount = perJobPendingCount;
        this.perJobPendingLimit = perJobPendingLimit;
        this.globalPendingCount = globalPendingCount;
        this.globalPendingLimit = globalPendingLimit;
        this.deferInitialMs = deferInitialMs;
        this.deferMaxMs = deferMaxMs;
    }

    @Override
    public ExpiryDecision evaluate(String jobId, long nowEpochMs) {
        long deferMs = Math.min(deferInitialMs, deferMaxMs);

        if (jobConsumerAvailablePermits != null && jobConsumerAvailablePermits.getAsInt() <= 0) {
            return new ExpiryDecision(ExpiryDecisionType.DEFER, "consumer_permits_exhausted", deferMs);
        }
        if (globalConsumerAvailablePermits != null && globalConsumerAvailablePermits.getAsInt() <= 0) {
            return new ExpiryDecision(ExpiryDecisionType.DEFER, "global_consumer_permits_exhausted", deferMs);
        }
        if (perJobPendingLimit > 0 && perJobPendingCount != null
                && perJobPendingCount.getAsLong() >= perJobPendingLimit) {
            return new ExpiryDecision(ExpiryDecisionType.DEFER, "pending_consumer_overflow", deferMs);
        }
        if (globalPendingLimit > 0 && globalPendingCount != null
                && globalPendingCount.getAsLong() >= globalPendingLimit) {
            return new ExpiryDecision(ExpiryDecisionType.DEFER, "global_pending_consumer_overflow", deferMs);
        }
        return new ExpiryDecision(ExpiryDecisionType.ALLOW_SOFT_DRAIN, "ok", 0L);
    }
}

