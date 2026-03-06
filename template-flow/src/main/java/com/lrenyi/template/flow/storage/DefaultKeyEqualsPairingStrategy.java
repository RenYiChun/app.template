package com.lrenyi.template.flow.storage;

import java.util.Optional;
import com.lrenyi.template.flow.api.PairingStrategy;
import com.lrenyi.template.flow.context.FlowEntry;

/**
 * 默认配对策略：同 key 1:1 查找并移除。
 * 与原有 CaffeineFlowStorage 的 findAndRemovePartner 逻辑一致。
 *
 * @param <T> 存储的数据类型
 */
public final class DefaultKeyEqualsPairingStrategy<T> implements PairingStrategy<T> {
    private static final PairingStrategy<?> INSTANCE = new DefaultKeyEqualsPairingStrategy<>();
    
    private DefaultKeyEqualsPairingStrategy() {
    }
    
    @SuppressWarnings("unchecked")
    public static <T> PairingStrategy<T> getInstance() {
        return (PairingStrategy<T>) INSTANCE;
    }
    
    @Override
    public Optional<FlowEntry<T>> findPartner(String key, FlowEntry<T> incoming, PairingContext<T> ctx) {
        return ctx.getAndRemove(key);
    }
}
