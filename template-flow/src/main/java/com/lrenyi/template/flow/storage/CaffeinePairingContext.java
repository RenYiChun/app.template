package com.lrenyi.template.flow.storage;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import com.github.benmanes.caffeine.cache.Cache;
import com.lrenyi.template.flow.context.FlowEntry;

/**
 * 基于 Caffeine Cache 的配对上下文实现。
 * 由 {@link CaffeineFlowStorage} 在持有 stripe 锁的前提下创建并传入策略使用。
 *
 * @param <T> 存储的数据类型
 */
final class CaffeinePairingContext<T> implements PairingContext<T> {
    private final Cache<String, FlowEntry<T>> cache;
    
    CaffeinePairingContext(Cache<String, FlowEntry<T>> cache) {
        this.cache = cache;
    }
    
    @Override
    public Optional<FlowEntry<T>> getAndRemove(String key) {
        AtomicReference<FlowEntry<T>> removed = new AtomicReference<>();
        cache.asMap().computeIfPresent(key, (k, existing) -> {
            removed.set(existing);
            return null;
        });
        return Optional.ofNullable(removed.get());
    }
    
    @Override
    public void put(String key, FlowEntry<T> entry) {
        cache.asMap().put(key, entry);
    }
}
