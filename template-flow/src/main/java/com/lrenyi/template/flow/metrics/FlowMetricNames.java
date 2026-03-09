package com.lrenyi.template.flow.metrics;

/**
 * Flow 引擎指标名称与标签常量。
 * <p>
 * 命名规范：{@code app.template.flow.{category}.{name}}，
 * Prometheus 导出时 '.' 转为 '_'，Counter 自动追加 {@code _total}。
 */
public final class FlowMetricNames {
    public static final String PREFIX = "app.template.flow";
    /** Job 启动次数。每次 FlowJoinerEngine.run() 调用时 +1。高：任务触发活跃 */
    public static final String JOB_STARTED = PREFIX + ".job.started";
    
    // ==================== Counters ====================
    /** Job 正常完成次数。run() 正常返回时 +1。completed/started 即任务成功率 */
    public static final String JOB_COMPLETED = PREFIX + ".job.completed";
    /** Job 被手动停止次数。高：频繁人工干预，正常应接近 0 */
    public static final String JOB_STOPPED = PREFIX + ".job.stopped";
    /** 已获取生产许可的数据条数（进场量）。高：数据源产出快 */
    public static final String PRODUCTION_ACQUIRED = PREFIX + ".production.acquired";
    /** 已成功存入 Storage 的数据条数（入库量）。acquired - released = 在途生产中 */
    public static final String PRODUCTION_RELEASED = PREFIX + ".production.released";
    /** 主动出口累计数（业务达成量）。onPairConsumed 或 onSingleConsumed(SINGLE_CONSUMED) 时 +1 */
    public static final String EGRESS_ACTIVE = PREFIX + ".egress.active";
    /**
     * 被动出口累计数（损耗量），按 reason 标签区分原因。
     * <p>reason 值域：TIMEOUT / EVICTION / REPLACE / MISMATCH / REJECT / SHUTDOWN / CLEARED_AFTER_PAIR_SUCCESS /
     * OVERFLOW_DROP_* 等。
     * {@code passive / (active + passive)} = 损耗率，高于 10% 需关注。
     */
    public static final String EGRESS_PASSIVE = PREFIX + ".egress.passive";
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
    /** 单条数据存入 Storage 的耗时。高：Storage 写入瓶颈（锁争用/队列满） */
    public static final String DEPOSIT_DURATION = PREFIX + ".deposit.duration";
    
    // ==================== Timers ====================
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
    /** 背压等待总耗时。生产者因缓存满/许可耗尽阻塞。高：生产远超消费，系统过载 */
    public static final String BACKPRESSURE_DURATION = PREFIX + ".backpressure.duration";
    
    // ==================== Gauges ====================
    /** 当前正在运行的 Job 数量。为 0 时系统空闲 */
    public static final String LAUNCHERS_ACTIVE = PREFIX + ".launchers.active";
    
