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
    /** 主动出口累计数（业务达成量）。配对成功 onSuccess 或 Finalizer onConsume 时 +1 */
    public static final String EGRESS_ACTIVE = PREFIX + ".egress.active";
    /**
     * 被动出口累计数（损耗量），按 reason 标签区分原因。
     * <p>reason 值域：
     * <ul>
     *   <li>TIMEOUT — TTL 过期，数据未等到配对即超时</li>
     *   <li>EVICTION — 容量淘汰，maxSize 满 LRU 策略踢出</li>
     *   <li>REPLACE — 覆盖模式下同 Key 新数据顶替旧数据</li>
     *   <li>MISMATCH — 配对模式下 isMatched() 返回 false</li>
     *   <li>REJECT — Queue 满，新数据被拒绝入队</li>
     *   <li>SHUTDOWN — 系统关闭时缓存中残留的未处理数据</li>
     * </ul>
     * {@code passive / (active + passive)} = 损耗率，高于 10% 需关注。
     */
    public static final String EGRESS_PASSIVE = PREFIX + ".egress.passive";
    /** 物理终结累计数。数据彻底离场、信号量释放时 +1。{@code rate(terminated[1m])} 即 TPS */
    public static final String TERMINATED = PREFIX + ".terminated";
    /**
     * 统一错误计数器，按 errorType + phase 维度聚合。
     * <p>errorType 值域：job_failed / deposit_failed / onConsume_failed / match_process_failed 等。
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
     * 高：消费端饱和导致 acquire 久，或 onSuccess 回调慢。
     */
    public static final String MATCH_DURATION = PREFIX + ".match.duration";
    /** 获取全局消费信号量的耗时。高：消费端资源争用严重 */
    public static final String ACQUIRE_DURATION = PREFIX + ".acquire.duration";
    /**
     * 终结处理端到端耗时（含排队等待），从 submitBodyOnly 入口到 onConsume 完成。
     * 高：消费执行器积压或 onConsume 回调慢。
     */
    public static final String FINALIZE_DURATION = PREFIX + ".finalize.duration";
    /** 背压等待总耗时。生产者因缓存满/许可耗尽阻塞。高：生产远超消费，系统过载 */
    public static final String BACKPRESSURE_DURATION = PREFIX + ".backpressure.duration";
    /** 全局消费信号量已占用许可数。高（接近 limit）：消费端饱和 */
    public static final String SEMAPHORE_USED = PREFIX + ".semaphore.used";
    
    // ==================== Gauges ====================
    /** 全局消费信号量上限（配置值）。用于计算利用率 = used / limit */
    public static final String SEMAPHORE_LIMIT = PREFIX + ".semaphore.limit";
    /** 当前正在运行的 Job 数量。为 0 时系统空闲 */
    public static final String LAUNCHERS_ACTIVE = PREFIX + ".launchers.active";
    /** 当前 Storage 中缓存的数据条数。高（接近 maxSize）：数据堆积，即将触发驱逐或背压 */
    public static final String STORAGE_SIZE = PREFIX + ".storage.size";
    /** 任务完成率 = terminated / totalExpected。仅 totalExpected &gt; 0 时注册 */
    public static final String COMPLETION_RATE = PREFIX + ".completion.rate";
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
    private FlowMetricNames() {}
}
