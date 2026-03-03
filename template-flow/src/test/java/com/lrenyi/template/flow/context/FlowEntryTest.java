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
}
