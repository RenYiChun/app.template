package com.lrenyi.template.flow.api;

import java.util.OptionalLong;
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.model.FlowStorageType;

/**
 * T: 数据项类型 (Terminal/Task item)
 */
public interface FlowJoiner<T> {
    
    /**
     * 定义该业务逻辑使用的缓存/存储类型。
     * 实现类需显式返回，例如：return FlowStorageType.CAFFEINE;
     */
    default FlowStorageType getStorageType() {
        return FlowStorageType.LOCAL_BOUNDED; // 默认使用受控超时的本地存储
    }

    /**
     * 可选：覆盖该阶段「存储消费节拍」间隔（毫秒）。
     * <ul>
     *   <li>{@link com.lrenyi.template.flow.model.FlowStorageType#QUEUE}：队列出队/轮询间隔</li>
     *   <li>{@link com.lrenyi.template.flow.model.FlowStorageType#LOCAL_BOUNDED}：驱逐协调扫描间隔（与全局/每 Job 配置二选一，见实现）</li>
     * </ul>
     * 无值时沿用 {@link com.lrenyi.template.core.TemplateConfigProperties.Flow.Limits.PerJob} 与 Global 中的有效值。
     */
    default OptionalLong storageConsumerTickIntervalMillis() {
        return OptionalLong.empty();
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
     * 配对消费：当两个具有相同 key 的数据匹配成功时触发。
     * needMatched=false 时可 default 空实现。
     *
     * @param existing 先到达的数据（从缓存中取出）
     * @param incoming 后到达的数据（触发聚合的当前项）
     * @param jobId   任务 ID
     */
    void onPairConsumed(T existing, T incoming, String jobId);
    
    /**
     * 单条消费：数据以给定原因离开/消费时触发，带消费原因。
     * 必实现；业务可按 reason 区分（如 SINGLE_CONSUMED 为正常消费，其余为损耗等）。
     *
     * @param item   数据项
     * @param jobId  任务 ID
     * @param reason 消费原因（出口原因）
     */
    void onSingleConsumed(T item, String jobId, EgressReason reason);
    
    /**
     * 只有双流对齐任务才需要覆写此方法为 true。
     */
    default boolean needMatched() {
        return false;
    }
    
    /**
     * 匹配校验：仅在 needMatched 为 true 时有效。
     * 评估两个数据项是否满足配对业务条件
     * * @param existing 已经在缓存中等待的数据
     *
     * @param incoming 刚刚到达的数据
     *
     * @return 如果满足配对条件返回 true，否则返回 false
     */
    default boolean isMatched(T existing, T incoming) {
        return true;
    }
    
    default boolean isRetryable(T item, String jobId) {
        return false;
    }
}
