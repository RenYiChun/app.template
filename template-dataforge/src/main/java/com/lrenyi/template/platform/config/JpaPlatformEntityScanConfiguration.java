package com.lrenyi.template.platform.config;

import com.lrenyi.template.platform.domain.Permission;
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.domain.EntityScan;

/**
 * 当 JPA 启用时，自动将 {@code com.lrenyi.template.platform.domain} 加入实体扫描，使 RBAC 等平台内置实体
 * 对应用透明，无需在 {@code @EntityScan} 中显式配置该包。
 */
@AutoConfiguration
@ConditionalOnClass(EntityManager.class)
@EntityScan(basePackageClasses = Permission.class)
public class JpaPlatformEntityScanConfiguration {
}
