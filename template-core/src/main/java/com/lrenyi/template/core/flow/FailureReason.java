package com.lrenyi.template.core.flow;

/**
 * 被动出口/失败原因枚举。
 * 用于 {@link FlowJoiner#onFailed} 和进度/指标按原因统计。
 */
public enum FailureReason {
    /**
     * 缓存等待配对超时（TTL 到期）
     */
    TIMEOUT,
    /**
     * 容量驱逐（maxSize 满，LRU 等策略踢出）
     */
    EVICTION,
    /**
     * Key 冲突替换（非配对模式下新顶旧）
     */
    REPLACE,
    /**
     * 配对逻辑不匹配（isMatched 返回 false）
     */
    MISMATCH,
    /**
     * 任务拒绝准入（背压/过载等）
     */
    REJECT,
    /**
     * 系统关闭时残留未处理数据
     */
    SHUTDOWN,
    /**
     * 未知或未分类
     */
    UNKNOWN
}
