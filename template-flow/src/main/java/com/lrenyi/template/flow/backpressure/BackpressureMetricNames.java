package com.lrenyi.template.flow.backpressure;

/**
 * 背压体系指标名称与标签常量。
 */
public final class BackpressureMetricNames {
    
    private static final String PREFIX = "backpressure";
    
    // ─── 维度级指标 ───────────────────────────────────────────────────────────
    /** 维度 acquire 调用次数（每次调用计一次） */
    public static final String DIM_ACQUIRE_ATTEMPTS = PREFIX + ".dimension.acquire.attempts";
    /** 维度 acquire 发生背压阻塞次数（需要等待才获得资源） */
    public static final String DIM_ACQUIRE_BLOCKED = PREFIX + ".dimension.acquire.blocked";
    /** 维度 acquire 超时次数 */
    public static final String DIM_ACQUIRE_TIMEOUT = PREFIX + ".dimension.acquire.timeout";
    /** 维度 acquire 耗时分布（Timer） */
    public static final String DIM_ACQUIRE_DURATION = PREFIX + ".dimension.acquire.duration";
    /** 维度资源释放次数 */
    public static final String DIM_RELEASE_COUNT = PREFIX + ".dimension.release.count";
    
    // ─── 管理器级指标 ─────────────────────────────────────────────────────────
    /** acquire 调用成功次数 */
    public static final String MANAGER_ACQUIRE_SUCCESS = PREFIX + ".manager.acquire.success";
    /** acquire 调用失败次数（含超时、中断、异常） */
    public static final String MANAGER_ACQUIRE_FAILED = PREFIX + ".manager.acquire.failed";
    /** 当前活跃 lease 数（Gauge） */
    public static final String MANAGER_LEASE_ACTIVE = PREFIX + ".manager.lease.active";
    /** 幂等 close 次数（重复调用 close） */
    public static final String MANAGER_RELEASE_IDEMPOTENT_HIT = PREFIX + ".manager.release.idempotent_hit";
    /** 泄露检测次数（lease GC 前未 close） */
    public static final String MANAGER_RELEASE_LEAK_DETECTED = PREFIX + ".manager.release.leak_detected";
    
    // ─── 标签 ─────────────────────────────────────────────────────────────────
    /** Job 标识标签（注意高基数风险） */
    public static final String TAG_JOB_ID = "jobId";
    /** 维度 ID 标签 */
    public static final String TAG_DIMENSION_ID = "dimensionId";
    /** 结果标签 */
    public static final String TAG_OUTCOME = "outcome";
    
    private BackpressureMetricNames() {}
}
