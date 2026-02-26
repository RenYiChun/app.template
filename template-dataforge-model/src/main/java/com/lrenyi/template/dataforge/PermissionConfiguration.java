package com.lrenyi.template.dataforge;

import com.lrenyi.template.dataforge.config.DataforgeProperties;
import com.lrenyi.template.dataforge.config.PermissionInitializer;
import com.lrenyi.template.dataforge.registry.EntityRegistry;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * RBAC 动态权限校验自动配置：当 JPA 存在时注册 RbacQueryService 与 DefaultUserPermissionResolver，
 * 当 UserPermissionResolver 存在时注册 RbacPermissionChecker（优先于 DefaultDataforgePermissionChecker）。
 */
@Configuration
@AutoConfigureAfter(name = "org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration")
@AutoConfigureBefore(DataforgeAutoConfiguration.class)
public class PermissionConfiguration {
    
    @Bean
    @ConditionalOnClass(EntityManager.class)
    public PermissionInitializer permissionInitializer(ObjectProvider<EntityRegistry> entityRegistry,
            ObjectProvider<EntityManager> entityManager,
            ObjectProvider<DataforgeProperties> properties,
            ObjectProvider<TransactionTemplate> transactionTemplateProvider) {
        return new PermissionInitializer(entityRegistry, entityManager, properties, transactionTemplateProvider);
    }
}
