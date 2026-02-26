package com.lrenyi.template.flow.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowEntryTest {

    @Test
    void constructor_andGetters() {
        FlowEntry<String> e = new FlowEntry<>("data", "job1");
        assertEquals("data", e.getData());
        assertEquals("job1", e.getJobId());
    }

    @Test
    void retain_release() {
        FlowEntry<String> e = new FlowEntry<>("d", "j");
        e.retain();
        e.retain();
        e.release();
        e.release();
        e.release();
    }

    @Test
    void claimLogic_firstCall_returnsTrue() {
        FlowEntry<String> e = new FlowEntry<>("d", "j");
        assertTrue(e.claimLogic());
    }

    @Test
    void claimLogic_secondCall_returnsFalse() {
        FlowEntry<String> e = new FlowEntry<>("d", "j");
        assertTrue(e.claimLogic());
        assertFalse(e.claimLogic());
    }

    @Test
    void close_decrementsRefCount() {
        FlowEntry<String> e = new FlowEntry<>("d", "j");
        e.retain();
        e.close();
    }
}
