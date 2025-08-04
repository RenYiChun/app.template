package com.lrenyi.template.api;

import com.lrenyi.template.api.audit.aspect.AuditLogAspect;
import com.lrenyi.template.api.audit.processor.AuditLogProcessor;
import com.lrenyi.template.api.audit.service.AuditLogService;
import com.lrenyi.template.api.config.DefaultSecurityFilterChainBuilder;
import com.lrenyi.template.api.config.RsaPublicAndPrivateKey;
import com.lrenyi.template.api.config.TemplateRsaPublicAndPrivateKey;
import com.lrenyi.template.core.CoreAutoConfiguration;
import com.lrenyi.template.core.TemplateConfigProperties;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionAuthenticatedPrincipal;
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
 * • 操作日志审计功能
 * • WebSocket实时通信
 * • 权限控制和访问管理
 * <p>
 * 适用场景：
 * • 需要提供Web API接口的应用
 * • 需要用户认证授权的Web应用
 * • 需要WebSocket实时通信的应用
 * <p>
 */
@Slf4j
@ComponentScan
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(CoreAutoConfiguration.class)
//@formatter:off
@Import({
        ApiAutoConfiguration.SecurityAutoConfiguration.class,
        ApiAutoConfiguration.AuditLogConfiguration.class,
        ApiAutoConfiguration.MethodSecurityConfig.class
})
//@formatter:on
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ApiAutoConfiguration {
    
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
        public AuditLogService auditLogService(AuditLogProcessor auditLogProcessor,
                                               @Value("${spring.application.name:unknown-service}") String serviceName) {
            return new AuditLogService(auditLogProcessor, serviceName);
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
        public SpringOpaqueTokenIntrospector opaqueTokenIntrospector(TemplateConfigProperties properties) {
            TemplateConfigProperties.OAuth2Config oauth2 = properties.getOauth2();
            TemplateConfigProperties.OAuth2Config.OpaqueTokenConfig opaqueToken = oauth2.getOpaqueToken();
            String uri = opaqueToken.getIntrospectionUri();
            String clientId = opaqueToken.getClientId();
            String clientSecret = opaqueToken.getClientSecret();
            SpringOpaqueTokenIntrospector introspector = new SpringOpaqueTokenIntrospector(uri, clientId, clientSecret);
            introspector.setAuthenticationConverter(accessor -> {
                Collection<GrantedAuthority> authorities = new ArrayList<>();
                List<String> scopes = accessor.getScopes();
                if (scopes != null) {
                    for (String scope : scopes) {
                        authorities.add(new SimpleGrantedAuthority(scope));
                    }
                }
                return new OAuth2IntrospectionAuthenticatedPrincipal(accessor.getClaims(), authorities);
            });
            return introspector;
        }
        
        @Bean
        public JwtAuthenticationConverter jwtAuthenticationConverter() {
            JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
            authoritiesConverter.setAuthorityPrefix("");
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
