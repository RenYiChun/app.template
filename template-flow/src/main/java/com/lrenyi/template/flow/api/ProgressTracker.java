package com.lrenyi.template.flow.api;

import com.lrenyi.template.flow.context.FlowProgressSnapshot;

/**
 * 进度追踪器：基于物理位移的流控观测模型
 */
public interface ProgressTracker {

    /**
     * 信号：生产许可已获取 (Permit Acquired)
     * 含义：Source 开始读取数据，系统并发负载 +1
     */
    void onProductionAcquired();

    /**
     * 信号：生产许可已释放 (Permit Released)
     * 含义：数据已成功存入 Storage，生产端压力解除
     * 此时数据进入"驻留状态"
     */
    void onProductionReleased();

    /**
     * 信号：消费许可已获取 (Permit Acquired)
     * 物理含义：数据正式占用全局名额，从"等待处理"转为"正在消费"
     */
    void onConsumerAcquired();

    /**
     * 信号：消费许可已释放 (Permit Released)
     * 含义：回调执行完毕，全局信号量释放，数据彻底离场
     *
     * @param jobId 任务 ID
     */
    void onConsumerReleased(String jobId);

    /**
     * 信号：数据已终结（未经过 consumer executor 的路径，如 REJECT）。
     *
     * @param count 终结条数
     */
    void onTerminated(int count);

    /**
     * 获取当前物理水位快照
     * 包含：生产中、缓存中、回调中（Stuck）、已终结等物理计数
     */
    FlowProgressSnapshot getSnapshot();

    /**
     * 动态更新任务预期总量（用于计算完成率）
     */
    void setTotalExpected(String jobId, long total);

    /**
     * 标记任务输入已截止（Source 读完了）
     */
    void markSourceFinished(String jobId);

    /**
     * 设置用于监控指标标签的 jobId（可读展示名）。
     * 默认 no-op；{@link com.lrenyi.template.flow.internal.DefaultProgressTracker} 会使用此值记录 TERMINATED 等指标。
     */
    default void setMetricJobId(String metricJobId) {
    }

    /**
     * 获取用于监控/日志的展示名。默认返回 null；DefaultProgressTracker 返回 setMetricJobId 设置的值。
     */
    default String getMetricJobId() {
        return null;
    }

    /**
     * 是否已完成。
     */
    boolean isCompleted(boolean showStatus);

    /**
     * 当前是否满足完成条件（只读判定，不产生额外副作用）。
     */
    boolean isCompletionConditionMet();

    /**
     * 生产是否已完成：Source 已截止且所有生产许可已释放。
     * 用于触发非匹配模式下的 completion drain。
     */
    default boolean isProductionComplete() {
        return false;
    }
}
