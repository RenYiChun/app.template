package com.lrenyi.template.flow.model;

/**
 * 消费数据的出口原因，覆盖所有消费场景。
 * PAIR_MATCHED 对应 onPairConsumed；SINGLE_CONSUMED 对应单条正常消费；其余对应 onSingleConsumed(reason)。
 */
public enum EgressReason {
    /**
     * 配对成功，走 onPairConsumed（主动）
     */
    PAIR_MATCHED,
    /**
     * 单条正常消费，走 onSingleConsumed（主动）
     */
    SINGLE_CONSUMED,
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
     * 多值模式超限：淘汰最老项
     */
    OVERFLOW_DROP_OLDEST,
    /**
     * 多值模式超限：丢弃新入项
     */
    OVERFLOW_DROP_NEWEST,
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
     * 配对成功后清空剩余（多对匹配关闭时，槽位内未匹配条目被主动驱逐）
     */
    CLEARED_AFTER_PAIR_SUCCESS,
    /**
     * 未知或未分类
     */
    UNKNOWN
}
