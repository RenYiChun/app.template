package com.lrenyi.oauth2.service;

import com.lrenyi.oauth2.service.config.ConfigImportSelector;
import com.lrenyi.oauth2.service.config.OAuth2AuthorizationServerPropertiesMapper;
import com.lrenyi.oauth2.service.config.OauthSecurityFilterChainBuilder;
import com.lrenyi.oauth2.service.oauth2.password.PasswordGrantAuthenticationToken;
import com.lrenyi.oauth2.service.oauth2.token.UuidOAuth2RefreshTokenGenerator;
import com.lrenyi.oauth2.service.oauth2.token.UuidOAuth2TokenGenerator;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.oauth2.service.oauth2.password.PasswordAuthenticationFilter;
import com.lrenyi.oauth2.service.oauth2.password.PreAuthenticationChecker;
import com.lrenyi.template.api.config.RsaPublicAndPrivateKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.oauth2.server.servlet.OAuth2AuthorizationServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenClaimsContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;

@Slf4j
@ComponentScan
@Import(ConfigImportSelector.class)
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.template.enabled", havingValue = "true")
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
    @ConditionalOnProperty(name = "app.template.oauth2.enabled", havingValue = "true", matchIfMissing = true)
    public SecurityFilterChain authorizationServerFilterChain(HttpSecurity http,
                                                              OauthSecurityFilterChainBuilder builder) throws Exception {
        return builder.build(http);
    }
    
    @Bean
    public PasswordAuthenticationFilter preAuthenticationFilter(ObjectProvider<PreAuthenticationChecker> preAuthenticationCheckers,
                                                                TemplateConfigProperties templateConfigProperties) {
        return new PasswordAuthenticationFilter(preAuthenticationCheckers, templateConfigProperties);
    }
    
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public OAuth2TokenCustomizer<OAuth2TokenClaimsContext> accessTokenCustomizer() {
        return context -> {
            // 从 AuthorizationGrant 获取额外参数
            if (context.getAuthorizationGrant() instanceof PasswordGrantAuthenticationToken) {
                PasswordGrantAuthenticationToken grantToken = context.getAuthorizationGrant();
                Map<String, Object> parameters = grantToken.getAdditionalParameters();
                String usernameFromParams = (String) parameters.get(OAuth2ParameterNames.USERNAME);
                if (usernameFromParams != null) {
                    // 将用户名添加到 token claims 中
                    context.getClaims().claim(OAuth2TokenIntrospectionClaimNames.USERNAME, usernameFromParams);
                }
            }
        };
    }
    
    @Bean
    @ConditionalOnMissingBean(OAuth2TokenGenerator.class)
    OAuth2TokenGenerator<?> tokenGenerator(JWKSource<SecurityContext> jwkSource,
                                           OAuth2TokenCustomizer<OAuth2TokenClaimsContext> contextOAuth2TokenCustomizer) {
        JwtGenerator jwtGenerator = new JwtGenerator(new NimbusJwtEncoder(jwkSource));
        UuidOAuth2TokenGenerator tokenGenerator = new UuidOAuth2TokenGenerator();
        tokenGenerator.setAccessTokenCustomizer(contextOAuth2TokenCustomizer);
        return new DelegatingOAuth2TokenGenerator(jwtGenerator, tokenGenerator, new UuidOAuth2RefreshTokenGenerator());
    }
}
