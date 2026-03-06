package com.lrenyi.template.flow.storage;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import com.github.benmanes.caffeine.cache.Cache;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.model.FailureReason;

/**
 * 基于 Caffeine Cache + FlowSlot 的配对上下文实现。
 * 由 {@link CaffeineFlowStorage} 在持有 stripe 锁的前提下创建并传入策略使用。
 *
 * @param <T> 存储的数据类型
 */
final class CaffeinePairingContext<T> implements PairingContext<T> {
    private final Cache<String, FlowSlot<T>> cache;
    private final int maxPerKey;
    private final TemplateConfigProperties.Flow.PerJob perJob;
    private final BiConsumer<FlowEntry<T>, FailureReason> overflowHandler;
    
    CaffeinePairingContext(Cache<String, FlowSlot<T>> cache,
            int maxPerKey,
            TemplateConfigProperties.Flow.PerJob perJob,
            BiConsumer<FlowEntry<T>, FailureReason> overflowHandler) {
        this.cache = cache;
        this.maxPerKey = maxPerKey;
        this.perJob = perJob;
        this.overflowHandler = overflowHandler;
    }
    
    @Override
    public Optional<FlowEntry<T>> getAndRemove(String key) {
        AtomicReference<FlowEntry<T>> polled = new AtomicReference<>();
        cache.asMap().computeIfPresent(key, (k, slot) -> {
            polled.set(slot.poll().orElse(null));
            return slot.isEmpty() ? null : slot;
        });
        return Optional.ofNullable(polled.get());
    }
    
    @Override
    public void put(String key, FlowEntry<T> entry) {
        BiFunction<String, FlowSlot<T>, FlowSlot<T>> slotBiFunction = (k, existing) -> {
            FlowSlot<T> slot = existing != null ? existing : createSlot();
            Optional<FlowSlot.OverflowResult<T>> overflow = slot.append(entry);
            overflow.ifPresent(r -> overflowHandler.accept(r.entry(), r.reason()));
            return slot.isEmpty() ? null : slot;
        };
        cache.asMap().compute(key, slotBiFunction);
    }
    
    void putBackPartnerAtEnd(String key, FlowEntry<T> partner) {
        BiFunction<String, FlowSlot<T>, FlowSlot<T>> slotBiFunction = (k, existing) -> {
            FlowSlot<T> slot = existing != null ? existing : createSlot();
            slot.offerLast(partner);
            return slot;
        };
        cache.asMap().compute(key, slotBiFunction);
    }
    
    private FlowSlot<T> createSlot() {
        return new FlowSlot<>(maxPerKey, perJob.getMultiValueOverflowPolicy());
    }
}
