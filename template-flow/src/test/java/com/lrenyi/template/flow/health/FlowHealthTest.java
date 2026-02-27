package com.lrenyi.template.flow.health;

import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 健康检查单元测试：验证 checkHealth 聚合逻辑与 getHealthDetails 结构、数值准确性。
 */
class FlowHealthTest {

    @AfterEach
    void tearDown() {
        FlowHealth.clearIndicators();
    }

    @Test
    void checkHealth_whenNoIndicators_returnsHealthy() {
        FlowHealth.clearIndicators();
        assertEquals(HealthStatus.HEALTHY, FlowHealth.checkHealth());
    }

    @Test
    void checkHealth_singleIndicator_returnsThatStatus() {
        FlowHealth.clearIndicators();
        FlowHealth.registerIndicator(fixedIndicator("A", HealthStatus.HEALTHY));
        assertEquals(HealthStatus.HEALTHY, FlowHealth.checkHealth());

        FlowHealth.clearIndicators();
        FlowHealth.registerIndicator(fixedIndicator("B", HealthStatus.DEGRADED));
        assertEquals(HealthStatus.DEGRADED, FlowHealth.checkHealth());

        FlowHealth.clearIndicators();
        FlowHealth.registerIndicator(fixedIndicator("C", HealthStatus.UNHEALTHY));
        assertEquals(HealthStatus.UNHEALTHY, FlowHealth.checkHealth());
    }

    @Test
    void checkHealth_multipleIndicators_returnsWorstStatus() {
        FlowHealth.clearIndicators();
        FlowHealth.registerIndicator(fixedIndicator("I1", HealthStatus.HEALTHY));
        FlowHealth.registerIndicator(fixedIndicator("I2", HealthStatus.DEGRADED));
        assertEquals(HealthStatus.DEGRADED, FlowHealth.checkHealth());

        FlowHealth.clearIndicators();
        FlowHealth.registerIndicator(fixedIndicator("I1", HealthStatus.DEGRADED));
        FlowHealth.registerIndicator(fixedIndicator("I2", HealthStatus.UNHEALTHY));
        assertEquals(HealthStatus.UNHEALTHY, FlowHealth.checkHealth());

        FlowHealth.clearIndicators();
        FlowHealth.registerIndicator(fixedIndicator("I1", HealthStatus.HEALTHY));
        FlowHealth.registerIndicator(fixedIndicator("I2", HealthStatus.HEALTHY));
        assertEquals(HealthStatus.HEALTHY, FlowHealth.checkHealth());
    }

    @Test
    void checkHealth_indicatorThrows_returnsUnhealthy() {
        FlowHealth.clearIndicators();
        FlowHealth.registerIndicator(new FlowHealthIndicator() {
            @Override
            public HealthStatus checkHealth() {
                throw new RuntimeException("indicator failed");
            }

            @Override
            public Map<String, Object> getDetails() {
                return Collections.emptyMap();
            }

            @Override
            public String getName() {
                return "FailingIndicator";
            }
        });
        assertEquals(HealthStatus.UNHEALTHY, FlowHealth.checkHealth());
    }

    @Test
    void getHealthDetails_containsOverallStatusAndIndicators() {
        FlowHealth.clearIndicators();
        FlowHealth.registerIndicator(fixedIndicator("TestIndicator", HealthStatus.DEGRADED));

        Map<String, Object> details = FlowHealth.getHealthDetails();
        assertNotNull(details);
        assertEquals(HealthStatus.DEGRADED.name(), details.get("overallStatus"));

        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> indicators = (java.util.List<Map<String, Object>>) details.get("indicators");
        assertNotNull(indicators);
        assertEquals(1, indicators.size());
        assertEquals("TestIndicator", indicators.getFirst().get("name"));
        assertEquals(HealthStatus.DEGRADED.name(), indicators.getFirst().get("status"));
        assertNotNull(indicators.getFirst().get("details"));
    }

    @Test
    void getHealthDetails_indicatorThrows_includesErrorInDetails() {
        FlowHealth.clearIndicators();
        FlowHealth.registerIndicator(new FlowHealthIndicator() {
            @Override
            public HealthStatus checkHealth() {
                throw new IllegalStateException("check failed");
            }

            @Override
            public Map<String, Object> getDetails() {
                return Collections.emptyMap();
            }

            @Override
            public String getName() {
                return "BadIndicator";
            }
        });

        Map<String, Object> details = FlowHealth.getHealthDetails();
        java.util.List<Map<String, Object>> indicators = (java.util.List<Map<String, Object>>) details.get("indicators");
        assertNotNull(indicators);
        assertEquals(1, indicators.size());
        assertEquals("BadIndicator", indicators.getFirst().get("name"));
        assertEquals(HealthStatus.UNHEALTHY.name(), indicators.getFirst().get("status"));
        assertTrue(indicators.getFirst().containsKey("error"));
    }

    @Test
    void clearIndicators_afterClear_returnsHealthy() {
        FlowHealth.registerIndicator(fixedIndicator("X", HealthStatus.UNHEALTHY));
        assertEquals(HealthStatus.UNHEALTHY, FlowHealth.checkHealth());

        FlowHealth.clearIndicators();
        assertEquals(HealthStatus.HEALTHY, FlowHealth.checkHealth());

        Map<String, Object> details = FlowHealth.getHealthDetails();
        assertEquals(HealthStatus.HEALTHY.name(), details.get("overallStatus"));
        java.util.List<?> indicators = (java.util.List<?>) details.get("indicators");
        assertTrue(indicators == null || indicators.isEmpty());
    }

    private static FlowHealthIndicator fixedIndicator(String name, HealthStatus status) {
        return new FlowHealthIndicator() {
            @Override
            public HealthStatus checkHealth() {
                return status;
            }

            @Override
            public Map<String, Object> getDetails() {
                return Collections.singletonMap("status", status.name());
            }

            @Override
            public String getName() {
                return name;
            }
        };
    }
}
