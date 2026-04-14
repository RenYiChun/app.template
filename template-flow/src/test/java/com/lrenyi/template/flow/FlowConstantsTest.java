package com.lrenyi.template.flow;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.TimeUnit;
import com.lrenyi.template.flow.model.FlowConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlowConstantsTest {
    
    @Test
    void constructorThrowsUnsupportedOperationException() throws Exception {
        Constructor<FlowConstants> c = FlowConstants.class.getDeclaredConstructor();
        c.setAccessible(true);
        InvocationTargetException e = assertThrows(InvocationTargetException.class, c::newInstance);
        assertEquals(UnsupportedOperationException.class, e.getCause().getClass());
    }
    
    @Test
    void constantsValues() {
        assertEquals(5L, FlowConstants.DEFAULT_SHUTDOWN_TIMEOUT_SECONDS);
        assertEquals(TimeUnit.SECONDS, FlowConstants.DEFAULT_SHUTDOWN_TIMEOUT_UNIT);
        assertEquals(2L, FlowConstants.FORCE_SHUTDOWN_WAIT_SECONDS);
        assertEquals(50L, FlowConstants.DEFAULT_FAIR_LOCK_WAIT_MS);
        assertEquals(100L, FlowConstants.DEFAULT_BACKPRESSURE_CHECK_INTERVAL_MS);
        assertEquals(3, FlowConstants.DEFAULT_MAX_RETRIES);
        assertEquals(1000L, FlowConstants.DEFAULT_INITIAL_RETRY_DELAY_MS);
        assertEquals(2.0, FlowConstants.DEFAULT_RETRY_MULTIPLIER);
        assertEquals("flow-producer-", FlowConstants.THREAD_NAME_PREFIX_PRODUCER);
        assertEquals("flow-consumer-", FlowConstants.THREAD_NAME_PREFIX_CONSUMER);
        assertEquals("flow-eviction", FlowConstants.THREAD_NAME_PREFIX_EVICTION);
        assertEquals("flow-storage-egress-", FlowConstants.THREAD_NAME_PREFIX_STORAGE_EGRESS);
        assertEquals("flow-resource-registry-shutdown", FlowConstants.THREAD_NAME_SHUTDOWN_HOOK);
        assertEquals("flow-progress-display", FlowConstants.THREAD_NAME_PROGRESS_DISPLAY);
    }
}
