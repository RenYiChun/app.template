package com.lrenyi.template.flow.storage;

import java.util.Optional;
import com.lrenyi.template.flow.api.PairingStrategy;
import com.lrenyi.template.flow.context.FlowEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 默认配对策略单元测试
 */
class DefaultKeyEqualsPairingStrategyTest {
    
    @Test
    void getInstanceReturnsSingleton() {
        PairingStrategy<String> a = DefaultKeyEqualsPairingStrategy.getInstance();
        PairingStrategy<String> b = DefaultKeyEqualsPairingStrategy.getInstance();
        assertSame(a, b);
    }
    
    @Test
    void findPartnerWhenContextHasExistingReturnsIt() {
        PairingStrategy<String> strategy = DefaultKeyEqualsPairingStrategy.getInstance();
        FlowEntry<String> existing = new FlowEntry<>("existing", "job-1");
        
        StubPairingContext ctx = new StubPairingContext();
        ctx.put("k1", existing);
        
        FlowEntry<String> incoming = new FlowEntry<>("incoming", "job-1");
        Optional<FlowEntry<String>> result = strategy.findPartner("k1", incoming, ctx);
        
        assertTrue(result.isPresent());
        assertEquals("existing", result.get().getData());
        assertTrue(ctx.wasGetAndRemoveCalled());
    }
    
    @Test
    void findPartnerWhenContextEmptyReturnsEmpty() {
        PairingStrategy<String> strategy = DefaultKeyEqualsPairingStrategy.getInstance();
        StubPairingContext ctx = new StubPairingContext();
        
        FlowEntry<String> incoming = new FlowEntry<>("incoming", "job-1");
        Optional<FlowEntry<String>> result = strategy.findPartner("k1", incoming, ctx);
        
        assertFalse(result.isPresent());
        assertTrue(ctx.wasGetAndRemoveCalled());
    }
    
    private static class StubPairingContext implements PairingContext<String> {
        private final java.util.Map<String, FlowEntry<String>> stored = new java.util.HashMap<>();
        private boolean getAndRemoveCalled;
        
        @Override
        public Optional<FlowEntry<String>> getAndRemove(String key) {
            getAndRemoveCalled = true;
            return Optional.ofNullable(stored.remove(key));
        }
        
        @Override
        public void put(String key, FlowEntry<String> entry) {
            stored.put(key, entry);
        }
        
        boolean wasGetAndRemoveCalled() {
            return getAndRemoveCalled;
        }
    }
}
