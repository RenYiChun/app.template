package com.lrenyi.template.api;

import com.lrenyi.template.api.audit.aspect.AuditLogAspect;
import com.lrenyi.template.api.audit.processor.AuditLogProcessor;
import com.lrenyi.template.api.audit.service.AuditLogService;
import com.lrenyi.template.core.CoreAutoConfiguration;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.json.JsonService;
import com.lrenyi.template.core.util.Result;
import com.lrenyi.template.api.config.FeignClientConfiguration;
import com.lrenyi.template.api.config.RsaPublicAndPrivateKey;
import com.lrenyi.template.api.config.TemplateRsaPublicAndPrivateKey;
import com.nimbusds.common.contenttype.ContentType;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
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
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(CoreAutoConfiguration.class)
//@formatter:off
@Import({
        ApiAutoConfiguration.SecurityAutoConfiguration.class,
        ApiAutoConfiguration.FeignAutoConfiguration.class,
        ApiAutoConfiguration.AuditLogConfiguration.class
})
//@formatter:on
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ApiAutoConfiguration {
    
    /**
     * Feign配置模块 - 条件导入
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "feign.RequestInterceptor")
    @ConditionalOnProperty(name = "app.template.feign.enabled", havingValue = "true", matchIfMissing = true)
    @Import(FeignClientConfiguration.class)
    static class FeignAutoConfiguration {
        // 空的配置类，仅用于条件导入
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
        @Order(2)
        public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
                                                              RsaPublicAndPrivateKey rsaPublicAndPrivateKey,
                                                              Environment environment,
                                                              ObjectProvider<Consumer<HttpSecurity>> httpConfigurerProvider,
                                                              TemplateConfigProperties templateConfigProperties,
                                                              ObjectProvider<JsonService> jsonServiceProvider) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable);
            TemplateConfigProperties.SecurityProperties security = templateConfigProperties.getSecurity();
            if (!templateConfigProperties.isEnabled() || !security.isEnabled()) {
                http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
                return http.build();
            }
            String appName = environment.getProperty("spring.application.name");
            Set<String> permitUrlsOfApp = security.getDefaultPermitUrls();
            Map<String, Set<String>> permitUrls = security.getPermitUrls();
            Set<String> set = permitUrls.get(appName);
            if (set != null) {
                permitUrlsOfApp.addAll(set);
            }
            log.info("the permit urls of service {} is: {}", appName, String.join(",", permitUrlsOfApp));
            http.authorizeHttpRequests(authorize -> {
                String[] permitUrlsArray = permitUrlsOfApp.toArray(new String[0]);
                authorize.dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll();
                authorize.requestMatchers(permitUrlsArray).permitAll().anyRequest().authenticated();
            });
            TemplateConfigProperties.OAuth2Config oAuth2Config = templateConfigProperties.getOauth2();
            TemplateConfigProperties.OAuth2Config.OpaqueTokenConfig opaqueToken = oAuth2Config.getOpaqueToken();
            if (opaqueToken.isEnable()) {
                http.oauth2ResourceServer((oauth2) -> oauth2.opaqueToken((opaque) -> {
                    opaque.introspectionUri(opaqueToken.getIntrospectionUri())
                          .introspectionClientCredentials(opaqueToken.getIntrospectionClientId(),
                                                          opaqueToken.getIntrospectionClientSecret()
                          );
                }));
            } else {
                // @formatter:off
                if (security.isLocalJwtPublicKey()) {
                    // 加载本地公钥
                    RSAPublicKey publicKey = rsaPublicAndPrivateKey.templateRSAPublicKey();
                    // 使用公钥创建JwtDecoder
                    JwtDecoder jwtDecoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
                    // 配置资源服务器
                    http.oauth2ResourceServer(
                            oauth2ResourceServer -> oauth2ResourceServer
                                    .jwt(jwt -> jwt.decoder(jwtDecoder)));
                } else {
                    http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
                        String domain = security.getNetJwtPublicKeyDomain();
                        char c = domain.charAt(domain.length() - 1);
                        if (c == '/') {
                            domain = domain.substring(0, domain.length() - 1);
                        }
                        jwt.jwkSetUri(domain + "/jwt/public/key");
                    }));
                }
                // @formatter:on
            }
            // @formatter:off
            http.exceptionHandling((exceptionHandling) -> exceptionHandling.accessDeniedHandler((request, response, accessDeniedException) -> {
                // @formatter:on
                Result<String> error = new Result<>();
                error.makeThrowable(accessDeniedException, "发生了未被处理的异常");
                error.setData(request.getRequestURI());
                JsonService jsonService = jsonServiceProvider.getIfAvailable();
                if (jsonService != null) {
                    String jsonString = jsonService.serialize(error);
                    response.setContentType(ContentType.APPLICATION_JSON.getType());
                    try {
                        ServletOutputStream outputStream = response.getOutputStream();
                        outputStream.write(jsonString.getBytes(StandardCharsets.UTF_8));
                        outputStream.flush();
                    } catch (IOException e) {
                        log.error("返回异常结果给前端时出现异常", e);
                    }
                } else {
                    log.warn("the bean of JsonService is null");
                }
            }));
            Consumer<HttpSecurity> consumer = httpConfigurerProvider.getIfAvailable();
            if (consumer != null) {
                consumer.accept(http);
            }
            return http.build();
        }
    }
}
