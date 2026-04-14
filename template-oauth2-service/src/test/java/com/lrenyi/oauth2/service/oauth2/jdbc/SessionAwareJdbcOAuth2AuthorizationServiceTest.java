package com.lrenyi.oauth2.service.oauth2.jdbc;

import java.time.Instant;
import java.util.Set;
import com.lrenyi.template.core.TemplateConfigProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionAwareJdbcOAuth2AuthorizationServiceTest {

    @Test
    void findByIdDoesNotTouchSessionOrSave() {
        OAuth2AuthorizationService delegate = mock(OAuth2AuthorizationService.class);
        TemplateConfigProperties properties = sessionEnabledProperties();
        SessionAwareJdbcOAuth2AuthorizationService service =
                new SessionAwareJdbcOAuth2AuthorizationService(delegate, properties);
        OAuth2Authorization authorization = authorization();
        when(delegate.findById("auth-id")).thenReturn(authorization);

        OAuth2Authorization found = service.findById("auth-id");

        assertSame(authorization, found);
        verify(delegate).findById("auth-id");
        verify(delegate, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void findByTokenTouchesAccessTokenOnly() {
        OAuth2AuthorizationService delegate = mock(OAuth2AuthorizationService.class);
        TemplateConfigProperties properties = sessionEnabledProperties();
        SessionAwareJdbcOAuth2AuthorizationService service =
                new SessionAwareJdbcOAuth2AuthorizationService(delegate, properties);
        OAuth2Authorization authorization = authorization();
        OAuth2TokenType refreshTokenType = new OAuth2TokenType("refresh_token");
        when(delegate.findByToken("refresh-token", refreshTokenType)).thenReturn(authorization);

        OAuth2Authorization found = service.findByToken("refresh-token", refreshTokenType);

        assertSame(authorization, found);
        verify(delegate).findByToken("refresh-token", refreshTokenType);
        verify(delegate, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void findByTokenExtendsAccessTokenSession() {
        OAuth2AuthorizationService delegate = mock(OAuth2AuthorizationService.class);
        TemplateConfigProperties properties = sessionEnabledProperties();
        SessionAwareJdbcOAuth2AuthorizationService service =
                new SessionAwareJdbcOAuth2AuthorizationService(delegate, properties);
        OAuth2Authorization authorization = authorization();
        when(delegate.findByToken("access-token", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(authorization);

        OAuth2Authorization found = service.findByToken("access-token", OAuth2TokenType.ACCESS_TOKEN);

        verify(delegate).save(found);
        assertEquals(authorization.getAccessToken().getToken().getTokenValue(),
                found.getAccessToken().getToken().getTokenValue());
    }

    private static TemplateConfigProperties sessionEnabledProperties() {
        TemplateConfigProperties properties = new TemplateConfigProperties();
        properties.getSecurity().setSessionIdleTimeout(true);
        properties.getSecurity().setSessionTimeOutSeconds(1800L);
        properties.getSecurity().setTokenMaxLifetimeSeconds(7200L);
        return properties;
    }

    private static OAuth2Authorization authorization() {
        Instant issuedAt = Instant.now().minusSeconds(60);
        Instant expiresAt = Instant.now().plusSeconds(300);
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                "access-token",
                issuedAt,
                expiresAt,
                Set.of("read"));
        RegisteredClient registeredClient = RegisteredClient.withId("client-row-id")
                .clientId("client-id")
                .clientSecret("secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("read")
                .build();
        return OAuth2Authorization.withRegisteredClient(registeredClient)
                .id("auth-id")
                .principalName("tester")
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.CLIENT_CREDENTIALS)
                .token(accessToken)
                .build();
    }
}
