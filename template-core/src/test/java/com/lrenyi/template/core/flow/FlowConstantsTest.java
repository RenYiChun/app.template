package com.lrenyi.template.core.flow;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlowConstantsTest {

    @Test
    void constructor_throwsUnsupportedOperationException() throws Exception {
        Constructor<FlowConstants> c = FlowConstants.class.getDeclaredConstructor();
        c.setAccessible(true);
        InvocationTargetException e = assertThrows(InvocationTargetException.class, c::newInstance);
        assertEquals(UnsupportedOperationException.class, e.getCause().getClass());
    }

    @Test
    void constants_values() {
        assertEquals(5L, FlowConstants.DEFAULT_SHUTDOWN_TIMEOUT_SECONDS);
        assertEquals(java.util.concurrent.TimeUnit.SECONDS, FlowConstants.DEFAULT_SHUTDOWN_TIMEOUT_UNIT);
        assertEquals(2L, FlowConstants.FORCE_SHUTDOWN_WAIT_SECONDS);
        assertEquals(50L, FlowConstants.DEFAULT_FAIR_LOCK_WAIT_MS);
        assertEquals(2000L, FlowConstants.DEFAULT_BACKPRESSURE_CHECK_INTERVAL_MS);
        assertEquals(3, FlowConstants.DEFAULT_MAX_RETRIES);
        assertEquals(1000L, FlowConstants.DEFAULT_INITIAL_RETRY_DELAY_MS);
        assertEquals(2.0, FlowConstants.DEFAULT_RETRY_MULTIPLIER);
        assertEquals("flow-storage-egress", FlowConstants.THREAD_NAME_STORAGE_EGRESS);
        assertEquals("prod-", FlowConstants.THREAD_NAME_PREFIX_PRODUCER);
        assertEquals("flow-resource-registry-shutdown", FlowConstants.THREAD_NAME_SHUTDOWN_HOOK);
        assertEquals("flow-progress-display", FlowConstants.THREAD_NAME_PROGRESS_DISPLAY);
    }
}
