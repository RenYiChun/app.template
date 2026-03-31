package com.lrenyi.template.flow.exception;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class DefaultFlowExceptionHandlerTest {
    
    private final DefaultFlowExceptionHandler handler = new DefaultFlowExceptionHandler();
    
    @Test
    void handleExceptionWithEntryIdAndNonEmptyContextLogsErrorWhenCritical() {
        FlowExceptionContext context = new FlowExceptionContext("job1",
                                                                "entry1",
                                                                new OutOfMemoryError("test OOM"),
                                                                FlowPhase.STORAGE
        ).addContext("key", "value");
        assertDoesNotThrow(() -> handler.handleException(context));
    }
    
    @Test
    void handleExceptionWithNullEntryIdLogsWarnWhenNotCritical() {
        FlowExceptionContext context =
                new FlowExceptionContext("job1", null, new RuntimeException("test"), FlowPhase.PRODUCTION);
        assertDoesNotThrow(() -> handler.handleException(context));
    }
    
    @Test
    void handleExceptionEmptyContextLogsWarn() {
        FlowExceptionContext context = new FlowExceptionContext("job1",
                                                                "e1",
                                                                new IllegalArgumentException("test empty context"),
                                                                FlowPhase.CONSUMPTION
        );
        assertDoesNotThrow(() -> handler.handleException(context));
    }
    
    @Test
    void handleExceptionStoragePhaseTreatedAsCritical() {
        FlowExceptionContext context = new FlowExceptionContext("j",
                                                                "e",
                                                                new RuntimeException("test storage critical"),
                                                                FlowPhase.STORAGE
        ).addContext("k", "v");
        assertDoesNotThrow(() -> handler.handleException(context));
    }
    
    @Test
    void handleExceptionVirtualMachineErrorTreatedAsCritical() {
        FlowExceptionContext context =
                new FlowExceptionContext("j", "e", new StackOverflowError("test SOE"), FlowPhase.CONSUMPTION);
        assertDoesNotThrow(() -> handler.handleException(context));
    }

    @Test
    void handleExceptionExpectedInterruptionLogsWithoutStackTrace(CapturedOutput output) {
        FlowExceptionContext context = new FlowExceptionContext("job1",
                                                                null,
                                                                new InterruptedException("test stop"),
                                                                FlowPhase.STORAGE,
                                                                "storage_acquire_interrupted")
                .addContext("displayName", "job1:1")
                .addContext("expectedInterruption", true);

        assertDoesNotThrow(() -> handler.handleException(context));

        assertTrue(output.getOut().contains("storage_acquire_interrupted"));
        assertFalse(output.getOut().contains("java.lang.InterruptedException"));
    }

    @Test
    void handleExceptionStorageAcquireInterruptedLogsWithoutStackTraceEvenWithoutFlag(CapturedOutput output) {
        FlowExceptionContext context = new FlowExceptionContext("job1",
                                                                null,
                                                                new InterruptedException("race stop"),
                                                                FlowPhase.STORAGE,
                                                                "storage_acquire_interrupted")
                .addContext("displayName", "job1:1");

        assertDoesNotThrow(() -> handler.handleException(context));

        assertTrue(output.getOut().contains("storage_acquire_interrupted"));
        assertFalse(output.getOut().contains("java.lang.InterruptedException"));
    }
}
