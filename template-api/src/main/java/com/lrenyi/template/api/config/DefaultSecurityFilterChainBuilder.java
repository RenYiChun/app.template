package com.lrenyi.template.api.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import com.lrenyi.template.api.feign.InternalRequestMatcher;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.TemplateConfigProperties.SecurityProperties;
import com.lrenyi.template.core.json.JsonService;
import com.lrenyi.template.core.util.MCode;
import com.lrenyi.template.core.util.Result;
import com.nimbusds.common.contenttype.ContentType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 框架默认 Security 过滤链构建器。
 * <p>
 * 职责：CSRF 关闭、白名单授权、OAuth2 资源服务器（JWT/Opaque Token）、401/403 异常处理、配置化 CORS。
 * 跨域：{@code app.template.security.cors.enabled=true} 时根据 cors 配置项自动启用，无需写 Java。
 * 可通过 {@code ObjectProvider&lt;Consumer&lt;HttpSecurity&gt;&gt;} 注入自定义配置覆盖或扩展。
 * </p>
 */
@Slf4j
@Component
@AllArgsConstructor
public class DefaultSecurityFilterChainBuilder {
    
    private static final String AUTH_FAILURE_METRIC = "app.template.http.auth.failure";
    private static final String DEFAULT_APP_NAME = "default";
    
    private final RsaPublicAndPrivateKey rsaPublicAndPrivateKey;
    private final Environment environment;
    private final ObjectProvider<Consumer<HttpSecurity>> httpConfigurerProvider;
    private final TemplateConfigProperties templateConfigProperties;
    private final ObjectProvider<JsonService> jsonServiceProvider;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final OpaqueTokenIntrospector opaqueTokenIntrospector;
    private final MeterRegistry meterRegistry;
    
