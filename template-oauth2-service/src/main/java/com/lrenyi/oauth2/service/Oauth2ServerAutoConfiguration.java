package com.lrenyi.oauth2.service;

import com.lrenyi.oauth2.service.config.ConfigImportSelector;
import com.lrenyi.oauth2.service.config.OAuth2AuthorizationServerPropertiesMapper;
import com.lrenyi.oauth2.service.oauth2.TemplateLogOutHandler;
import com.lrenyi.oauth2.service.oauth2.password.PasswordGrantAuthenticationConverter;
import com.lrenyi.oauth2.service.oauth2.password.PasswordGrantAuthenticationProvider;
import com.lrenyi.oauth2.service.oauth2.token.UuidOAuth2RefreshTokenGenerator;
import com.lrenyi.oauth2.service.oauth2.token.UuidOAuth2TokenGenerator;
import com.lrenyi.template.core.config.properties.CustomSecurityConfigProperties;
import com.lrenyi.template.core.util.StringUtils;
import com.lrenyi.template.web.config.RsaPublicAndPrivateKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.security.oauth2.server.servlet.OAuth2AuthorizationServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.client.RestTemplate;

@Slf4j
@ComponentScan
@Import(ConfigImportSelector.class)
@Configuration(proxyBeanMethods = false)
public class Oauth2ServerAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public OAuth2AuthorizationService authorizationService() {
        return new InMemoryOAuth2AuthorizationService();
    }
    
    @Bean
    @ConditionalOnMissingBean(RegisteredClientRepository.class)
    public RegisteredClientRepository registeredClientRepository(OAuth2AuthorizationServerProperties properties) {
        OAuth2AuthorizationServerPropertiesMapper mapper = new OAuth2AuthorizationServerPropertiesMapper(properties);
        List<RegisteredClient> registeredClients = mapper.asRegisteredClients();
        return new InMemoryRegisteredClientRepository(registeredClients.toArray(new RegisteredClient[0]));
    }
    
    @Bean
    @ConditionalOnMissingBean(JWKSource.class)
    public JWKSource<SecurityContext> jwkSource(RsaPublicAndPrivateKey rsaPublicAndPrivateKey) {
        RSAPublicKey publicKey = rsaPublicAndPrivateKey.templateRSAPublicKey();
        RSAPrivateKey privateKey = rsaPublicAndPrivateKey.templateRSAPrivateKey();
        
        String kid = UUID.randomUUID().toString();
        rsaPublicAndPrivateKey.setKid(kid);
        RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(kid).build();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }
    
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
                                                                      CustomSecurityConfigProperties properties,
                                                                      OAuth2AuthorizationService authorizationService,
                                                                      OAuth2TokenGenerator<?> tokenGenerator,
                                                                      TemplateLogOutHandler handler,
                                                                      AuthenticationFailureHandler templateAuthenticationFailureHandler) throws Exception {
        String loginPage = properties.getCustomizeLoginPage();
        
        // 配置安全匹配器，只匹配OAuth2相关的端点
        http.securityMatcher("/oauth2/**", "/login/**", "/logout", "/.well-known/**", "/jwks", "/jwt/public/key")
            .exceptionHandling((exceptions) -> {
                LoginUrlAuthenticationEntryPoint point = new LoginUrlAuthenticationEntryPoint(StringUtils.hasLength(
                        loginPage) ? loginPage : "/login");
                MediaTypeRequestMatcher matcher = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
                exceptions.defaultAuthenticationEntryPointFor(point, matcher);
            });
        Customizer<FormLoginConfigurer<HttpSecurity>> loginCustomizer = Customizer.withDefaults();
        if (StringUtils.hasLength(loginPage)) {
            loginCustomizer = form -> form.loginPage(loginPage);
        }
        http.formLogin(loginCustomizer);
        http.logout(form -> form.addLogoutHandler(handler));
        
        Set<String> allPermitUrls = properties.getAllPermitUrls();
        http.authorizeHttpRequests(request -> request.requestMatchers(allPermitUrls.toArray(new String[0]))
                                                     .permitAll()
                                                     .anyRequest().authenticated());
        
        http.with(OAuth2AuthorizationServerConfigurer.authorizationServer(), Customizer.withDefaults());
        
        OAuth2AuthorizationServerConfigurer configurer = http.getConfigurer(OAuth2AuthorizationServerConfigurer.class);
        configurer.tokenEndpoint(point -> {
            point.errorResponseHandler(templateAuthenticationFailureHandler);
            point.accessTokenRequestConverter(new PasswordGrantAuthenticationConverter());
            point.authenticationProvider(new PasswordGrantAuthenticationProvider(authorizationService, tokenGenerator));
        });
        configurer.oidc(Customizer.withDefaults());
        return http.build();
    }
    
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }
    
    @Bean
    @ConditionalOnMissingBean(OAuth2TokenGenerator.class)
    OAuth2TokenGenerator<?> tokenGenerator(JWKSource<SecurityContext> jwkSource) {
        JwtGenerator jwtGenerator = new JwtGenerator(new NimbusJwtEncoder(jwkSource));
        return new DelegatingOAuth2TokenGenerator(jwtGenerator,
                                                  new UuidOAuth2TokenGenerator(),
                                                  new UuidOAuth2RefreshTokenGenerator()
        );
    }
}
