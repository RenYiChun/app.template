package com.lrenyi.template.flow.internal;

/**
 * 统一的下游压力评估器接口。
 * 用于在 soft timeout 到期时判断当前是否允许进行超时离库。
 */
public interface DownstreamPressureEvaluator {

    ExpiryDecision evaluate(String jobId, long nowEpochMs);

    record ExpiryDecision(
            ExpiryDecisionType type,
            String reason,
            long suggestedDelayMs
    ) {}

    enum ExpiryDecisionType {
        ALLOW_SOFT_DRAIN,
        DEFER,
        FORCE_HARD_DRAIN
    }
}

