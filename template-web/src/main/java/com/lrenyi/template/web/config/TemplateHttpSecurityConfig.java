package com.lrenyi.template.web.config;

import com.lrenyi.template.core.config.properties.CustomSecurityConfigProperties;
import com.lrenyi.template.core.util.StringUtils;
import com.lrenyi.template.web.authorization.CustomAccessDeniedHandler;
import com.lrenyi.template.web.authorization.DefaultTemplateAuthorization;
import com.lrenyi.template.web.authorization.RequestAuthorizationManager;
import com.lrenyi.template.web.authorization.RsaPublicAndPrivateKey;
import com.lrenyi.template.web.authorization.TemplateAuthorization;
import jakarta.annotation.Resource;
import jakarta.servlet.DispatcherType;
import java.security.interfaces.RSAPublicKey;
import java.util.Set;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.NullRequestCache;
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
    @ConditionalOnMissingBean(TemplateAuthorization.class)
    public TemplateAuthorization templateAuthorization() {
        return new DefaultTemplateAuthorization();
    }
    
    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
                                                          RsaPublicAndPrivateKey rsaPublicAndPrivateKey) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable);
        if (!securityConfig.isEnable()) {
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        } else {
            configHttpSecurityConfig(http, rsaPublicAndPrivateKey);
        }
        if (httpConfigurer != null) {
            httpConfigurer.accept(http);
        }
        return http.build();
    }
    
    private void configHttpSecurityConfig(HttpSecurity http,
                                          RsaPublicAndPrivateKey rsaPublicAndPrivateKey) throws Exception {
        Set<String> allPermitUrls = securityConfig.getAllPermitUrls();
        log.info("the all permit urls is: {}", String.join(",", allPermitUrls));
        http.authorizeHttpRequests(authorize -> {
            String[] permitUrlsArray = allPermitUrls.toArray(new String[0]);
            authorize.dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll();
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
        Customizer<FormLoginConfigurer<HttpSecurity>> loginCustomizer = Customizer.withDefaults();
        String loginPage = securityConfig.getCustomizeLoginPage();
        if (StringUtils.hasLength(loginPage)) {
            loginCustomizer = form -> form.loginPage(loginPage);
        }
        http.requestCache((cache) -> cache.requestCache(new NullRequestCache()));
        http.sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                                                   .maximumSessions(1)
                                                   .maxSessionsPreventsLogin(true));
        
        http.formLogin(loginCustomizer);
        http.exceptionHandling((exceptionHandling) -> exceptionHandling.accessDeniedHandler(new CustomAccessDeniedHandler()));
    }
}
