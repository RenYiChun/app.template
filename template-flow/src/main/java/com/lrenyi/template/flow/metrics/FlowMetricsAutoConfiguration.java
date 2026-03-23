package com.lrenyi.template.flow.metrics;

import com.lrenyi.template.flow.health.FlowActuatorHealthIndicator;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flow 指标体系自动配置。
 * <p>
 * 当 classpath 存在 {@link MeterRegistry} 时自动注册
 * {@link FlowActuatorHealthIndicator}，将 Flow 健康检查桥接到 Actuator。
 */
@Configuration
@ConditionalOnClass(MeterRegistry.class)
public class FlowMetricsAutoConfiguration {
    
    @Bean
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnMissingBean(FlowActuatorHealthIndicator.class)
    public FlowActuatorHealthIndicator flowActuatorHealthIndicator() {
        return new FlowActuatorHealthIndicator();
    }
}
