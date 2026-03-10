package com.lrenyi.template.flow.api;

import com.lrenyi.template.flow.context.FlowProgressSnapshot;
import com.lrenyi.template.flow.model.EgressReason;

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
     * 信号：主动出口触发 (Active Egress)
     * 含义：数据通过业务接口（如 onSuccess/onConsume）主动离库
     * 框架通过此信号自动计算"有效流转率"
     */
    void onActiveEgress();
    
    /**
     * 信号：被动出口触发 (Passive Egress)，带失败原因
     * 含义：数据通过框架策略（如 TTL 过期、驱逐、替换、不匹配等）离库
     * 框架通过此信号计算损耗率并按原因统计
     *
     * @param reason 失败原因，用于 Snapshot/指标按原因统计
     */
    void onPassiveEgress(EgressReason reason);
    
    
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
     * 是否已完成。
     */
    boolean isCompleted();
    
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
    
    /**
     * 信号：Job 已启动。Launcher 注册成功后由 FlowManager 调用，用于打点 JOB_STARTED。
     */
    default void onJobStarted() {
    }
    
    /**
     * 信号：Job 被手动停止。由 FlowManager 在 stopJob 时调用，用于打点 JOB_STOPPED。
     */
    default void onJobStopped() {
    }
    
    /**
     * 信号：Finalizer 获取 pending slot 超时。用于打点 FINALIZER_PENDING_SLOT_ACQUIRE_TIMEOUT。
     */
    default void onFinalizerPendingSlotTimeout() {
    }
    
    /**
     * 信号：Finalizer 因严格 pending 模式跳过提交。用于打点 FINALIZER_SUBMIT_SKIPPED。
     */
    default void onFinalizerSubmitSkipped() {
    }
}
