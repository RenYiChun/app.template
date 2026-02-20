package com.lrenyi.template.dataforge.config;

import com.lrenyi.template.dataforge.permission.DataforgePermissionChecker;
import com.lrenyi.template.dataforge.permission.RbacPermissionChecker;
import com.lrenyi.template.dataforge.permission.UserPermissionResolver;
import com.lrenyi.template.dataforge.rbac.resolver.DefaultUserPermissionResolver;
import com.lrenyi.template.dataforge.rbac.service.RbacQueryService;
import com.lrenyi.template.dataforge.rbac.service.impl.RbacQueryServiceImpl;
import com.lrenyi.template.dataforge.registry.EntityRegistry;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
public class RbacAutoConfiguration {

    @Bean
    @ConditionalOnClass(EntityManager.class)
    @ConditionalOnBean(EntityManager.class)
    public RbacQueryService rbacQueryService(EntityManager entityManager) {
        return new RbacQueryServiceImpl(entityManager);
    }

    @Bean
    @ConditionalOnBean(RbacQueryService.class)
    public UserPermissionResolver defaultUserPermissionResolver(RbacQueryService rbacQueryService,
                                                                DataforgeProperties properties) {
        return new DefaultUserPermissionResolver(rbacQueryService, properties);
    }

    @Bean
    @ConditionalOnBean(UserPermissionResolver.class)
    public DataforgePermissionChecker rbacPermissionChecker(UserPermissionResolver userPermissionResolver) {
        return new RbacPermissionChecker(userPermissionResolver);
    }

    @Bean
    @ConditionalOnClass(EntityManager.class)
    public PermissionInitializer permissionInitializer(EntityRegistry entityRegistry,
                                                      EntityManager entityManager,
                                                      DataforgeProperties properties,
                                                      ObjectProvider<TransactionTemplate> transactionTemplateProvider) {
        return new PermissionInitializer(entityRegistry, entityManager, properties, transactionTemplateProvider);
    }
}
