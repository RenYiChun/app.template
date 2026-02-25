package com.lrenyi.template.core.flow.manager;

import java.util.Map;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.flow.health.FlowHealth;
import com.lrenyi.template.core.flow.resource.FlowResourceRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowManagerTest {

    private TemplateConfigProperties.Flow config;

    @BeforeEach
    void setUp() {
        FlowManager.reset();
        FlowResourceRegistry.reset();
        FlowHealth.clearIndicators();
        config = new TemplateConfigProperties.Flow();
        config.getConsumer().setConcurrencyLimit(100);
    }

    @AfterEach
    void tearDown() {
        FlowManager.reset();
        FlowResourceRegistry.reset();
        FlowHealth.clearIndicators();
    }

    @Test
    void getInstance_returnsNonNull() {
        FlowManager manager = FlowManager.getInstance(config);
        assertNotNull(manager);
    }

    @Test
    void getInstance_sameConfig_returnsSameInstance() {
        FlowManager m1 = FlowManager.getInstance(config);
        FlowManager m2 = FlowManager.getInstance(config);
        assertSame(m1, m2);
    }

    @Test
    void getInstance_differentConfig_recreatesInstance() {
        FlowManager m1 = FlowManager.getInstance(config);
        TemplateConfigProperties.Flow config2 = new TemplateConfigProperties.Flow();
        config2.getConsumer().setConcurrencyLimit(200);
        FlowManager m2 = FlowManager.getInstance(config2);
        assertNotNull(m2);
        assertNotSame(m1, m2);
    }

    @Test
    void packageConstructor_createsInstanceWithoutInit() {
        FlowManager manager = new FlowManager(config, new SimpleMeterRegistry(), true);
        assertNotNull(manager);
        assertNotNull(manager.getResourceRegistry());
        assertNotNull(manager.getRegistry());
    }

    @Test
    void getActiveLaunchers_initiallyEmpty() {
        FlowManager manager = FlowManager.getInstance(config);
        Map<String, ?> launchers = manager.getActiveLaunchers();
        assertNotNull(launchers);
        assertTrue(launchers.isEmpty());
    }

    @Test
    void getHealthStatus_returnsMap() {
        FlowManager manager = FlowManager.getInstance(config);
        Map<String, Object> health = manager.getHealthStatus();
        assertNotNull(health);
    }

    @Test
    void reset_clearsInstance() {
        FlowManager.getInstance(config);
        FlowManager.reset();
        FlowManager m2 = FlowManager.getInstance(config);
        assertNotNull(m2);
    }
}
