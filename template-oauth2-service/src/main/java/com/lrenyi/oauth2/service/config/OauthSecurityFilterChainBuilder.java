package com.lrenyi.oauth2.service.config;

import java.util.Set;
import com.lrenyi.oauth2.service.oauth2.TemplateLogOutHandler;
import com.lrenyi.oauth2.service.oauth2.introspection.SessionAwareOAuth2TokenIntrospectionAuthenticationProvider;
import com.lrenyi.oauth2.service.oauth2.password.PasswordAuthenticationFilter;
import com.lrenyi.oauth2.service.oauth2.password.PasswordGrantAuthenticationConverter;
import com.lrenyi.oauth2.service.oauth2.password.PasswordGrantAuthenticationProvider;
import com.lrenyi.template.core.TemplateConfigProperties;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 构建 OAuth2 授权服务器使用的 {@link SecurityFilterChain}：匹配 /oauth2/* 与 /logout，
 * 配置登录页、CSRF、CORS、登出、白名单、审计/预认证过滤器及 Token/内省/OIDC 端点。
 * <p>
 * 设计说明：依赖通过构造器注入便于测试与不可变性；可选 {@link OAuth2AuditFilter} 通过
 * {@link ObjectProvider} 获取，避免强制依赖审计模块。
 * </p>
 */
@Slf4j
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "app.template.oauth2.enabled", havingValue = "true", matchIfMissing = true)
public class OauthSecurityFilterChainBuilder {
    
    private static final String LOGIN_URL = "/login";
    
    private final TemplateConfigProperties templateConfigProperties;
    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2TokenGenerator<?> tokenGenerator;
    private final TemplateLogOutHandler handler;
    private final AuthenticationFailureHandler templateAuthenticationFailureHandler;
    private final PasswordAuthenticationFilter preAuthenticationFilter;
    private final ObjectProvider<OAuth2AuditFilter> oauth2AuditFilterProvider;
    private final RegisteredClientRepository registeredClientRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserDetailsService userDetailsService;
    
    public SecurityFilterChain build(HttpSecurity http) throws Exception {
        TemplateConfigProperties.SecurityProperties security = templateConfigProperties.getSecurity();
        String loginPage = security.getCustomizeLoginPage();
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();
        RequestMatcher endpointsMatcher = authorizationServerConfigurer.getEndpointsMatcher();
        RequestMatcher combinedMatcher = createCombinedRequestMatcher(endpointsMatcher);
        
        http.securityMatcher(combinedMatcher);
        applyExceptionHandling(http, loginPage);
        applyCsrf(http, endpointsMatcher);
        applyFormLogin(http, loginPage);
        applyCorsAndLogout(http);
        applyAuthorizeHttpRequests(http, security.getAllPermitUrls());
        addAuditAndPreAuthFilters(http);
        http.with(authorizationServerConfigurer, Customizer.withDefaults());
        
        OAuth2AuthorizationServerConfigurer configurer = http.getConfigurer(OAuth2AuthorizationServerConfigurer.class);
        configureTokenEndpoint(configurer);
        configureIntrospectionAndOidc(configurer);
        return http.build();
    }
    
    private static RequestMatcher createCombinedRequestMatcher(RequestMatcher endpointsMatcher) {
        AntPathRequestMatcher postLogOut = new AntPathRequestMatcher("/logout", "POST");
        return new OrRequestMatcher(endpointsMatcher, postLogOut);
    }
    
