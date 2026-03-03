package com.lrenyi.template.flow.health;

import java.util.Collections;
import java.util.List;
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
    
    private static final String KEY_INDICATORS = "indicators";
    private static final String KEY_STATUS = "status";
    
    @AfterEach
    void tearDown() {
        FlowHealth.clearIndicators();
    }
    
    @Test
    void checkHealthWhenNoIndicatorsReturnsHealthy() {
        FlowHealth.clearIndicators();
        assertEquals(HealthStatus.HEALTHY, FlowHealth.checkHealth());
    }
    
    @Test
    void checkHealthSingleIndicatorReturnsThatStatus() {
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
    
    private static FlowHealthIndicator fixedIndicator(String name, HealthStatus status) {
        return new FlowHealthIndicator() {
            @Override
            public HealthStatus checkHealth() {
                return status;
            }
            
            @Override
            public Map<String, Object> getDetails() {
                return Collections.singletonMap(KEY_STATUS, status.name());
            }
            
            @Override
            public String getName() {
                return name;
            }
        };
    }
    
    @Test
    void checkHealthMultipleIndicatorsReturnsWorstStatus() {
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
    void checkHealthIndicatorThrowsReturnsUnhealthy() {
        FlowHealth.clearIndicators();
        FlowHealth.registerIndicator(new FlowHealthIndicator() {
            @Override
            public HealthStatus checkHealth() {
                throw new IndicatorTestException("indicator failed");
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
    void getHealthDetailsContainsOverallStatusAndIndicators() {
        FlowHealth.clearIndicators();
        FlowHealth.registerIndicator(fixedIndicator("TestIndicator", HealthStatus.DEGRADED));
        
        Map<String, Object> details = FlowHealth.getHealthDetails();
        assertNotNull(details);
        assertEquals(HealthStatus.DEGRADED.name(), details.get("overallStatus"));
        
        @SuppressWarnings("unchecked") List<Map<String, Object>> indicators =
                (List<Map<String, Object>>) details.get(KEY_INDICATORS);
        assertNotNull(indicators);
        assertEquals(1, indicators.size());
        assertEquals("TestIndicator", indicators.getFirst().get("name"));
        assertEquals(HealthStatus.DEGRADED.name(), indicators.getFirst().get(KEY_STATUS));
        assertNotNull(indicators.getFirst().get("details"));
    }
    
    @Test
    void getHealthDetailsIndicatorThrowsIncludesErrorInDetails() {
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
        List<Map<String, Object>> indicators = (List<Map<String, Object>>) details.get(KEY_INDICATORS);
        assertNotNull(indicators);
        assertEquals(1, indicators.size());
        assertEquals("BadIndicator", indicators.getFirst().get("name"));
        assertEquals(HealthStatus.UNHEALTHY.name(), indicators.getFirst().get(KEY_STATUS));
        assertTrue(indicators.getFirst().containsKey("error"));
    }
    
    @Test
    void clearIndicatorsAfterClearReturnsHealthy() {
        FlowHealth.registerIndicator(fixedIndicator("X", HealthStatus.UNHEALTHY));
        assertEquals(HealthStatus.UNHEALTHY, FlowHealth.checkHealth());
        
        FlowHealth.clearIndicators();
        assertEquals(HealthStatus.HEALTHY, FlowHealth.checkHealth());
        
        Map<String, Object> details = FlowHealth.getHealthDetails();
        assertEquals(HealthStatus.HEALTHY.name(), details.get("overallStatus"));
        List<?> indicators = (List<?>) details.get(KEY_INDICATORS);
        assertTrue(indicators == null || indicators.isEmpty());
    }
    
    private static class IndicatorTestException extends RuntimeException {
        IndicatorTestException(String message) {
            super(message);
        }
    }
}
