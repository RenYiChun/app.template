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
    /** 生产许可获取累计数。数据进入框架生产链路时 +1，{@code rate(production_acquired[1m])} 即进入速率（条/秒）。 */
    public static final String PRODUCTION_ACQUIRED = PREFIX + ".production_acquired";
    /** 物理终结累计数。数据彻底离场、信号量释放时 +1。{@code rate(terminated[1m])} 即 TPS */
    public static final String TERMINATED = PREFIX + ".terminated";
    /** 生产许可释放累计数。与 production_acquired 配对，用于完成判定。 */
    public static final String PRODUCTION_RELEASED = PREFIX + ".production_released";
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
    /** 根任务标识，用于跨阶段聚合与 7 天历史回看。 */
    public static final String TAG_ROOT_JOB_ID = "rootJobId";
    /** 根任务显示名，未配置时回退为 rootJobId。 */
    public static final String TAG_ROOT_JOB_DISPLAY_NAME = "rootJobDisplayName";
    /** 阶段路径键，如 0 / 3/fork/7/0。 */
    public static final String TAG_STAGE_KEY = "stageKey";
    /** 阶段名称，用于人类可读展示。 */
    public static final String TAG_STAGE_NAME = "stageName";
    /** 阶段显示名，未配置时回退为 stageKey。 */
    public static final String TAG_STAGE_DISPLAY_NAME = "stageDisplayName";
    /** 根任务展示名。 */
    public static final String TAG_DISPLAY_NAME = "displayName";
    /** 错误类型。如 job_failed / deposit_failed / onConsume_failed 等 */
    public static final String TAG_ERROR_TYPE = "errorType";
    /** 错误发生阶段。PRODUCTION / STORAGE / CONSUMPTION / FINALIZATION */
    public static final String TAG_PHASE = "phase";

    // ==================== 资源限制/使用量 Gauges ====================
    /** 存储容量限制上限（Gauge） */
    public static final String RESOURCES_STORAGE_LIMIT = PREFIX + ".resources.storage.limit";
    /** 存储容量当前使用（Gauge） */
    public static final String RESOURCES_STORAGE_USED = PREFIX + ".resources.storage.used";
    /** Sink 终端全局并发限制上限（Gauge） */
    public static final String RESOURCES_SINK_CONCURRENCY_LIMIT = PREFIX + ".resources.sink_concurrency.limit";
    /** Sink 终端全局并发当前占用（Gauge） */
    public static final String RESOURCES_SINK_CONCURRENCY_USED = PREFIX + ".resources.sink_concurrency.used";
    /** 等待 Sink 全局并发许可的耗时（Timer） */
    public static final String SINK_CONCURRENCY_WAIT_DURATION = PREFIX + ".sink_concurrency.wait.duration";
    /** Sink 全局并发许可获取超时次数（Counter） */
    public static final String SINK_CONCURRENCY_ACQUIRE_TIMEOUT = PREFIX + ".sink_concurrency.acquire.timeout";

    // ==================== Per-job 资源指标（与全局区分，避免同名冲突） ====================
    /** Per-job 存储容量限制上限 */
    public static final String RESOURCES_PER_JOB_STORAGE_LIMIT = PREFIX + ".resources.per_job.storage.limit";
    /** Per-job 存储容量当前使用 */
    public static final String RESOURCES_PER_JOB_STORAGE_USED = PREFIX + ".resources.per_job.storage.used";
    /** Per-job 活跃消费上限 */
    public static final String RESOURCES_PER_JOB_ACTIVE_CONSUMERS_LIMIT =
            PREFIX + ".resources.per_job.active_consumers.limit";

    // ==================== Job 完成判定 Gauges（per-job） ====================
    /** Source 是否已读完（0/1），用于完成判定。 */
    public static final String COMPLETION_SOURCE_FINISHED = PREFIX + ".completion.source_finished";
    /** 推送模式下 in-flight push 数量，用于完成判定。 */
    public static final String COMPLETION_IN_FLIGHT_PUSH = PREFIX + ".completion.in_flight_push";
    /**
     * 活跃消费数（ProgressTracker 的 activeConsumers），用于完成判定。
     * 该值仅统计已拿到消费许可、正在执行回调的条数，不等同于任意背压信号量占用。
     */
    public static final String COMPLETION_ACTIVE_CONSUMERS = PREFIX + ".completion.active_consumers";
    /** 根任务启动时间（秒） */
    public static final String JOB_START_TIME_SECONDS = PREFIX + ".job.start_time_seconds";
    /** 根任务结束时间（秒） */
    public static final String JOB_END_TIME_SECONDS = PREFIX + ".job.end_time_seconds";
    /** 根任务持续时间（秒） */
    public static final String JOB_DURATION_SECONDS = PREFIX + ".job.duration_seconds";
    /** 阶段启动时间（秒） */
    public static final String STAGE_START_TIME_SECONDS = PREFIX + ".stage.start_time_seconds";
    /** 阶段结束时间（秒） */
    public static final String STAGE_END_TIME_SECONDS = PREFIX + ".stage.end_time_seconds";
    /** 阶段持续时间（秒） */
    public static final String STAGE_DURATION_SECONDS = PREFIX + ".stage.duration_seconds";
    private FlowMetricNames() {}
}
