package com.lrenyi.template.core.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadConstantTest {

    @Test
    void namePrefix_isExpected() {
        assertTrue(ThreadConstant.NAME_PREFIX.startsWith("auto_create_thread_"));
    }

    @Test
    void virtualThreadExecutor_isNotNull() {
        ExecutorService executor = ThreadConstant.VIRTUAL_THREAD_EXECUTOR;
        assertNotNull(executor);
    }

    @Test
    void scheduledExecutorService_isNotNull() {
        ScheduledExecutorService scheduler = ThreadConstant.SCHEDULED_EXECUTOR_SERVICE;
        assertNotNull(scheduler);
    }
}
