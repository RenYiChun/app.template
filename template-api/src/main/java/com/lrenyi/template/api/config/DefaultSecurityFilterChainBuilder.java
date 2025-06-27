package com.lrenyi.template.api.config;

import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.json.JsonService;
import com.lrenyi.template.core.util.Result;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DefaultSecurityFilterChainBuilder {
    private RsaPublicAndPrivateKey rsaPublicAndPrivateKey;
    private Environment environment;
    private ObjectProvider<Consumer<HttpSecurity>> httpConfigurerProvider;
    private TemplateConfigProperties templateConfigProperties;
    private ObjectProvider<JsonService> jsonServiceProvider;
    private JwtAuthenticationConverter jwtAuthenticationConverter;
    private OpaqueTokenIntrospector opaqueTokenIntrospector;
    
    public SecurityFilterChain build(HttpSecurity http) throws Exception {
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
        // 检查是否启用了不透明token（Opaque Token）认证模式
        if (opaqueToken.isEnabled()) {
            // @formatter:off
            // 配置OAuth2资源服务器以支持不透明token验证
            http.oauth2ResourceServer((oauth2) -> oauth2.opaqueToken(opaque -> opaque.introspector(opaqueTokenIntrospector)));
        } else {
            if (security.isLocalJwtPublicKey()) {
                // 加载本地公钥
                RSAPublicKey publicKey = rsaPublicAndPrivateKey.templateRSAPublicKey();
                // 使用公钥创建JwtDecoder
                JwtDecoder jwtDecoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
                // 配置资源服务器
                http.oauth2ResourceServer(
                        oauth2ResourceServer -> oauth2ResourceServer.jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter))
                );
            } else {
                http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
                    String domain = security.getNetJwtPublicKeyDomain();
                    char c = domain.charAt(domain.length() - 1);
                    if (c == '/') {
                        domain = domain.substring(0, domain.length() - 1);
                    }
                    jwt.jwkSetUri(domain + "/jwt/public/key").jwtAuthenticationConverter(jwtAuthenticationConverter);
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
    
    @Autowired
    public void setRsaPublicAndPrivateKey(RsaPublicAndPrivateKey rsaPublicAndPrivateKey) {
        this.rsaPublicAndPrivateKey = rsaPublicAndPrivateKey;
    }
    
    @Autowired
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
    
    @Autowired
    public void setHttpConfigurerProvider(ObjectProvider<Consumer<HttpSecurity>> httpConfigurerProvider) {
        this.httpConfigurerProvider = httpConfigurerProvider;
    }
    
    @Autowired
    public void setTemplateConfigProperties(TemplateConfigProperties templateConfigProperties) {
        this.templateConfigProperties = templateConfigProperties;
    }
    
    @Autowired
    public void setJsonServiceProvider(ObjectProvider<JsonService> jsonServiceProvider) {
        this.jsonServiceProvider = jsonServiceProvider;
    }
    
    @Autowired
    public void setJwtAuthenticationConverter(JwtAuthenticationConverter jwtAuthenticationConverter) {
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
    }
    
    @Autowired
    public void setOpaqueTokenIntrospector(OpaqueTokenIntrospector opaqueTokenIntrospector) {
        this.opaqueTokenIntrospector = opaqueTokenIntrospector;
    }
}
