package com.lrenyi.template.flow.storage;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.context.FlowEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CaffeinePairingContext 单元测试（基于 FlowSlot 槽位模型）
 */
class CaffeinePairingContextTest {
    private Cache<String, FlowSlot<String>> cache;
    private CaffeinePairingContext<String> ctx;
    
    @BeforeEach
    void setUp() {
        cache = Caffeine.newBuilder()
                        .maximumSize(100)
                        .expireAfterWrite(10, TimeUnit.SECONDS)
                        .build();
        TemplateConfigProperties.Flow.PerJob perJob = new TemplateConfigProperties.Flow.PerJob();
        perJob.setMultiValueEnabled(true);
        perJob.setMultiValueMaxPerKey(16);
        ctx = new CaffeinePairingContext<>(cache, 16, perJob, (key, entry, reason) -> {});
    }

    @Test
    void getAndRemoveWhenEmptyReturnsEmpty() {
        Optional<FlowEntry<String>> result = ctx.getAndRemove("k1");
        assertFalse(result.isPresent());
        assertEquals(0, cache.estimatedSize());
    }

    @Test
    void getAndRemoveWhenPresentReturnsAndRemoves() {
        FlowEntry<String> entry = new FlowEntry<>("data", "job-1");
        FlowSlot<String> slot = new FlowSlot<>(16, TemplateConfigProperties.Flow.MultiValueOverflowPolicy.DROP_OLDEST);
        slot.append(entry);
        cache.put("k1", slot);

        Optional<FlowEntry<String>> result = ctx.getAndRemove("k1");

        assertTrue(result.isPresent());
        assertEquals("data", result.get().getData());
        assertNull(cache.getIfPresent("k1"));
    }
    
    @Test
    void putStoresEntry() {
        FlowEntry<String> entry = new FlowEntry<>("data", "job-1");
        ctx.put("k1", entry);

        FlowSlot<String> slot = cache.getIfPresent("k1");
        assertNotNull(slot);
        FlowEntry<String> got = slot.peek().orElse(null);
        assertNotNull(got);
        assertEquals("data", got.getData());
    }
    
    @Test
    void getAndRemoveThenPutRoundtrip() {
        FlowEntry<String> first = new FlowEntry<>("first", "job-1");
        ctx.put("k1", first);
        
        Optional<FlowEntry<String>> removed = ctx.getAndRemove("k1");
        assertTrue(removed.isPresent());
        assertEquals("first", removed.get().getData());
        
        FlowEntry<String> second = new FlowEntry<>("second", "job-1");
        ctx.put("k1", second);
        
        Optional<FlowEntry<String>> removed2 = ctx.getAndRemove("k1");
        assertTrue(removed2.isPresent());
        assertEquals("second", removed2.get().getData());
    }
    
    @Test
    void putBackPartnerAtEndAppendsToSlot() {
        FlowEntry<String> a = new FlowEntry<>("a", "job-1");
        FlowEntry<String> b = new FlowEntry<>("b", "job-1");
        ctx.put("k1", a);
        ctx.put("k1", b);
        
        Optional<FlowEntry<String>> first = ctx.getAndRemove("k1");
        assertTrue(first.isPresent());
        assertEquals("a", first.get().getData());
        
        ctx.putBackPartnerAtEnd("k1", first.get());
        
        Optional<FlowEntry<String>> second = ctx.getAndRemove("k1");
        assertTrue(second.isPresent());
        assertEquals("b", second.get().getData());
        
        Optional<FlowEntry<String>> third = ctx.getAndRemove("k1");
        assertTrue(third.isPresent());
        assertEquals("a", third.get().getData());
    }
}
