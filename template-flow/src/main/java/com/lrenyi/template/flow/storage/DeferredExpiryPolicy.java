package com.lrenyi.template.flow.storage;

/**
 * 软超时延期策略：根据当前时间与 slot 元数据计算下一次检查时间。
 */
public interface DeferredExpiryPolicy {

    /**
     * 计算下一次检查时间戳（毫秒）。
     *
     * @param nowEpochMs 当前时间
     * @param earliestSoftExpireAt 槽内最早软超时时间
     * @param earliestHardExpireAt 槽内最早硬超时时间
     * @param previousNextCheckAt  上一次 nextCheckAt（如有）
     * @return 下一次检查时间戳（毫秒）
     */
    long nextCheckAt(long nowEpochMs, long earliestSoftExpireAt, long earliestHardExpireAt, long previousNextCheckAt);
}

