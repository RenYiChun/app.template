package com.lrenyi.template.platform.config;

import com.lrenyi.template.platform.permission.PlatformPermissionChecker;
import com.lrenyi.template.platform.permission.RbacPermissionChecker;
import com.lrenyi.template.platform.permission.UserPermissionResolver;
import com.lrenyi.template.platform.rbac.init.PermissionInitializer;
import com.lrenyi.template.platform.rbac.resolver.DefaultUserPermissionResolver;
import com.lrenyi.template.platform.rbac.service.RbacQueryService;
import com.lrenyi.template.platform.rbac.service.impl.RbacQueryServiceImpl;
import com.lrenyi.template.platform.registry.EntityRegistry;
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RBAC 动态权限校验自动配置：当 JPA 存在时注册 RbacQueryService 与 DefaultUserPermissionResolver，
 * 当 UserPermissionResolver 存在时注册 RbacPermissionChecker（优先于 DefaultPlatformPermissionChecker）。
 */
@Configuration
@AutoConfigureBefore(EntityPlatformAutoConfiguration.class)
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
                                                                EntityPlatformProperties properties) {
        return new DefaultUserPermissionResolver(rbacQueryService, properties);
    }

    @Bean
    @ConditionalOnBean(UserPermissionResolver.class)
    public PlatformPermissionChecker rbacPermissionChecker(UserPermissionResolver userPermissionResolver) {
        return new RbacPermissionChecker(userPermissionResolver);
    }

    @Bean
    @ConditionalOnClass(EntityManager.class)
    @ConditionalOnBean(EntityManager.class)
    public PermissionInitializer permissionInitializer(EntityRegistry entityRegistry,
                                                      EntityManager entityManager,
                                                      EntityPlatformProperties properties) {
        return new PermissionInitializer(entityRegistry, entityManager, properties);
    }
}
