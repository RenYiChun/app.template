package com.lrenyi.template.flow.resource;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceShutdownExceptionTest {
    
    @Test
    void constructorMessageOnly() {
        ResourceShutdownException e = new ResourceShutdownException("msg");
        assertEquals("msg", e.getMessage());
        assertTrue(e.getErrors().isEmpty());
        assertFalse(e.hasMultipleErrors());
    }
    
    @Test
    void constructorMessageAndErrors() {
        List<Exception> errs = List.of(new RuntimeException("a"), new RuntimeException("b"));
        ResourceShutdownException e = new ResourceShutdownException("msg", errs);
        assertEquals("msg", e.getMessage());
        assertEquals(2, e.getErrors().size());
        assertTrue(e.hasMultipleErrors());
    }
    
    @Test
    void constructorMessageAndNullErrorsUsesEmptyList() {
        ResourceShutdownException e = new ResourceShutdownException("msg", (List<Exception>) null);
        assertTrue(e.getErrors().isEmpty());
        assertFalse(e.hasMultipleErrors());
    }
    
    @Test
    void constructorMessageAndCause() {
        Throwable cause = new RuntimeException("cause");
        ResourceShutdownException e = new ResourceShutdownException("msg", cause);
        assertEquals("msg", e.getMessage());
        assertEquals(cause, e.getCause());
        assertTrue(e.getErrors().isEmpty());
    }
}
