package com.lrenyi.template.flow.storage;

import com.lrenyi.template.core.TemplateConfigProperties;

/**
 * 默认延期策略：基于配置的 initial/max 进行指数退避，并确保不超过硬超时时间。
 */
final class DefaultDeferredExpiryPolicy implements DeferredExpiryPolicy {
    private final long initialDelayMs;
    private final long maxDelayMs;

    DefaultDeferredExpiryPolicy(TemplateConfigProperties.Flow.PerJob perJob) {
        TemplateConfigProperties.Flow.KeyedCache cache = perJob.getKeyedCache();
        this.initialDelayMs = cache.getExpiryDeferInitialMill();
        this.maxDelayMs = cache.getExpiryDeferMaxMill();
    }

    @Override
    public long nextCheckAt(long nowEpochMs, long earliestSoftExpireAt, long earliestHardExpireAt,
            long previousNextCheckAt) {
        long defer;
        if (previousNextCheckAt <= 0) {
            defer = initialDelayMs;
        } else {
            long lastDefer = Math.max(0, previousNextCheckAt - nowEpochMs);
            if (lastDefer <= 0) {
                lastDefer = initialDelayMs;
            }
            defer = Math.min(maxDelayMs, lastDefer * 2);
        }
        long candidate = nowEpochMs + defer;
        if (earliestHardExpireAt > 0) {
            candidate = Math.min(candidate, earliestHardExpireAt);
        }
        return candidate;
    }
}

