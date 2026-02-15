package com.lrenyi.template.entityplatform.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 仅在 classpath 存在 template-api 时加载 {@link EntityPlatformAuditConfiguration}，
 * 避免未依赖 template-api 时加载审计相关类导致 NoClassDefFoundError。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "com.lrenyi.template.api.audit.resolver.AuditDescriptionResolver")
@Import(EntityPlatformAuditConfiguration.class)
public class EntityPlatformAuditConfigurationBootstrap {
}
