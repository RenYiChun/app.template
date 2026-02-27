package com.lrenyi.template.flow.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DefaultFlowExceptionHandlerTest {

    private final DefaultFlowExceptionHandler handler = new DefaultFlowExceptionHandler();

    @Test
    void handleException_withEntryId_andNonEmptyContext_logsErrorWhenCritical() {
        FlowExceptionContext context = new FlowExceptionContext("job1", "entry1", new OutOfMemoryError("test OOM"), FlowPhase.STORAGE)
                .addContext("key", "value");
        assertDoesNotThrow(() -> handler.handleException(context));
    }

    @Test
    void handleException_withNullEntryId_logsWarnWhenNotCritical() {
        FlowExceptionContext context = new FlowExceptionContext("job1", null, new RuntimeException("test"), FlowPhase.PRODUCTION);
        assertDoesNotThrow(() -> handler.handleException(context));
    }

    @Test
    void handleException_emptyContext_logsWarn() {
        FlowExceptionContext context = new FlowExceptionContext("job1", "e1", new IllegalArgumentException("test empty context"), FlowPhase.CONSUMPTION);
        assertDoesNotThrow(() -> handler.handleException(context));
    }

    @Test
    void handleException_storagePhase_treatedAsCritical() {
        FlowExceptionContext context = new FlowExceptionContext("j", "e", new RuntimeException("test storage critical"), FlowPhase.STORAGE)
                .addContext("k", "v");
        assertDoesNotThrow(() -> handler.handleException(context));
    }

    @Test
    void handleException_virtualMachineError_treatedAsCritical() {
        FlowExceptionContext context = new FlowExceptionContext("j", "e", new StackOverflowError("test SOE"), FlowPhase.CONSUMPTION);
        assertDoesNotThrow(() -> handler.handleException(context));
    }
}
