package com.lrenyi.template.web;

import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.json.JsonService;
import com.lrenyi.template.core.util.Result;
import com.lrenyi.template.web.config.RequestAuthorizationManager;
import com.lrenyi.template.web.config.RsaPublicAndPrivateKey;
import com.lrenyi.template.web.config.TemplateRsaPublicAndPrivateKey;
import com.nimbusds.common.contenttype.ContentType;
import feign.RequestInterceptor;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "app.template.web.enabled", matchIfMissing = true)
public class ApiAutoConfiguration {
    
    /**
     * 安全配置模块
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "app.template.security.enabled", matchIfMissing = true)
    static class SecurityAutoConfiguration {
        
        @Bean
        @ConditionalOnMissingBean
        public RsaPublicAndPrivateKey rsaPublicAndPrivateKey() {
            return new TemplateRsaPublicAndPrivateKey();
        }
        
        @Bean
        @ConditionalOnMissingBean
        public RequestInterceptor feignClientInterceptor() {
            return template -> {
                // 获取对象
                ServletRequestAttributes attribute =
                        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attribute == null) {
                    return;
                }
                // 获取请求对象
                HttpServletRequest request = attribute.getRequest();
                // 获取当前请求的header，获取到jwt令牌
                Enumeration<String> headerNames = request.getHeaderNames();
                if (headerNames == null) {
                    return;
                }
                while (headerNames.hasMoreElements()) {
                    String headerName = headerNames.nextElement();
                    String headerValue = request.getHeader(headerName);
                    // 将header向下传递
                    template.header(headerName, headerValue);
                }
            };
        }
        
        @Bean
        @Order(2)
        public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
                                                              RsaPublicAndPrivateKey rsaPublicAndPrivateKey,
                                                              Environment environment,
                                                              Consumer<HttpSecurity> httpConfigurer,
                                                              TemplateConfigProperties templateConfigProperties,
                                                              JsonService jsonService) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable);
            TemplateConfigProperties.SecurityConfig security = templateConfigProperties.getSecurity();
            if (!security.isEnable()) {
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
                authorize.requestMatchers(permitUrlsArray)
                         .permitAll()
                         .anyRequest()
                         .access(new RequestAuthorizationManager());
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
                String jsonString = jsonService.serialize(error);
                response.setContentType(ContentType.APPLICATION_JSON.getType());
                try {
                    ServletOutputStream outputStream = response.getOutputStream();
                    outputStream.write(jsonString.getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                } catch (IOException e) {
                    log.error("返回异常结果给前端时出现异常", e);
                }
            }));
            if (httpConfigurer != null) {
                httpConfigurer.accept(http);
            }
            return http.build();
        }
    }
}
