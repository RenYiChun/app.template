package com.lrenyi.template.flow.api;

import java.util.Optional;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.storage.PairingContext;

/**
 * 配对策略：定义如何从存储中查找并移除配对对象。
 * <p>
 * 仅当 {@link FlowJoiner#needMatched()} 为 true 时生效。
 * 默认实现为 key 等值 1:1 查找；业务可覆写 {@link FlowJoiner#getPairingStrategy()} 以实现多 key、多候选等语义。
 *
 * @param <T> 存储的数据类型
 */
public interface PairingStrategy<T> {
    
    /**
     * 从存储中查找并移除配对对象。
     *
     * @param key     由 joiner.joinKey(incoming.getData()) 得到的主键
     * @param incoming 当前到达的条目
     * @param ctx     配对上下文，提供存储的原子操作
     * @return 若找到配对对象则返回 Optional.of(partner)，否则返回 empty（incoming 将由存储层存入缓存等待）
     */
    Optional<FlowEntry<T>> findPartner(String key, FlowEntry<T> incoming, PairingContext<T> ctx);
}