    private void applyExceptionHandling(HttpSecurity http, String loginPage) throws Exception {
        String loginFormUrl = ensureLoginPagePath(loginPage);
        LoginUrlAuthenticationEntryPoint entryPoint = new LoginUrlAuthenticationEntryPoint(loginFormUrl);
        MediaTypeRequestMatcher htmlMatcher = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        http.exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(entryPoint, htmlMatcher));
    }
    
    /**
     * 对 OAuth2 端点（/oauth2/token、introspection、OIDC 等）禁用 CSRF 是安全的：
     * 这些端点由客户端凭据或 authorization_code 流程调用，请求携带 client_id/secret 或 POST body，
     * 非 Cookie；浏览器不会自动携带这些凭据，故无 CSRF 风险。表单登录 /login 与 /logout 仍受 CSRF 保护。
     */
    private void applyCsrf(HttpSecurity http, RequestMatcher endpointsMatcher) throws Exception {
        http.csrf(csrf -> csrf.ignoringRequestMatchers(endpointsMatcher)); //NOSONAR
    }
    
    private void applyFormLogin(HttpSecurity http, String loginPage) throws Exception {
        String loginFormUrl = ensureLoginPagePath(loginPage);
        Customizer<FormLoginConfigurer<HttpSecurity>> customizer =
                LOGIN_URL.equals(loginFormUrl) ? Customizer.withDefaults() : form -> form.loginPage(loginFormUrl);
        http.formLogin(customizer);
    }
    
    private void applyCorsAndLogout(HttpSecurity http) throws Exception {
        http.cors(Customizer.withDefaults());
        http.logout(logout -> logout.logoutUrl("/logout").addLogoutHandler(handler).logoutSuccessHandler(handler));
    }
    
    private void applyAuthorizeHttpRequests(HttpSecurity http, Set<String> permitUrls) throws Exception {
        http.authorizeHttpRequests(request -> request.requestMatchers(permitUrls.toArray(new String[0]))
                                                     .permitAll()
                                                     .anyRequest()
                                                     .authenticated());
    }
    
    private void addAuditAndPreAuthFilters(HttpSecurity http) {
        http.addFilterBefore(preAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        OAuth2AuditFilter auditFilter = oauth2AuditFilterProvider.getIfAvailable();
        if (auditFilter != null) {
            http.addFilterBefore(auditFilter, UsernamePasswordAuthenticationFilter.class);
        }
    }
    
    private void configureTokenEndpoint(OAuth2AuthorizationServerConfigurer configurer) {
        configurer.tokenEndpoint(point -> {
            point.errorResponseHandler(templateAuthenticationFailureHandler);
            point.accessTokenRequestConverter(new PasswordGrantAuthenticationConverter());
            point.authenticationProvider(new PasswordGrantAuthenticationProvider(authorizationService,
                                                                                 tokenGenerator,
                                                                                 passwordEncoder,
                                                                                 userDetailsService
            ));
        });
    }
    
    private void configureIntrospectionAndOidc(OAuth2AuthorizationServerConfigurer configurer) {
        configurer.tokenIntrospectionEndpoint(endpoint -> endpoint.authenticationProviders(providers -> {
            providers.removeIf(OAuth2TokenIntrospectionAuthenticationProvider.class::isInstance);
            providers.add(new SessionAwareOAuth2TokenIntrospectionAuthenticationProvider(registeredClientRepository,
                                                                                         authorizationService));
        }));
        configurer.oidc(Customizer.withDefaults());
    }
    
    /**
     * 仅接受相对路径，拒绝绝对 URL 与 protocol-relative URL，防止开放重定向。
     * 非法值时回退为 "/login" 并打日志。
     */
    private String ensureLoginPagePath(String loginPage) {
        if (!StringUtils.hasLength(loginPage)) {
            return LOGIN_URL;
        }
        if (loginPage.startsWith("//") || loginPage.contains("://")) {
            log.warn("[安全] 登录页不允许配置为绝对 URL，已忽略 app.template.security.customize-login-page={}",
                     loginPage
            );
            return LOGIN_URL;
        }
        if (!loginPage.startsWith("/")) {
            log.warn("[安全] 登录页必须为以 / 开头的路径，已忽略 app.template.security.customize-login-page={}",
                     loginPage
            );
            return LOGIN_URL;
        }
        return loginPage;
    }
}
