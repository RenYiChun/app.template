package com.lrenyi.template.flow.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlowEntryTest {
    
    @Test
    void constructor_andGetters() {
        FlowEntry<String> e = new FlowEntry<>("data", "job1");
        assertEquals("data", e.getData());
        assertEquals("job1", e.getJobId());
    }
    
    @Test
    void retain_release() throws Exception {
        FlowEntry<String> e = new FlowEntry<>("d", "j");
        assertEquals(1, getRefCnt(e));
        e.retain();
        assertEquals(2, getRefCnt(e));
        e.retain();
        assertEquals(3, getRefCnt(e));
        e.release();
        assertEquals(2, getRefCnt(e));
        e.release();
        assertEquals(1, getRefCnt(e));
        e.release();
        assertEquals(0, getRefCnt(e));
    }
    
    private int getRefCnt(FlowEntry<?> entry) throws Exception {
        java.lang.reflect.Field field = FlowEntry.class.getDeclaredField("refCnt");
        field.setAccessible(true);
        return field.getInt(entry);
    }
    
    @Test
    void close_decrementsRefCount() throws Exception {
        FlowEntry<String> e = new FlowEntry<>("d", "j");
        e.retain();
        assertEquals(2, getRefCnt(e));
        e.close();
        assertEquals(1, getRefCnt(e));
    }
}
