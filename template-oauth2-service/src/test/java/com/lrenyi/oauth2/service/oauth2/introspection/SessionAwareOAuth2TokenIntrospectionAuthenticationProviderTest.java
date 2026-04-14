package com.lrenyi.oauth2.service.oauth2.introspection;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionAwareOAuth2TokenIntrospectionAuthenticationProviderTest {
    
    @Test
    void authenticateDefaultsToReadonlyLookup() {
        RegisteredClient registeredClient = registeredClient();
        RegisteredClientRepository registeredClientRepository = mock(RegisteredClientRepository.class);
        org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService authorizationService =
                mock(org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService.class);
        SessionAwareOAuth2TokenIntrospectionAuthenticationProvider provider =
                new SessionAwareOAuth2TokenIntrospectionAuthenticationProvider(registeredClientRepository,
                                                                               authorizationService);
        OAuth2Authorization authorization = authorization(registeredClient);
        when(authorizationService.findByToken("access-token", null)).thenReturn(authorization);
        when(registeredClientRepository.findById("client-row-id")).thenReturn(registeredClient);
        
        OAuth2ClientAuthenticationToken clientPrincipal = new OAuth2ClientAuthenticationToken(registeredClient,
                ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                "secret");
        OAuth2TokenIntrospectionAuthenticationToken authentication =
                new OAuth2TokenIntrospectionAuthenticationToken("access-token", clientPrincipal, null, Map.of());
        
        OAuth2TokenIntrospectionAuthenticationToken result =
                (OAuth2TokenIntrospectionAuthenticationToken) provider.authenticate(authentication);
        
        verify(authorizationService).findByToken("access-token", null);
        assertEquals(Boolean.TRUE, result.getTokenClaims().getClaims().get("active"));
    }
    
    @Test
    void authenticateTouchesSessionWhenRequested() {
        RegisteredClient registeredClient = registeredClient();
        RegisteredClientRepository registeredClientRepository = mock(RegisteredClientRepository.class);
        org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService authorizationService =
                mock(org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService.class);
        SessionAwareOAuth2TokenIntrospectionAuthenticationProvider provider =
                new SessionAwareOAuth2TokenIntrospectionAuthenticationProvider(registeredClientRepository,
                                                                               authorizationService);
        OAuth2Authorization authorization = authorization(registeredClient);
        when(authorizationService.findByToken("access-token", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(authorization);
        when(registeredClientRepository.findById("client-row-id")).thenReturn(registeredClient);
        
        OAuth2ClientAuthenticationToken clientPrincipal = new OAuth2ClientAuthenticationToken(registeredClient,
                ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                "secret");
        OAuth2TokenIntrospectionAuthenticationToken authentication =
                new OAuth2TokenIntrospectionAuthenticationToken("access-token",
                        clientPrincipal,
                        null,
                        Map.of(SessionAwareOAuth2TokenIntrospectionAuthenticationProvider.TOUCH_SESSION_PARAMETER_NAME,
                                "true"));
        
        provider.authenticate(authentication);
        
        verify(authorizationService).findByToken("access-token", OAuth2TokenType.ACCESS_TOKEN);
    }
    
    private static RegisteredClient registeredClient() {
        return RegisteredClient.withId("client-row-id")
                               .clientId("client-id")
                               .clientSecret("secret")
                               .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                               .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                               .scope("read")
                               .build();
    }
    
    private static OAuth2Authorization authorization(RegisteredClient registeredClient) {
        Instant issuedAt = Instant.now().minusSeconds(60);
        Instant expiresAt = Instant.now().plusSeconds(300);
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                "access-token",
                issuedAt,
                expiresAt,
                Set.of("read"));
        return OAuth2Authorization.withRegisteredClient(registeredClient)
                                  .id("auth-id")
                                  .principalName("tester")
                                  .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                                  .token(accessToken)
                                  .build();
    }
}
