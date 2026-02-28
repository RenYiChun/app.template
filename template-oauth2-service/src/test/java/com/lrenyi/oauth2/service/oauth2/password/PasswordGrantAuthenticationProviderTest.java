package com.lrenyi.oauth2.service.oauth2.password;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PasswordGrantAuthenticationProvider 单元测试
 */
@ExtendWith(MockitoExtension.class)
class PasswordGrantAuthenticationProviderTest {
    
    @Mock
    private OAuth2AuthorizationService authorizationService;
    
    @Mock
    private OAuth2TokenGenerator<?> tokenGenerator;
    
    @Mock
    private PasswordEncoder passwordEncoder;
    
    @Mock
    private UserDetailsService userDetailsService;
    
    private PasswordGrantAuthenticationProvider provider;
    
    @BeforeEach
    void setUp() {
        provider = new PasswordGrantAuthenticationProvider(authorizationService,
                                                           tokenGenerator,
                                                           passwordEncoder,
                                                           userDetailsService
        );
    }
    
    @Test
    void supports_passwordGrantToken_returnsTrue() {
        assertTrue(provider.supports(PasswordGrantAuthenticationToken.class));
    }
    
    @Test
    void supports_otherAuthentication_returnsFalse() {
        assertFalse(provider.supports(Authentication.class));
    }
    
    @Test
    void supports_passwordGrantTokenInstance_returnsTrue() {
        PasswordGrantAuthenticationToken token = createToken("user", "pass");
        assertTrue(provider.supports(token.getClass()));
    }
    
    private static PasswordGrantAuthenticationToken createToken(String username, String password) {
        Map<String, Object> params =
                Map.of(OAuth2ParameterNames.USERNAME, username, OAuth2ParameterNames.PASSWORD, password);
        return new PasswordGrantAuthenticationToken(mockClient(), params);
    }
    
    private static OAuth2ClientAuthenticationToken mockClient() {
        RegisteredClient client = RegisteredClient.withId("id")
                                                  .clientId("client")
                                                  .clientSecret("secret")
                                                  .authorizationGrantType(AuthorizationGrantType.PASSWORD)
                                                  .build();
        OAuth2ClientAuthenticationToken token =
                new OAuth2ClientAuthenticationToken(client, ClientAuthenticationMethod.CLIENT_SECRET_BASIC, "client");
        token.setAuthenticated(true);
        return token;
    }
}
