package com.lrenyi.template.core.flow;

import com.lrenyi.template.core.flow.config.FlowStorageType;
import com.lrenyi.template.core.flow.source.FlowSourceProvider;

/**
 * T: 数据项类型 (Terminal/Task item)
 */
public interface FlowJoiner<T> {

    /**
     * 定义该业务逻辑使用的缓存/存储类型。
     * 实现类需显式返回，例如：return FlowStorageType.CAFFEINE;
     */
    default FlowStorageType getStorageType() {
        return FlowStorageType.CAFFEINE; // 默认使用高频的本地缓存
    }

    /**
     * 返回数据的具体类型。
     * 业务实现类需要显式返回，例如：return LogItem.class;
     */
    Class<T> getDataType();

    /**
     * 数据源提供者：产出多个子流（FlowSource），每个子流对应一个并发单元。
     * 可由 FlowSourceAdapters.fromStreams(Stream&lt;Stream&lt;T&gt;&gt;) 等适配得到。
     */
    FlowSourceProvider<T> sourceProvider();

    /**
     * 关联键：定义数据聚合的唯一标识
     */
    String joinKey(T item);
    
    /**
     * 聚合成功：当两个具有相同 key 的数据相遇时触发
     *
     * @param existing 先到达的数据（从缓存中取出）
     * @param incoming 后到达的数据（触发聚合的当前项）
     */
    void onSuccess(T existing, T incoming, String jobId);
    
    /**
     * 简单消费钩子：仅在非配对场景下被 onSuccess 默认调用
     */
    default void onConsume(T item, String jobId) {
        // 默认空实现或抛错，由子类选择性覆写
    }
    
    /**
     * 只有双流对齐任务才需要覆写此方法为 true。
     */
    default boolean needMatched() {return false;}
    
    /**
     * 匹配校验：仅在 needMatched 为 true 时有效。
     * 评估两个数据项是否满足配对业务条件
     * * @param existing 已经在缓存中等待的数据
     *
     * @param incoming 刚刚到达的数据
     *
     * @return 如果满足配对条件返回 true，否则返回 false
     */
    default boolean isMatched(T existing, T incoming) {return true;}
    
    /**
     * 孤立数据/失败数据处理出口。
     * * 此方法是数据生命周期的【保底归宿】，以下情形会触发：
     * 1. 【超时】：needMatched=true 时，数据在缓存中等待配对超时。
     * 2. 【容量驱逐】：缓存达到 maxSize 或队列达到 capacity，导致旧数据被踢出。
     * 3. 【冲突丢弃】：非配对模式下，Key 冲突导致旧数据被 Replace。
     * 4. 【逻辑不匹配】：isMatched 返回 false，两个 Entry 均会被视为孤立项处理。
     * 5. 【系统过载】：任务拒绝准入（Rejected）时。
     * 6. 【系统关闭】：Shutdown 时，存储内残留的未处理数据。
     * * @param item  未完成流程的数据项。
     *
     * @param jobId 所属任务ID。
     */
    void onFailed(T item, String jobId);
}