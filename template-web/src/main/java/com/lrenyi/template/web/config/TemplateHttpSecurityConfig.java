package com.lrenyi.template.web.config;

import com.lrenyi.template.core.config.properties.CustomSecurityConfigProperties;
import com.lrenyi.template.core.util.SpringContextUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.DispatcherType;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableWebSecurity
public class TemplateHttpSecurityConfig {
    
    @Resource
    private CustomSecurityConfigProperties securityConfig;
    private Consumer<HttpSecurity> httpConfigurer;
    
    @Autowired(required = false)
    public void setHttpConfigurer(Consumer<HttpSecurity> httpConfigurer) {
        this.httpConfigurer = httpConfigurer;
    }
    
    @Bean
    @ConditionalOnMissingBean(RolePermissionService.class)
    public RolePermissionService templateAuthorization() {
        return new DefaultRolePermissionService();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public RsaPublicAndPrivateKey rsaPublicAndPrivateKey() {
        return new TemplateRsaPublicAndPrivateKey();
    }
    
    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
            RsaPublicAndPrivateKey rsaPublicAndPrivateKey,
            Environment environment) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable);
        if (!securityConfig.isEnable()) {
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
            return http.build();
        }
        String appName = environment.getProperty("spring.application.name");
        Set<String> permitUrlsOfApp = securityConfig.getDefaultPermitUrls();
        Map<String, Set<String>> permitUrls = securityConfig.getPermitUrls();
        Set<String> set = permitUrls.get(appName);
        if (set != null) {
            permitUrlsOfApp.addAll(set);
        }
        log.info("the permit urls of service {} is: {}",
                appName,
                String.join(",", permitUrlsOfApp)
        );
        http.authorizeHttpRequests(authorize -> {
            String[] permitUrlsArray = permitUrlsOfApp.toArray(new String[0]);
            authorize.dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR)
                    .permitAll();
            authorize.requestMatchers(permitUrlsArray)
                    .permitAll()
                    .anyRequest()
                    .access(new RequestAuthorizationManager());
        });
        CustomSecurityConfigProperties.OpaqueToken opaqueToken = securityConfig.getOpaqueToken();
        if (opaqueToken.isEnable()) {
            http.oauth2ResourceServer((oauth2) -> oauth2.opaqueToken((opaque) -> {
                opaque.introspectionUri(opaqueToken.getIntrospectionUri())
                        .introspectionClientCredentials(opaqueToken.getIntrospectionClientId(),
                                opaqueToken.getIntrospectionClientSecret()
                        );
            }));
        } else {
            // @formatter:off
            if (securityConfig.isLocalJwtPublicKey()) {
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
                    String domain = securityConfig.getNetJwtPublicKeyDomain();
                    char c = domain.charAt(domain.length() - 1);
                    if (c == '/') {
                        domain = domain.substring(0, domain.length() - 1);
                    }
                    jwt.jwkSetUri(domain + "/jwt/public/key");
                }));
            }
            // @formatter:on
        }
        http.exceptionHandling((exceptionHandling) -> exceptionHandling.accessDeniedHandler(new CustomAccessDeniedHandler()));
        if (httpConfigurer != null) {
            httpConfigurer.accept(http);
        }
        return http.build();
    }
}
