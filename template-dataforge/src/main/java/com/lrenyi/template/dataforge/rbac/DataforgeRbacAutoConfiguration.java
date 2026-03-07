package com.lrenyi.template.dataforge.rbac;

import com.lrenyi.template.dataforge.config.DataforgeProperties;
import com.lrenyi.template.dataforge.registry.EntityRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/**
 * RBAC 权限初始化自动装配。
 */
@AutoConfiguration
public class DataforgeRbacAutoConfiguration {
    
    @Bean
    @ConditionalOnBean(RbacPermissionSync.class)
    public PermissionInitializer permissionInitializer(ObjectProvider<EntityRegistry> entityRegistryProvider,
            ObjectProvider<DataforgeProperties> propertiesProvider,
            ObjectProvider<RbacPermissionSync> rbacPermissionSyncProvider) {
        return new PermissionInitializer(entityRegistryProvider, propertiesProvider, rbacPermissionSyncProvider);
    }
}
