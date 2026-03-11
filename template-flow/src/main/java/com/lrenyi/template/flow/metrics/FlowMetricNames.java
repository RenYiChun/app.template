package com.lrenyi.template.flow.metrics;

/**
 * Flow 引擎指标名称与标签常量。
 * <p>
 * 命名规范：{@code app.template.flow.{category}.{name}}，
 * Prometheus 导出时 '.' 转为 '_'，Counter 自动追加 {@code _total}。
 */
public final class FlowMetricNames {
    public static final String PREFIX = "app.template.flow";

    // ==================== Counters ====================
    /** 物理终结累计数。数据彻底离场、信号量释放时 +1。{@code rate(terminated[1m])} 即 TPS */
    public static final String TERMINATED = PREFIX + ".terminated";
    /**
     * 统一错误计数器，按 errorType + phase 维度聚合。
     * <p>errorType 值域：job_failed / deposit_failed / onSingleConsumed_failed / onPairConsumed_failed /
     * match_process_failed 等。
     * <br>phase 值域：PRODUCTION / STORAGE / CONSUMPTION / FINALIZATION。
     * <br>不携带 jobId 标签（防高基数），通过日志关联具体 Job。
     * <br>高：系统异常频繁，需按 errorType 和 phase 下钻定位。
     */
    public static final String ERRORS = PREFIX + ".errors";

    // ==================== Timers ====================
    /** 单条数据存入 Storage 的耗时。高：Storage 写入瓶颈（锁争用/队列满） */
    public static final String DEPOSIT_DURATION = PREFIX + ".deposit.duration";
    /**
     * Caffeine 配对处理的端到端耗时（含消费许可获取等待）。
     * 高：消费端饱和导致 acquire 久，或 onPairConsumed 回调慢。
     */
    public static final String MATCH_DURATION = PREFIX + ".match.duration";
    /**
     * 终结处理端到端耗时（含排队等待），从 submitDataToConsumer 入口到 onSingleConsumed 完成。
     * 高：消费执行器积压或 onSingleConsumed 回调慢。
     */
    public static final String FINALIZE_DURATION = PREFIX + ".finalize.duration";

    // ==================== Tags ====================
    /** 任务标识。注意高基数风险，仅在任务数可控时使用 */
    public static final String TAG_JOB_ID = "jobId";
    /** 错误类型。如 job_failed / deposit_failed / onConsume_failed 等 */
    public static final String TAG_ERROR_TYPE = "errorType";
    /** 错误发生阶段。PRODUCTION / STORAGE / CONSUMPTION / FINALIZATION */
    public static final String TAG_PHASE = "phase";

    private FlowMetricNames() {}
}
