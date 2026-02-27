package com.lrenyi.template.dataforge.backend.config;

import com.lrenyi.template.dataforge.audit.processor.AuditLogProcessor;
import com.lrenyi.template.dataforge.backend.processor.DataforgeAuditLogProcessor;
import com.lrenyi.template.dataforge.registry.EntityRegistry;
import com.lrenyi.template.dataforge.service.EntityCrudService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 业务层 Bean 注册：AuditLogProcessor、RbacPermissionSync 等与具体实体相关的实现。
 * Permission、OperationLog 由业务定义，框架只依赖接口。
 */
@Configuration(proxyBeanMethods = false)
public class DataforgeBackendBeansConfiguration {
    
    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.template.audit.enabled", havingValue = "true")
    public AuditLogProcessor dataforgeAuditLogProcessor(EntityRegistry entityRegistry,
            EntityCrudService entityCrudService) {
        return new DataforgeAuditLogProcessor(entityRegistry, entityCrudService);
    }
}
