package com.lrenyi.template.core;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TemplateConfigProperties.class)
@ConditionalOnProperty(name = "app.template.core.enabled", matchIfMissing = true)
public class CoreAutoConfiguration {

}
