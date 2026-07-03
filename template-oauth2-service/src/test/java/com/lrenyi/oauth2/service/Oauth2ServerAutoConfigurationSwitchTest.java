package com.lrenyi.oauth2.service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import com.lrenyi.oauth2.service.oauth2.TemplateAuthenticationFailureHandler;
import com.lrenyi.oauth2.service.oauth2.TemplateLogOutHandler;
import com.lrenyi.oauth2.service.oauth2.password.PasswordAuthenticationFilter;
import com.lrenyi.template.api.config.RsaPublicAndPrivateKey;
import com.lrenyi.template.core.CoreAutoConfiguration;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.server.servlet.OAuth2AuthorizationServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

class Oauth2ServerAutoConfigurationSwitchTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    CoreAutoConfiguration.class,
                    Oauth2ServerAutoConfiguration.class
            ))
            .withUserConfiguration(TestRsaConfiguration.class);

    @Test
    void oauth2RuntimeComponentsStayDisabledWhenAppTemplateSwitchIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(OAuth2AuthorizationService.class);
            assertThat(context).hasSingleBean(JWKSource.class);
            assertThat(context).hasSingleBean(JwtDecoder.class);
            assertThat(context).hasSingleBean(OAuth2TokenGenerator.class);
            assertThat(context).doesNotHaveBean(SecurityFilterChain.class);
            assertThat(context).doesNotHaveBean(PasswordAuthenticationFilter.class);
            assertThat(context).doesNotHaveBean(TemplateAuthenticationFailureHandler.class);
            assertThat(context).doesNotHaveBean(TemplateLogOutHandler.class);
        });
    }

    @Test
    void appTemplateDisabledKeepsOauth2SupportBeansAndSkipsRuntimeComponents() {
        contextRunner.withPropertyValues("app.template.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(OAuth2AuthorizationService.class);
            assertThat(context).doesNotHaveBean(RegisteredClientRepository.class);
            assertThat(context).hasSingleBean(JWKSource.class);
            assertThat(context).hasSingleBean(JwtDecoder.class);
            assertThat(context).hasSingleBean(OAuth2TokenGenerator.class);
            assertThat(context).doesNotHaveBean(SecurityFilterChain.class);
            assertThat(context).doesNotHaveBean(PasswordAuthenticationFilter.class);
            assertThat(context).doesNotHaveBean(TemplateAuthenticationFailureHandler.class);
            assertThat(context).doesNotHaveBean(TemplateLogOutHandler.class);
        });
    }

    @Test
    void configuredClientsRegisterDefaultMemoryRepository() {
        contextRunner.withPropertyValues(
                "app.template.enabled=false",
                "spring.security.oauth2.authorizationserver.client.test.registration.client-id=test-client",
                "spring.security.oauth2.authorizationserver.client.test.registration.client-secret={noop}secret",
                "spring.security.oauth2.authorizationserver.client.test.registration.client-authentication-methods=client_secret_basic",
                "spring.security.oauth2.authorizationserver.client.test.registration.authorization-grant-types=client_credentials",
                "spring.security.oauth2.authorizationserver.client.test.registration.scopes=read"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RegisteredClientRepository.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OAuth2AuthorizationServerProperties.class)
    static class TestRsaConfiguration {

        @Bean
        RsaPublicAndPrivateKey rsaPublicAndPrivateKey() {
            return new GeneratedRsaPublicAndPrivateKey();
        }
    }

    static class GeneratedRsaPublicAndPrivateKey extends RsaPublicAndPrivateKey {

        private final KeyPair keyPair;

        GeneratedRsaPublicAndPrivateKey() {
            this.keyPair = generateKeyPair();
        }

        @Override
        public RSAPublicKey templateRSAPublicKey() {
            return (RSAPublicKey) keyPair.getPublic();
        }

        @Override
        public RSAPrivateKey templateRSAPrivateKey() {
            return (RSAPrivateKey) keyPair.getPrivate();
        }

        private static KeyPair generateKeyPair() {
            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                return generator.generateKeyPair();
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("RSA KeyPairGenerator unavailable", e);
            }
        }
    }
}
