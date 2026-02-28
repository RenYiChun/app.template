package com.lrenyi.template.flow.context;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
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
                                   long endTimeMillis,      // 10.任务结束时间（未结束为 0）
                                   Map<String, Long> passiveEgressByReason  // 11. 按失败原因统计的被动出口数（可选）
) implements Serializable {
    
    /**
     * 兼容构造：passiveEgressByReason 为 null 时视为空 Map
     */
    public FlowProgressSnapshot {
        if (passiveEgressByReason == null) {
            passiveEgressByReason = Collections.emptyMap();
        } else {
            passiveEgressByReason = Collections.unmodifiableMap(passiveEgressByReason);
        }
    }
    
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
        return Math.max(0, activeConsumers);
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
     * 按失败原因获取被动出口数量
     *
     * @param reason 失败原因枚举名（如 TIMEOUT、EVICTION）
     * @return 该原因的累计数量，未统计时返回 0
     */
    public long getPassiveEgressByReason(String reason) {
        if (reason == null || passiveEgressByReason.isEmpty()) {
            return 0L;
        }
        return passiveEgressByReason.getOrDefault(reason, 0L);
    }
    
    /**
     * 待消费数（已离库未终结）：productionReleased - inStorage - activeConsumers - terminated。
     * 这些条数已从缓存离开（驱逐/配对等），在 removal 回调或消费队列中等待/占用许可，尚未调用 onGlobalTerminated。
     */
    public long getPendingConsumerCount() {
        long gap = productionReleased - inStorage - activeConsumers - terminated;
        return Math.max(0, gap);
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