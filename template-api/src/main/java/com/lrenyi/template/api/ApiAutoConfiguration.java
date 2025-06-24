package com.lrenyi.template.api;

import com.lrenyi.template.api.audit.aspect.AuditLogAspect;
import com.lrenyi.template.api.audit.processor.AuditLogProcessor;
import com.lrenyi.template.api.audit.service.AuditLogService;
import com.lrenyi.template.api.config.DefaultSecurityFilterChainBuilder;
import com.lrenyi.template.core.CoreAutoConfiguration;
import com.lrenyi.template.api.config.FeignClientConfiguration;
import com.lrenyi.template.api.config.RsaPublicAndPrivateKey;
import com.lrenyi.template.api.config.TemplateRsaPublicAndPrivateKey;
import com.lrenyi.template.core.TemplateConfigProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.introspection.SpringOpaqueTokenIntrospector;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 此模块作为表现层的自动配置类
 * <p>
 * 【模块职责】表现层 - 负责处理用户交互和Web请求
 * <p>
 * 核心功能：
 * • Web安全认证授权 (Spring Security, OAuth2)
 * • HTTP请求处理 (Spring MVC, REST API)
 * • WebSocket实时通信
 * • API文档生成 (Swagger/OpenAPI)
 * • 权限控制和访问管理
 * <p>
 * 适用场景：
 * • 需要提供Web API接口的应用
 * • 需要用户认证授权的Web应用
 * • 需要WebSocket实时通信的应用
 * • 需要在线API文档的应用
 * <p>
 */
@Slf4j
@ComponentScan
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(CoreAutoConfiguration.class)
//@formatter:off
@Import({
        ApiAutoConfiguration.SecurityAutoConfiguration.class,
        ApiAutoConfiguration.FeignAutoConfiguration.class,
        ApiAutoConfiguration.AuditLogConfiguration.class,
        ApiAutoConfiguration.MethodSecurityConfig.class
})
//@formatter:on
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ApiAutoConfiguration {
    
    /**
     * Feign配置模块 - 条件导入
     */
    @ConditionalOnClass(name = "feign.RequestInterceptor")
    @ConditionalOnProperty(name = "app.template.feign.enabled", havingValue = "true", matchIfMissing = true)
    @Import(FeignClientConfiguration.class)
    static class FeignAutoConfiguration {
        // 空的配置类，仅用于条件导入
    }
    
    @EnableMethodSecurity()
    @ConditionalOnProperty(name = "app.template.authorize.enabled", havingValue = "true", matchIfMissing = true)
    static class MethodSecurityConfig {
        // 可以在这里添加其他方法级别安全的配置
    }
    
    @EnableAsync
    @ConditionalOnProperty(name = "app.template.audit.enabled", havingValue = "true", matchIfMissing = true)
    static class AuditLogConfiguration {
        
        @Bean
        @ConditionalOnMissingBean
        public AuditLogProcessor auditLogProcessor() {
            // 默认的日志处理器，打印到控制台
            return System.out::println;
        }
        
        @Bean
        public AuditLogService auditLogService(AuditLogProcessor auditLogProcessor) {
            return new AuditLogService(auditLogProcessor);
        }
        
        @Bean
        public AuditLogAspect auditLogAspect(AuditLogService auditLogService) {
            return new AuditLogAspect(auditLogService);
        }
    }
    
    static class SecurityAutoConfiguration {
        
        @Bean
        @ConditionalOnMissingBean
        public RsaPublicAndPrivateKey rsaPublicAndPrivateKey() {
            return new TemplateRsaPublicAndPrivateKey();
        }
        
        @Bean
        @ConditionalOnMissingBean({OpaqueTokenIntrospector.class})
        @ConditionalOnProperty(
                name = "app.template.oauth2.opaque-token.enabled", havingValue = "true", matchIfMissing = true
        )
        SpringOpaqueTokenIntrospector opaqueTokenIntrospector(TemplateConfigProperties properties) {
            TemplateConfigProperties.OAuth2Config.OpaqueTokenConfig opaqueToken = properties.getOauth2()
                                                                                            .getOpaqueToken();
            return new SpringOpaqueTokenIntrospector(opaqueToken.getIntrospectionUri(),
                                                     opaqueToken.getClientId(),
                                                     opaqueToken.getClientSecret()
            );
        }
        
        @Bean
        public JwtAuthenticationConverter jwtAuthenticationConverter() {
            JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
            authoritiesConverter.setAuthorityPrefix("SCOPE_");
            // 设置从 scope 字段提取权限
            authoritiesConverter.setAuthoritiesClaimName("scope");
            
            JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
            converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
            return converter;
        }
        
        @Bean
        @Order(2)
        public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
                                                              DefaultSecurityFilterChainBuilder builder) throws Exception {
            
            return builder.build(http);
        }
    }
}
