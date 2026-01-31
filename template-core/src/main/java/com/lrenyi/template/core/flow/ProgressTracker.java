package com.lrenyi.template.core.flow;

import com.lrenyi.template.core.flow.context.FlowProgressSnapshot;
import java.util.concurrent.CompletableFuture;

/**
 * 进度追踪器：基于物理位移的流控观测模型
 */
public interface ProgressTracker {
    
    // ==========================================
    // 第一阶段：准入（Admission）- 生产许可控制
    // ==========================================
    
    /**
     * 信号：生产许可已获取 (Permit Acquired)
     * 含义：Source 开始读取数据，系统并发负载 +1
     */
    void onProductionAcquired();
    
    /**
     * 信号：生产许可已释放 (Permit Released)
     * 含义：数据已成功存入 Storage，生产端压力解除
     * 此时数据进入“驻留状态”
     */
    void onProductionReleased();
    
    
    // ==========================================
    // 第二阶段：流转（Flowing）- 消费路径观测
    // ==========================================
    
    /**
     * 信号：消费准入（数据正式占用全局名额）
     * 物理含义：数据从“等待处理”转为“正在消费”
     */
    void onConsumerBegin();
    
    /**
     * 信号：主动出口触发 (Active Egress)
     * 含义：数据通过业务接口（如 onSuccess/onConsume）主动离库
     * 框架通过此信号自动计算“有效流转率”
     */
    void onActiveEgress();
    
    /**
     * 信号：被动出口触发 (Passive Egress)，带失败原因
     * 含义：数据通过框架策略（如 TTL 过期、驱逐、替换、不匹配等）离库
     * 框架通过此信号计算损耗率并按原因统计
     *
     * @param reason 失败原因，用于 Snapshot/指标按原因统计
     */
    default void onPassiveEgress(FailureReason reason) {
        onPassiveEgress();
    }
    
    /**
     * 信号：被动出口触发 (Passive Egress)，无原因（兼容）
     * 框架内部会调用 {@link #onPassiveEgress(FailureReason)}，未覆写带原因版本时由此兜底。
     */
    void onPassiveEgress();
    
    
    // ==========================================
    // 第三阶段：终结（Termination）- 消费许可控制
    // ==========================================
    
    /**
     * 信号：消费许可已释放 (JobGlobal Release)
     * 含义：回调执行完毕，全局信号量释放，数据彻底离场
     */
    void onGlobalTerminated(String jobId);
    
    
    // ==========================================
    // 状态查询与快照
    // ==========================================
    
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
     * 任务完成的 Future。
     * 完成触发条件：markSourceFinished 已调用 且 当前活跃许可(Consumer)归零。
     */
    CompletableFuture<Void> getCompletionFuture();
}