package com.lrenyi.template.flow.resource;

import com.lrenyi.template.core.TemplateConfigProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowResourceRegistryTest {
    
    @AfterEach
    void tearDown() {
        FlowResourceRegistry.reset();
    }
    
    @Test
    void testConstructor_createsInitializedInstance() {
        TemplateConfigProperties.Flow config = new TemplateConfigProperties.Flow();
        config.getConsumer().setConcurrencyLimit(16);
        
        FlowResourceRegistry registry = new FlowResourceRegistry(config, new SimpleMeterRegistry(), true);
        
        assertTrue(registry.isInitialized());
        assertNotNull(registry.getGlobalSemaphore());
        assertNotNull(registry.getFlowConsumerExecutor());
        assertNotNull(registry.getStorageEgressExecutor());
        assertNotNull(registry.getCacheRemovalExecutor());
        assertNotNull(registry.getFairLock());
        assertNotNull(registry.getPermitReleased());
        assertNotNull(registry.getFlowCacheManager());
        
        try {
            registry.shutdown();
        } catch (ResourceShutdownException e) {
            // ignore
        }
        assertTrue(registry.isShutdown());
    }
    
    @Test
    void testConstructor_shutdownStopsExecutors() throws ResourceShutdownException {
        TemplateConfigProperties.Flow config = new TemplateConfigProperties.Flow();
        config.getConsumer().setConcurrencyLimit(8);
        
        FlowResourceRegistry registry = new FlowResourceRegistry(config, new SimpleMeterRegistry(), false);
        assertFalse(registry.isShutdown());
        
        registry.shutdown();
        assertTrue(registry.isShutdown());
        assertTrue(registry.getFlowConsumerExecutor().isShutdown());
    }
}
