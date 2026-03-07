package com.lrenyi.template.flow.manager;

import java.util.Map;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.health.FlowHealth;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
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
        config.getLimits().getGlobal().setConsumerConcurrency(100);
    }
    
    @AfterEach
    void tearDown() {
        FlowManager.reset();
        FlowResourceRegistry.reset();
        FlowHealth.clearIndicators();
    }
    
    @Test
    void getInstanceReturnsNonNull() {
        FlowManager manager = FlowManager.getInstance(config);
        assertNotNull(manager);
    }
    
    @Test
    void getInstanceSameConfigReturnsSameInstance() {
        FlowManager m1 = FlowManager.getInstance(config);
        FlowManager m2 = FlowManager.getInstance(config);
        assertSame(m1, m2);
    }
    
    @Test
    void getInstanceDifferentConfigRecreatesInstance() {
        FlowManager m1 = FlowManager.getInstance(config);
        TemplateConfigProperties.Flow config2 = new TemplateConfigProperties.Flow();
        config2.getLimits().getGlobal().setConsumerConcurrency(200);
        FlowManager m2 = FlowManager.getInstance(config2);
        assertNotNull(m2);
        assertNotSame(m1, m2);
    }
    
    @Test
    void packageConstructorCreatesInstanceWithoutInit() {
        FlowManager manager = new FlowManager(config, new SimpleMeterRegistry(), true);
        assertNotNull(manager);
        assertNotNull(manager.getResourceRegistry());
        assertNotNull(manager.getRegistry());
    }
    
    @Test
    void getActiveLaunchersInitiallyEmpty() {
        FlowManager manager = FlowManager.getInstance(config);
        Map<String, ?> launchers = manager.getActiveLaunchers();
        assertNotNull(launchers);
        assertTrue(launchers.isEmpty());
    }
    
    @Test
    void getHealthStatusReturnsMap() {
        FlowManager manager = FlowManager.getInstance(config);
        Map<String, Object> health = manager.getHealthStatus();
        assertNotNull(health);
    }
    
    @Test
    void resetClearsInstance() {
        FlowManager.getInstance(config);
        FlowManager.reset();
        FlowManager m2 = FlowManager.getInstance(config);
        assertNotNull(m2);
    }
}
