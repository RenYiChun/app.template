package com.lrenyi.template.flow.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlowEntryTest {
    
    @Test
    void constructorAndGetters() {
        FlowEntry<String> e = new FlowEntry<>("data", "job1");
        assertEquals("data", e.getData());
        assertEquals("job1", e.getJobId());
    }
    
    @Test
    void retainRelease() {
        FlowEntry<String> e = new FlowEntry<>("d", "j");
        assertEquals(1, e.getRefCntForTest());
        e.retain();
        assertEquals(2, e.getRefCntForTest());
        e.retain();
        assertEquals(3, e.getRefCntForTest());
        e.release();
        assertEquals(2, e.getRefCntForTest());
        e.release();
        assertEquals(1, e.getRefCntForTest());
        e.release();
        assertEquals(0, e.getRefCntForTest());
    }
    
    @Test
    void closeDecrementsRefCount() {
        FlowEntry<String> e = new FlowEntry<>("d", "j");
        e.retain();
        assertEquals(2, e.getRefCntForTest());
        e.close();
        assertEquals(1, e.getRefCntForTest());
    }
    
    @Test
    void initRetryRemainingOnlyOnce() {
        FlowEntry<String> e = new FlowEntry<>("d", "j");
        e.initRetryRemaining(3);
        assertEquals(3, e.getRetryRemaining());
        e.initRetryRemaining(10);
        assertEquals(3, e.getRetryRemaining());
    }

    @Test
    void initRetryRemainingWithMinusOneKeepsDefault() {
        FlowEntry<String> e = new FlowEntry<>("d", "j");
        assertEquals(-1, e.getRetryRemaining());
        e.initRetryRemaining(-1);
        assertEquals(-1, e.getRetryRemaining());
    }
    
    @Test
    void tryConsumeOneRetryUntilZero() {
        FlowEntry<String> e = new FlowEntry<>("d", "j");
        e.initRetryRemaining(2);
        assertEquals(2, e.getRetryRemaining());
        org.junit.jupiter.api.Assertions.assertTrue(e.tryConsumeOneRetry());
        assertEquals(1, e.getRetryRemaining());
        org.junit.jupiter.api.Assertions.assertTrue(e.tryConsumeOneRetry());
        assertEquals(0, e.getRetryRemaining());
        org.junit.jupiter.api.Assertions.assertFalse(e.tryConsumeOneRetry());
        assertEquals(0, e.getRetryRemaining());
    }
}
