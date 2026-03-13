package com.lrenyi.template.flow.backpressure;

/**
 * 背压体系指标名称与标签常量。
 */
public final class BackpressureMetricNames {

    private static final String PREFIX = "backpressure";

    // ─── 维度级指标（均按 global / per-job 两个维度划分）────────────────────────
    /** 维度 acquire 调用次数（global 层，每次涉及 global 的调用计一次） */
    public static final String DIM_ACQUIRE_ATTEMPTS_GLOBAL = PREFIX + ".dimension.acquire.attempts.global";
    /** 维度 acquire 调用次数（per-job 层，每次涉及 per-job 的调用计一次） */
    public static final String DIM_ACQUIRE_ATTEMPTS_PER_JOB = PREFIX + ".dimension.acquire.attempts.per_job";
    /** 维度 acquire 因 global 信号量阻塞次数 */
    public static final String DIM_ACQUIRE_BLOCKED_GLOBAL = PREFIX + ".dimension.acquire.blocked.global";
    /** 维度 acquire 因 per-job 信号量阻塞次数 */
    public static final String DIM_ACQUIRE_BLOCKED_PER_JOB = PREFIX + ".dimension.acquire.blocked.per_job";
    /** 维度 acquire 超时次数（global 层） */
    public static final String DIM_ACQUIRE_TIMEOUT_GLOBAL = PREFIX + ".dimension.acquire.timeout.global";
    /** 维度 acquire 超时次数（per-job 层） */
    public static final String DIM_ACQUIRE_TIMEOUT_PER_JOB = PREFIX + ".dimension.acquire.timeout.per_job";
    /** 维度 acquire 耗时分布（global 层，Timer） */
    public static final String DIM_ACQUIRE_DURATION_GLOBAL = PREFIX + ".dimension.acquire.duration.global";
    /** 维度 acquire 耗时分布（per-job 层，Timer） */
    public static final String DIM_ACQUIRE_DURATION_PER_JOB = PREFIX + ".dimension.acquire.duration.per_job";
    /** 维度资源释放次数（global 层） */
    public static final String DIM_RELEASE_COUNT_GLOBAL = PREFIX + ".dimension.release.count.global";
    /** 维度资源释放次数（per-job 层） */
    public static final String DIM_RELEASE_COUNT_PER_JOB = PREFIX + ".dimension.release.count.per_job";

    // ─── 管理器级指标（均按 global / per-job 两个维度划分）────────────────────
    /** acquire 调用成功次数（global 层） */
    public static final String MANAGER_ACQUIRE_SUCCESS_GLOBAL = PREFIX + ".manager.acquire.success.global";
    /** acquire 调用成功次数（per-job 层） */
    public static final String MANAGER_ACQUIRE_SUCCESS_PER_JOB = PREFIX + ".manager.acquire.success.per_job";
    /** acquire 调用失败次数（global 层，含超时于 global） */
    public static final String MANAGER_ACQUIRE_FAILED_GLOBAL = PREFIX + ".manager.acquire.failed.global";
    /** acquire 调用失败次数（per-job 层，含超时于 per-job） */
    public static final String MANAGER_ACQUIRE_FAILED_PER_JOB = PREFIX + ".manager.acquire.failed.per_job";
    /** acquire 调用失败次数（无来源：中断、异常等） */
    public static final String MANAGER_ACQUIRE_FAILED_OTHER = PREFIX + ".manager.acquire.failed.other";
    /** 当前活跃 lease 数（global 层，Gauge） */
    public static final String MANAGER_LEASE_ACTIVE_GLOBAL = PREFIX + ".manager.lease.active.global";
    /** 当前活跃 lease 数（per-job 层，Gauge） */
    public static final String MANAGER_LEASE_ACTIVE_PER_JOB = PREFIX + ".manager.lease.active.per_job";
    /** 幂等 close 次数（global 层） */
    public static final String MANAGER_RELEASE_IDEMPOTENT_HIT_GLOBAL =
        PREFIX + ".manager.release.idempotent_hit.global";
    /** 幂等 close 次数（per-job 层） */
    public static final String MANAGER_RELEASE_IDEMPOTENT_HIT_PER_JOB =
        PREFIX + ".manager.release.idempotent_hit.per_job";
    /** 泄露检测次数（global 层） */
    public static final String MANAGER_RELEASE_LEAK_DETECTED_GLOBAL = PREFIX + ".manager.release.leak_detected.global";
    /** 泄露检测次数（per-job 层） */
    public static final String MANAGER_RELEASE_LEAK_DETECTED_PER_JOB =
        PREFIX + ".manager.release.leak_detected.per_job";

    // ─── 标签 ─────────────────────────────────────────────────────────────────
    /** Job 标识标签（注意高基数风险） */
    public static final String TAG_JOB_ID = "jobId";
    /** 维度 ID 标签 */
    public static final String TAG_DIMENSION_ID = "dimensionId";

    private BackpressureMetricNames() {}
}
