package com.lrenyi.template.dataforge.jpa;

import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.domain.EntityScan;

/**
 * 当 JPA 启用时，自动将 {@code com.lrenyi.template.dataforge.domain} 加入实体扫描。
 */
@AutoConfiguration
@ConditionalOnClass(EntityManager.class)
@EntityScan(basePackages = "com.lrenyi.template.dataforge.domain")
public class JpaDataforgeEntityScanConfiguration {}
