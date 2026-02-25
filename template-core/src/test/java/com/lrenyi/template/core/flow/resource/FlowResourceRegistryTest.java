package com.lrenyi.template.core.flow.resource;

import com.lrenyi.template.core.TemplateConfigProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FlowResourceRegistry 单元测试。
 * 使用包可见构造函数创建独立实例，不影响单例。
 */
class FlowResourceRegistryTest {

    @AfterEach
    void tearDown() {
        FlowResourceRegistry.reset();
    }

    @Test
    void testConstructor_createsInitializedInstance() {
        TemplateConfigProperties.Flow config = new TemplateConfigProperties.Flow();
        config.getConsumer().setConcurrencyLimit(16);
        config.getMonitor().setProgressDisplaySecond(0);

        FlowResourceRegistry registry = new FlowResourceRegistry(config, true);

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
        config.getMonitor().setProgressDisplaySecond(0);

        FlowResourceRegistry registry = new FlowResourceRegistry(config, false);
        assertFalse(registry.isShutdown());

        registry.shutdown();
        assertTrue(registry.isShutdown());
        assertTrue(registry.getFlowConsumerExecutor().isShutdown());
    }
}
