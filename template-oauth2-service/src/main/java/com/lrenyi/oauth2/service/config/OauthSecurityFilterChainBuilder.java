package com.lrenyi.oauth2.service.config;

import com.lrenyi.oauth2.service.oauth2.TemplateLogOutHandler;
import com.lrenyi.oauth2.service.oauth2.password.PasswordGrantAuthenticationConverter;
import com.lrenyi.oauth2.service.oauth2.password.PasswordGrantAuthenticationProvider;
import com.lrenyi.oauth2.service.oauth2.password.PasswordAuthenticationFilter;
import com.lrenyi.oauth2.service.oauth2.password.RbacUserDetailsService;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.util.StringUtils;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.template.oauth2.enabled", havingValue = "true", matchIfMissing = true)
public class OauthSecurityFilterChainBuilder {
    private TemplateConfigProperties templateConfigProperties;
    private OAuth2AuthorizationService authorizationService;
    private OAuth2TokenGenerator<?> tokenGenerator;
    private TemplateLogOutHandler handler;
    private AuthenticationFailureHandler templateAuthenticationFailureHandler;
    private PasswordAuthenticationFilter preAuthenticationFilter;
    private PasswordEncoder passwordEncoder;
    private UserDetailsService userDetailsService;
    
    public SecurityFilterChain build(HttpSecurity http) throws Exception {
        TemplateConfigProperties.SecurityProperties security = templateConfigProperties.getSecurity();
        String loginPage = security.getCustomizeLoginPage();
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();
        RequestMatcher endpointsMatcher = authorizationServerConfigurer.getEndpointsMatcher();
        
        // 配置安全匹配器，只匹配OAuth2相关的端点
        String[] uris = {"/oauth2/**", "/login/**", "/logout", "/jwks", "/jwt/public/key"};
        http.securityMatcher(endpointsMatcher).exceptionHandling((exceptions) -> {
            String loginFormUrl = StringUtils.hasLength(loginPage) ? loginPage : "/login";
            LoginUrlAuthenticationEntryPoint point = new LoginUrlAuthenticationEntryPoint(loginFormUrl);
            MediaTypeRequestMatcher matcher = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
            exceptions.defaultAuthenticationEntryPointFor(point, matcher);
        });
        http.csrf((csrf) -> csrf.ignoringRequestMatchers(new RequestMatcher[]{endpointsMatcher}));
        Customizer<FormLoginConfigurer<HttpSecurity>> loginCustomizer = Customizer.withDefaults();
        if (StringUtils.hasLength(loginPage)) {
            loginCustomizer = form -> form.loginPage(loginPage);
        }
        http.formLogin(loginCustomizer);
        http.logout(form -> form.addLogoutHandler(handler));
        
        Set<String> allPermitUrls = security.getAllPermitUrls();
        http.authorizeHttpRequests(request -> request.requestMatchers(allPermitUrls.toArray(new String[0]))
                                                     .permitAll()
                                                     .anyRequest()
                                                     .authenticated());
        http.addFilterBefore(preAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        http.with(authorizationServerConfigurer, Customizer.withDefaults());
        
        OAuth2AuthorizationServerConfigurer configurer = http.getConfigurer(OAuth2AuthorizationServerConfigurer.class);
        configurer.tokenEndpoint(point -> {
            point.errorResponseHandler(templateAuthenticationFailureHandler);
            point.accessTokenRequestConverter(new PasswordGrantAuthenticationConverter());
            point.authenticationProvider(new PasswordGrantAuthenticationProvider(authorizationService,
                                                                                 tokenGenerator,
                                                                                 passwordEncoder,
                                                                                 userDetailsService
            ));
        });
        // 配置内省端点 - 启用标准 /oauth2/introspect
        configurer.tokenIntrospectionEndpoint(Customizer.withDefaults());
        configurer.oidc(Customizer.withDefaults());
        return http.build();
    }
    
    @Autowired
    public void setTemplateConfigProperties(TemplateConfigProperties templateConfigProperties) {
        this.templateConfigProperties = templateConfigProperties;
    }
    
    @Autowired
    public void setAuthorizationService(OAuth2AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }
    
    @Autowired
    public void setTokenGenerator(OAuth2TokenGenerator<?> tokenGenerator) {
        this.tokenGenerator = tokenGenerator;
    }
    
    @Autowired
    public void setHandler(TemplateLogOutHandler handler) {
        this.handler = handler;
    }
    
    @Autowired
    public void setTemplateAuthenticationFailureHandler(AuthenticationFailureHandler templateAuthenticationFailureHandler) {
        this.templateAuthenticationFailureHandler = templateAuthenticationFailureHandler;
    }
    
    @Autowired
    public void setPreAuthenticationFilter(PasswordAuthenticationFilter preAuthenticationFilter) {
        this.preAuthenticationFilter = preAuthenticationFilter;
    }
    
    @Autowired
    public void setPasswordEncoder(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }
    
    @Autowired
    public void setUserDetailsService(RbacUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }
}
