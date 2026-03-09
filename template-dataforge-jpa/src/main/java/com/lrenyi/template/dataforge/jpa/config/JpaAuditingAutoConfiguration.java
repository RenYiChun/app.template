package com.lrenyi.template.dataforge.jpa.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA 审计自动配置：启用 @CreatedBy / @LastModifiedBy 自动填充。
 * <p>
 * 依赖 template-api 提供的 AuditorAware bean（dataforgeAuditorAware），
 * 当该 bean 存在时自动启用 JPA Auditing，createBy/updateBy 由当前登录用户名填充。
 * </p>
 */
@AutoConfiguration
@EnableJpaAuditing(auditorAwareRef = "dataforgeAuditorAware")
@ConditionalOnBean(name = "dataforgeAuditorAware")
public class JpaAuditingAutoConfiguration {
}