    // ==================== limits 维度指标（OOM 控制） ====================
    public static final String LIMITS_PREFIX = PREFIX + ".limits";
    /** 各维度许可获取等待耗时（按 dimension 标签区分） */
    public static final String LIMITS_ACQUIRE_WAIT_DURATION = LIMITS_PREFIX + ".acquire-wait-duration";
    /** 生产线程：每 Job 已占用 */
    public static final String LIMITS_PRODUCER_THREADS_USED = LIMITS_PREFIX + ".producer-threads.used";
    /** 生产线程：每 Job 上限 */
    public static final String LIMITS_PRODUCER_THREADS_LIMIT = LIMITS_PREFIX + ".producer-threads.limit";
    /** 生产线程：全主机已占用 */
    public static final String LIMITS_PRODUCER_THREADS_GLOBAL_USED = LIMITS_PREFIX + ".producer-threads.global.used";
    /** 生产线程：全主机上限 */
    public static final String LIMITS_PRODUCER_THREADS_GLOBAL_LIMIT = LIMITS_PREFIX + ".producer-threads.global.limit";
    /** 在途数据量：每 Job 已占用 */
    public static final String LIMITS_IN_FLIGHT_USED = LIMITS_PREFIX + ".in-flight.used";
    /** 在途数据量：每 Job 上限 */
    public static final String LIMITS_IN_FLIGHT_LIMIT = LIMITS_PREFIX + ".in-flight.limit";
    /** 在途数据量：全主机已占用 */
    public static final String LIMITS_IN_FLIGHT_GLOBAL_USED = LIMITS_PREFIX + ".in-flight.global.used";
    /** 在途数据量：全主机上限 */
    public static final String LIMITS_IN_FLIGHT_GLOBAL_LIMIT = LIMITS_PREFIX + ".in-flight.global.limit";
    /** 消费并发数：每 Job 已占用 */
    public static final String LIMITS_CONSUMER_CONCURRENCY_USED = LIMITS_PREFIX + ".consumer-concurrency.used";
    /** 消费并发数：每 Job 上限 */
    public static final String LIMITS_CONSUMER_CONCURRENCY_LIMIT = LIMITS_PREFIX + ".consumer-concurrency.limit";
    /** 消费并发数：全主机已占用 */
    public static final String LIMITS_CONSUMER_CONCURRENCY_GLOBAL_USED =
            LIMITS_PREFIX + ".consumer-concurrency.global.used";
    /** 消费并发数：全主机上限 */
    public static final String LIMITS_CONSUMER_CONCURRENCY_GLOBAL_LIMIT =
            LIMITS_PREFIX + ".consumer-concurrency.global.limit";
    /** 等待消费许可：每 Job 已离库未终结条数 */
    public static final String LIMITS_PENDING_CONSUMER_COUNT = LIMITS_PREFIX + ".pending-consumer.count";
    /** 等待消费许可：每 Job 背压阈值 */
    public static final String LIMITS_PENDING_CONSUMER_LIMIT = LIMITS_PREFIX + ".pending-consumer.limit";
    /** 等待消费许可：全主机已离库未终结条数 */
    public static final String LIMITS_PENDING_CONSUMER_GLOBAL_COUNT = LIMITS_PREFIX + ".pending-consumer.global.count";
    /** 等待消费许可：全主机背压阈值 */
    public static final String LIMITS_PENDING_CONSUMER_GLOBAL_LIMIT = LIMITS_PREFIX + ".pending-consumer.global.limit";
    /** 存储容量：每 Job 当前条数 */
    public static final String LIMITS_STORAGE_USED = LIMITS_PREFIX + ".storage.used";
    /** 存储容量：每 Job 容量上限 */
    public static final String LIMITS_STORAGE_LIMIT = LIMITS_PREFIX + ".storage.limit";
    /** 存储容量：全主机当前条数 */
    public static final String LIMITS_STORAGE_GLOBAL_USED = LIMITS_PREFIX + ".storage.global.used";
    /** 存储容量：全主机容量上限 */
    public static final String LIMITS_STORAGE_GLOBAL_LIMIT = LIMITS_PREFIX + ".storage.global.limit";
    /** 多值模式：丢弃/覆盖计数，reason 标签 overflow_drop_oldest / overflow_drop_newest */
    public static final String STORAGE_MULTI_VALUE_DISCARD_TOTAL = PREFIX + ".storage.multi-value.discard.total";
    /** 配对重入：尝试次数 */
    public static final String MATCH_RETRY_ATTEMPTED = PREFIX + ".match.retry.attempted";
    /** 配对重入：成功回灌次数 */
    public static final String MATCH_RETRY_SUCCEEDED = PREFIX + ".match.retry.succeeded";
    /** 配对重入：耗尽次数 */
    public static final String MATCH_RETRY_EXHAUSTED = PREFIX + ".match.retry.exhausted";
    /** 任务标识。注意高基数风险，仅在任务数可控时使用 */
    public static final String TAG_JOB_ID = "jobId";
    
    // ==================== Tags ====================
    /** 错误类型。如 job_failed / deposit_failed / onConsume_failed 等 */
    public static final String TAG_ERROR_TYPE = "errorType";
    /** 错误发生阶段。PRODUCTION / STORAGE / CONSUMPTION / FINALIZATION */
    public static final String TAG_PHASE = "phase";
    /** 被动出口原因。TIMEOUT / EVICTION / REPLACE / MISMATCH / REJECT / SHUTDOWN */
    public static final String TAG_REASON = "reason";
    /** 存储引擎类型。caffeine / queue */
    public static final String TAG_STORAGE_TYPE = "storageType";
    /** 许可维度。producer-threads / in-flight / storage / consumer-concurrency */
    public static final String TAG_DIMENSION = "dimension";
    /** 许可维度值：生产线程 */
    public static final String DIMENSION_PRODUCER_THREADS = "producer-threads";
    /** 许可维度值：在途数据 */
    public static final String DIMENSION_IN_FLIGHT = "in-flight";
    /** 许可维度值：存储容量 */
    public static final String DIMENSION_STORAGE = "storage";
    /** 许可维度值：消费并发 */
    public static final String DIMENSION_CONSUMER_CONCURRENCY = "consumer-concurrency";
    
    private FlowMetricNames() {}
}
