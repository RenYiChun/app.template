package com.lrenyi.template.flow.context;

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
                                   long terminated,         // 6. 物理终结累计数（许可彻底释放）
                                   long startTimeMillis,    // 7. 任务启动时间
                                   long endTimeMillis       // 8. 任务结束时间（未结束为 0）
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
     * 准入效率：
     * 反映数据从 Source 读入并塞进 Storage 的速度
     */
    public long getInProductionCount() {
        return productionAcquired - productionReleased;
    }
    
    /**
     * 待消费数（已离库未终结）：productionReleased - inStorage - activeConsumers - terminated。
     * 这些条数已从缓存离开（驱逐/配对等），在 removal 回调或消费队列中等待/占用许可，尚未调用 onConsumerReleased。
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