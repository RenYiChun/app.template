package com.lrenyi.template.flow;

import com.lrenyi.template.flow.metrics.FlowMetricsAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Flow 模块自动配置入口，由 Spring Boot 通过 AutoConfiguration.imports 加载。
 */
@Configuration(proxyBeanMethods = false)
@Import(FlowMetricsAutoConfiguration.class)
public class FlowAutoConfiguration {
}
