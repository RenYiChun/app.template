package com.lrenyi.template.core.flow.context;

import java.io.Serializable;
import lombok.Builder;

/**
 * 流量进度快照：基于物理水位观测模型
 */
@Builder
public record FlowProgressSnapshot(String jobId, long totalExpected, // 1. 预期总量
                                   long productionAcquired, // 2. 已获取生产许可总数
                                   long productionReleased, // 3. 已释放生产许可总数（已入库总数）
                                   long activeConsumers,    // 4. 当前占用的全局消费许可数（正在系统内流转的总量）
                                   long inStorage,          // 5. 当前缓存在存储引擎中的数量
                                   long activeEgress,       // 6. 主动出口累计数（业务达成）
                                   long passiveEgress,      // 7. 被动出口累计数（超时/驱逐/损耗）
                                   long terminated,         // 8. 物理终结累计数（许可彻底释放）
                                   long startTimeMillis,    // 9. 任务启动时间
                                   long endTimeMillis       // 10.任务结束时间（未结束为 0）
) implements Serializable {
    
    // ==========================================
    // 衍生诊断指标（由框架自动推导）
    // ==========================================
    
    /**
     * 物理完成率：反映任务离收工还有多远。
     * - 已知总量（totalExpected &gt; 0）：terminated / totalExpected；
     * - 未知总量/推送模式（totalExpected &lt;= 0）：任务已结束则 100%，否则 terminated / productionAcquired（按已生产量估算）。
     */
    public double getCompletionRate() {
        if (totalExpected > 0) {
            return Math.min(1.0, (double) terminated / totalExpected);
        }
        if (endTimeMillis > 0) {
            return 1.0;
        }
        if (productionAcquired <= 0) {
            return 0.0;
        }
        return Math.min(1.0, (double) terminated / productionAcquired);
    }
    
    /**
     * 幽灵积压数 (Stuck Count)：
     * 已经从缓存中出来，但还没有彻底终结的数据。
     * 反映了回调逻辑（onSuccess/onFailed）或线程池的阻塞情况。
     */
    public long getStuckCount() {
        // activeConsumers 代表所有进入系统生命周期的数据（准入计数）
        // inStorage 代表还在仓库里的
        // 剩下的就是：(正在排队抢 20万 票的) + (已经抢到票正在跑回调的)
        return Math.max(0, activeConsumers - inStorage);
    }
    
    /**
     * 准入效率：
     * 反映数据从 Source 读入并塞进 Storage 的速度
     */
    public long getInProductionCount() {
        return productionAcquired - productionReleased;
    }
    
    /**
     * 2. 业务成功率 (Success Rate / Health)
     * 表达：在已经处理完的数据中，有多少是业务达成的。
     * 反映系统质量，如果这个值低，说明 TTL 设置太短或处理太慢。
     */
    public double getSuccessRate() {
        long totalTerminated = activeEgress + passiveEgress;
        if (totalTerminated == 0) {
            return 1.0; // 尚未有数据离场时，默认质量是健康的
        }
        return (double) activeEgress / totalTerminated;
    }
    
    /**
     * 平均吞吐量 (TPS)：基于物理终结点的计算
     */
    public double getTps() {
        // 1. 确定计算终点：如果任务已结束，使用结束时间；否则使用当前时间
        long effectiveEndTime = (endTimeMillis > 0) ? endTimeMillis : System.currentTimeMillis();
        long durationMs = effectiveEndTime - startTimeMillis;
        if (durationMs <= 0) {
            return 0.0;
        }
        // 2. 计算 TPS：terminated 代表物理终结的总条数
        return terminated / (durationMs / 1000.0);
    }
}