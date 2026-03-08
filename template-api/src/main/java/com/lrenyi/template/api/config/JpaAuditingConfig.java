package com.lrenyi.template.api.config;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * JPA 审计 AuditorAware：从 SecurityContext 获取当前登录用户名，供 @CreatedBy / @LastModifiedBy 使用。
 * <p>
 * 当 classpath 存在 Spring Security 与 spring-data-commons 时自动注册。
 * template-dataforge-jpa 的 @EnableJpaAuditing 会引用此 bean（dataforgeAuditorAware）。
 * </p>
 */
@Configuration
@ConditionalOnClass(Authentication.class)
public class JpaAuditingConfig {

    @Bean("dataforgeAuditorAware")
    public AuditorAware<String> dataforgeAuditorAware() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getName)
                .filter(name -> !"anonymousUser".equals(name));
    }
}