    public SecurityFilterChain build(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable);
        TemplateConfigProperties.SecurityProperties security = templateConfigProperties.getSecurity();
        if (isSecurityDisabled()) {
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
            return http.build();
        }
        Set<String> permitUrls = collectPermitUrls(security);
        configureAuthorizationRules(http, permitUrls);
        configureOAuth2ResourceServer(http, security);
        configureExceptionHandling(http);
        configureCorsIfEnabled(http, security);
        applyCustomConfigurer(http);
        return http.build();
    }
    
    private boolean isSecurityDisabled() {
        return !templateConfigProperties.isSecurityEffectivelyEnabled();
    }
    
    private Set<String> collectPermitUrls(TemplateConfigProperties.SecurityProperties security) {
        String appName = environment.getProperty("spring.application.name");
        if (!StringUtils.hasLength(appName)) {
            appName = DEFAULT_APP_NAME;
        }
        Set<String> urls = new HashSet<>(security.getDefaultPermitUrls());
        Map<String, Set<String>> permitUrls = security.getPermitUrls();
        Set<String> appUrls = permitUrls != null ? permitUrls.get(appName) : null;
        if (appUrls != null) {
            urls.addAll(appUrls);
        }
        log.info("the permit URLs of service {} is: {}", appName, String.join(",", urls));
        return urls;
    }
    
    private void configureAuthorizationRules(HttpSecurity http, Set<String> permitUrls) throws Exception {
        String[] permitUrlsArray = permitUrls.toArray(new String[0]);
        http.authorizeHttpRequests(authorize -> {
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry =
                    authorize.dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR)
                             .permitAll()
                             .requestMatchers(permitUrlsArray)
                             .permitAll();
            TemplateConfigProperties.FeignProperties feign = templateConfigProperties.getFeign();
            if (feign.isNotOauth()) {
                registry =
                        registry.requestMatchers(new InternalRequestMatcher(feign.getInternalCallAllowedIpPatterns()))
                                .permitAll();
            }
            registry.anyRequest().authenticated();
        });
    }
    
    private void configureOAuth2ResourceServer(HttpSecurity http,
            TemplateConfigProperties.SecurityProperties security) throws Exception {
        TemplateConfigProperties.OAuth2Config.OpaqueTokenConfig opaqueToken =
                templateConfigProperties.getOauth2().getOpaqueToken();
        if (opaqueToken.isEnabled()) {
            http.oauth2ResourceServer(oauth2 -> oauth2.opaqueToken(opaque -> opaque.introspector(opaqueTokenIntrospector)));
            return;
        }
        if (security.isLocalJwtPublicKey()) {
            guardLocalJwtInProduction(security);
            configureLocalJwtResourceServer(http);
        } else {
            configureRemoteJwtResourceServer(http, security);
        }
    }
    
    private void configureExceptionHandling(HttpSecurity http) throws Exception {
        http.exceptionHandling(exceptionHandling -> exceptionHandling.authenticationEntryPoint(
                createAuthenticationEntryPoint()).accessDeniedHandler(createAccessDeniedHandler()));
    }
    
    /**
     * 当 app.template.security.cors.enabled=true 时，根据配置项构建 CorsConfigurationSource 并启用 CORS，
     * 用户无需编写 Java 代码；完全自定义时可将 enabled 置为 false 并通过 httpConfigurerProvider 注入。
     */
    private void configureCorsIfEnabled(HttpSecurity http,
            TemplateConfigProperties.SecurityProperties security) throws Exception {
        TemplateConfigProperties.CorsProperties cors = security.getCors();
        if (cors == null || !cors.isEnabled()) {
            return;
        }
        CorsConfiguration config = new CorsConfiguration();
        List<String> allowedOriginPatterns = cors.getAllowedOriginPatterns();
        if (allowedOriginPatterns != null && !allowedOriginPatterns.isEmpty()) {
            config.setAllowedOriginPatterns(allowedOriginPatterns);
        }
        List<String> allowedMethods = cors.getAllowedMethods();
        if (allowedMethods != null && !allowedMethods.isEmpty()) {
            config.setAllowedMethods(allowedMethods);
        }
        List<String> allowedHeaders = cors.getAllowedHeaders();
        if (allowedHeaders != null && !allowedHeaders.isEmpty()) {
            config.setAllowedHeaders(allowedHeaders);
        }
        Boolean allowCredentials = cors.getAllowCredentials();
        if (allowCredentials != null) {
            config.setAllowCredentials(allowCredentials);
        }
        Long maxAge = cors.getMaxAge();
        if (maxAge != null && maxAge > 0) {
            config.setMaxAge(maxAge);
        }
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        http.cors(c -> c.configurationSource(source));
    }
    
    private void applyCustomConfigurer(HttpSecurity http) {
        Consumer<HttpSecurity> consumer = httpConfigurerProvider.getIfAvailable();
        if (consumer != null) {
            consumer.accept(http);
        }
    }
    
    private void guardLocalJwtInProduction(TemplateConfigProperties.SecurityProperties security) {
        Set<String> activeProfiles = Arrays.stream(environment.getActiveProfiles()).collect(Collectors.toSet());
        boolean isProduction = security.getProductionProfiles().stream().anyMatch(activeProfiles::contains);
        if (isProduction) {
            throw new IllegalStateException("生产环境禁止使用本地 JWT 公钥。请设置 app.template.security.local-jwt-public-key=false，"
                                                    + "并配置 net-jwt-public-key-uri（完整 URI）或 "
                                                    + "net-jwt-public-key-domain（域名），"
                                                    + "或使用 Opaque Token 模式。当前激活的 profile: " + Arrays.toString(
                    environment.getActiveProfiles()));
        }
    }
    
    private void configureLocalJwtResourceServer(HttpSecurity http) throws Exception {
        RSAPublicKey publicKey = rsaPublicAndPrivateKey.templateRSAPublicKey();
        JwtDecoder jwtDecoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        http.oauth2ResourceServer(ors -> ors.jwt(jwt -> jwt.decoder(jwtDecoder)
                                                           .jwtAuthenticationConverter(jwtAuthenticationConverter)));
    }
    
    private void configureRemoteJwtResourceServer(HttpSecurity http,
            TemplateConfigProperties.SecurityProperties security) throws Exception {
        String jwkSetUri = resolveJwkSetUri(security);
        http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwkSetUri(jwkSetUri)
                                                                 .jwtAuthenticationConverter(jwtAuthenticationConverter)));
    }
    
    private AuthenticationEntryPoint createAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            String reason = inferAuthFailureReason(authException);
            incrementAuthFailureCounter(reason);
            writeAuthErrorJsonResponse(request, response, MCode.NO_PERMISSIONS);
        };
    }
    
    private AccessDeniedHandler createAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            incrementAuthFailureCounter("ACCESS_DENIED");
            writeAuthErrorJsonResponse(request, response, MCode.ACCESS_DENIED);
        };
    }
    
    /**
     * 解析 JWT 公钥 JWK Set 的 URI。
     * 优先使用 netJwtPublicKeyUri（完整 URI）；否则用 netJwtPublicKeyDomain + netJwtPublicKeyPath 拼接。
     * 路径可通过 app.template.security.net-jwt-public-key-path 自定义。
     *
     * @throws IllegalStateException 当 domain 未配置且非完整 URI 模式时
     */
    private String resolveJwkSetUri(SecurityProperties security) {
        if (StringUtils.hasLength(security.getNetJwtPublicKeyUri())) {
            return security.getNetJwtPublicKeyUri();
        }
        String domain = security.getNetJwtPublicKeyDomain();
        if (!StringUtils.hasLength(domain)) {
            throw new IllegalStateException("远程 JWT 模式下未配置 net-jwt-public-key-uri 或 net-jwt-public-key-domain");
        }
        String path = security.getNetJwtPublicKeyPath();
        if (!StringUtils.hasText(path)) {
            path = SecurityProperties.DEFAULT_NET_JWT_PUBLIC_KEY_PATH;
        }
        return UriComponentsBuilder.fromUriString(domain).path(path).build().toUriString();
    }
    
    private String inferAuthFailureReason(AuthenticationException authException) {
        String msg = authException.getMessage();
        if (msg == null) {
            return "MISSING_TOKEN";
        }
        String lower = msg.toLowerCase();
        if (lower.contains("expired")) {
            return "EXPIRED_TOKEN";
        }
        if (lower.contains("invalid")) {
            return "INVALID_TOKEN";
        }
        return "MISSING_TOKEN";
    }
    
    private void incrementAuthFailureCounter(String reason) {
        Counter.builder(AUTH_FAILURE_METRIC).tag("reason", reason).register(meterRegistry).increment();
    }
    
    /**
     * 统一写入 401/403 的 JSON 响应，与 GlobalExceptionHandler 的 Result 格式一致。
     */
    private void writeAuthErrorJsonResponse(HttpServletRequest request, HttpServletResponse response, MCode mcode) {
        if (response.isCommitted()) {
            return;
        }
        JsonService jsonService = jsonServiceProvider.getIfAvailable();
        if (jsonService != null) {
            Result<String> error = new Result<>();
            error.setCode(mcode.getCode());
            error.setData(request.getRequestURI());
            error.setMessage(mcode.getMessage());
            String jsonString = jsonService.serialize(error);
            response.setStatus(mcode.getCode());
            response.setContentType(ContentType.APPLICATION_JSON.getType());
            try {
                ServletOutputStream outputStream = response.getOutputStream();
                outputStream.write(jsonString.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            } catch (IOException e) {
                log.error("返回异常结果给前端时出现异常", e);
            }
        } else {
            try {
                response.sendError(mcode.getCode(), mcode.getMessage());
            } catch (IOException e) {
                log.error("sendError 失败: {}", e.getMessage());
            }
        }
    }
}
