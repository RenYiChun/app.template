package com.lrenyi.template.core.flow.metrics;

import com.lrenyi.template.core.flow.health.FlowActuatorHealthIndicator;
import com.lrenyi.template.core.metrics.AppMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flow 指标体系自动配置。
 * <p>
 * 当 classpath 存在 {@link MeterRegistry} 时自动注册：
 * <ul>
 *   <li>{@link AppMetrics} — 业务指标扩展工具类</li>
 *   <li>{@link FlowActuatorHealthIndicator} — Flow 健康检查桥接到 Actuator</li>
 * </ul>
 */
@Configuration
@ConditionalOnClass(MeterRegistry.class)
public class FlowMetricsAutoConfiguration {

    @Bean
    @ConditionalOnClass(HealthIndicator.class)
    public FlowActuatorHealthIndicator flowActuatorHealthIndicator() {
        return new FlowActuatorHealthIndicator();
    }

    @Bean
    public AppMetrics appMetrics(MeterRegistry meterRegistry) {
        return new AppMetrics(meterRegistry);
    }
}
