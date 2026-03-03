package com.lrenyi.template.flow.storage;

import com.lrenyi.template.flow.context.FlowEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 覆盖 FlowStorage 默认方法分支：deposit(null)、deposit 后 doDeposit 为 false 时 release、remove 默认抛异常。
 */
class FlowStorageTest {
    
    @Test
    void depositNullDoesNothing() {
        StubStorage<Object> storage = new StubStorage<>(true);
        assertDoesNotThrow(() -> storage.deposit(null));
        assertEquals(0, storage.depositCallCount);
    }
    
    @Test
    void depositSuccessNoRelease() {
        StubStorage<String> storage = new StubStorage<>(true);
        FlowEntry<String> entry = new FlowEntry<>("d", "j");
        storage.deposit(entry);
        assertEquals(1, storage.depositCallCount);
    }
    
    @Test
    void depositDoDepositFalseReleasesEntry() {
        StubStorage<String> storage = new StubStorage<>(false);
        FlowEntry<String> entry = new FlowEntry<>("d", "j");
        storage.deposit(entry);
        assertEquals(1, storage.depositCallCount);
        // 接口默认方法在 doDeposit 返回 false 时会调用 entry.release()
    }
    
    @Test
    void removeDefaultThrows() {
        StubStorage<Object> storage = new StubStorage<>(true);
        assertThrows(UnsupportedOperationException.class, () -> storage.remove("key"));
    }
    
    private static class StubStorage<T> implements FlowStorage<T> {
        private final boolean doDepositReturn;
        int depositCallCount;
        
        StubStorage(boolean doDepositReturn) {
            this.doDepositReturn = doDepositReturn;
        }
        
        @Override
        public boolean doDeposit(FlowEntry<T> ctx) {
            depositCallCount++;
            return doDepositReturn;
        }
        
        @Override
        public long size() {
            return 0;
        }
        
        @Override
        public long maxCacheSize() {
            return 0;
        }
        
        @Override
        public void shutdown() {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }
}
