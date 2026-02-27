package com.lrenyi.template.flow;

import com.lrenyi.template.flow.metrics.FlowMetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Flow 模块自动配置入口，由 Spring Boot 通过 AutoConfiguration.imports 加载。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.template.enabled", havingValue = "true", matchIfMissing = true)
@Import(FlowMetricsAutoConfiguration.class)
public class FlowAutoConfiguration {
}
