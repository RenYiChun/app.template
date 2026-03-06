package com.lrenyi.template.flow.storage;

import java.util.Optional;
import com.lrenyi.template.flow.context.FlowEntry;

/**
 * 配对上下文：封装存储的原子操作，供 {@link com.lrenyi.template.flow.api.PairingStrategy} 使用。
 * <p>
 * 实现类由存储层在持有 stripe 锁的前提下创建并传入策略，
 * 保证 {@link #getAndRemove(String)} 与 {@link #put(String, FlowEntry)} 的原子性。
 *
 * @param <T> 存储的数据类型
 */
public interface PairingContext<T> {
    
    /**
     * 原子操作：若 key 存在则移除并返回，否则返回 empty。
     * 调用方在 findPartner 返回 empty 时，需调用 {@link #put} 将 incoming 写入。
     *
     * @param key 由 joiner.joinKey 得到的聚合键
     * @return 若存在则返回被移除的条目，否则 empty
     */
    Optional<FlowEntry<T>> getAndRemove(String key);
    
    /**
     * 将 entry 写入 key。仅在 findPartner 返回 empty 时由存储层调用。
     * 调用时需持有对应 key 的 stripe 锁，保证与 getAndRemove 的原子性。
     *
     * @param key   聚合键
     * @param entry 待写入的条目
     */
    void put(String key, FlowEntry<T> entry);
}
