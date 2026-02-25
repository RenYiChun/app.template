package com.lrenyi.template.core.flow.health;

import java.util.Map;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * 将 Flow 自定义健康检查桥接到 Spring Boot Actuator Health 端点。
 * <p>
 * 通过 {@code /actuator/health} 可查看 Flow 引擎的健康状态详情。
 */
@Component
@ConditionalOnClass(HealthIndicator.class)
public class FlowActuatorHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        HealthStatus status = FlowHealth.checkHealth();
        Map<String, Object> details = FlowHealth.getHealthDetails();
        return switch (status) {
            case HEALTHY -> Health.up().withDetails(details).build();
            case DEGRADED -> Health.status("DEGRADED").withDetails(details).build();
            case UNHEALTHY -> Health.down().withDetails(details).build();
        };
    }
}
