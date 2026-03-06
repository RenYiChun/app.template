package com.lrenyi.template.flow.storage;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lrenyi.template.flow.context.FlowEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CaffeinePairingContext 单元测试
 */
class CaffeinePairingContextTest {
    private Cache<String, FlowEntry<String>> cache;
    private CaffeinePairingContext<String> ctx;
    
    @BeforeEach
    void setUp() {
        cache = Caffeine.newBuilder()
                        .maximumSize(100)
                        .expireAfterWrite(10, TimeUnit.SECONDS)
                        .build();
        ctx = new CaffeinePairingContext<>(cache);
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
        cache.put("k1", entry);
        
        Optional<FlowEntry<String>> result = ctx.getAndRemove("k1");
        
        assertTrue(result.isPresent());
        assertEquals("data", result.get().getData());
        assertFalse(cache.getIfPresent("k1") != null);
    }
    
    @Test
    void putStoresEntry() {
        FlowEntry<String> entry = new FlowEntry<>("data", "job-1");
        ctx.put("k1", entry);
        
        FlowEntry<String> got = cache.getIfPresent("k1");
        assertTrue(got != null);
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
}
