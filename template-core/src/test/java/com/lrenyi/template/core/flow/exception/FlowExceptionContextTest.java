package com.lrenyi.template.core.flow.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowExceptionContextTest {

    @Test
    void constructor_andGetters() {
        RuntimeException ex = new RuntimeException("e");
        FlowExceptionContext ctx = new FlowExceptionContext("job1", "entry1", ex, FlowPhase.CONSUMPTION);
        assertEquals("job1", ctx.getJobId());
        assertEquals("entry1", ctx.getEntryId());
        assertSame(ex, ctx.getException());
        assertEquals(FlowPhase.CONSUMPTION, ctx.getPhase());
        assertTrue(ctx.getContext().isEmpty());
    }

    @Test
    void addContext_returnsThis_andGetContextWithType() {
        FlowExceptionContext ctx = new FlowExceptionContext("j", "e", new RuntimeException(), FlowPhase.UNKNOWN);
        FlowExceptionContext chained = ctx.addContext("k1", "v1").addContext("k2", 2);
        assertSame(ctx, chained);
        assertEquals("v1", ctx.getContext("k1", String.class));
        assertEquals(Integer.valueOf(2), ctx.getContext("k2", Integer.class));
    }

    @Test
    void getContext_wrongType_returnsNull() {
        FlowExceptionContext ctx = new FlowExceptionContext("j", "e", new RuntimeException(), FlowPhase.UNKNOWN);
        ctx.addContext("k", "string");
        assertNull(ctx.getContext("k", Integer.class));
    }

    @Test
    void getContext_missingKey_returnsNull() {
        FlowExceptionContext ctx = new FlowExceptionContext("j", "e", new RuntimeException(), FlowPhase.UNKNOWN);
        assertNull(ctx.getContext("missing", String.class));
    }
}
