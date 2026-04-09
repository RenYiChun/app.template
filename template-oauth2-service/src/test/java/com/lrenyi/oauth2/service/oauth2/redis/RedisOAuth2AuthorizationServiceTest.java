package com.lrenyi.oauth2.service.oauth2.redis;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.util.Digests;
import com.lrenyi.template.core.util.TemplateConstant;
import io.netty.buffer.ByteBufUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisOAuth2AuthorizationServiceTest {
    
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    
    @Mock
    private HashOperations<String, String, String> hashOperations;
    
    @BeforeEach
    void setUp() {
        doReturn(hashOperations).when(redisTemplate).opsForHash();
    }
    
    @Test
    void findByTokenWithoutTokenTypeDoesNotTouchSession() {
        OAuth2Authorization authorization = authorization();
        stubAuthorizationLookup(authorization);
        RedisOAuth2AuthorizationService service = service();
        
        OAuth2Authorization found = service.findByToken("access-token", null);
        
        assertEquals(authorization.getAccessToken().getToken().getExpiresAt(),
                     found.getAccessToken().getToken().getExpiresAt());
        verify(hashOperations, never()).put(anyString(), anyString(), anyString());
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
    }
    
    @Test
    void findByTokenWithAccessTokenStillTouchesSession() {
        OAuth2Authorization authorization = authorization();
        stubAuthorizationLookup(authorization);
        RedisOAuth2AuthorizationService service = service();
        
        OAuth2Authorization found = service.findByToken("access-token", OAuth2TokenType.ACCESS_TOKEN);
        
        assertTrue(found.getAccessToken().getToken().getExpiresAt()
                        .isAfter(authorization.getAccessToken().getToken().getExpiresAt()));
        verify(hashOperations, atLeastOnce()).put(anyString(), anyString(), anyString());
        verify(redisTemplate, atLeastOnce()).expire(anyString(), anyLong(), eq(TimeUnit.MINUTES));
    }
    
    private RedisOAuth2AuthorizationService service() {
        RedisOAuth2AuthorizationService service = new RedisOAuth2AuthorizationService(redisTemplate);
        ReflectionTestUtils.setField(service, "templateConfigProperties", sessionEnabledProperties());
        return service;
    }
    
    private void stubAuthorizationLookup(OAuth2Authorization authorization) {
        String serialized = serialize(authorization);
        when(hashOperations.get(anyString(), eq(TemplateConstant.AUTHORIZATION_DATA_KEY))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            return accessTokenKey().equals(key) ? serialized : null;
        });
    }
    
    private static String serialize(OAuth2Authorization authorization) {
        byte[] serialized = new JdkSerializationStrategy().serialize(authorization);
        return ByteBufUtil.hexDump(serialized);
    }
    
    private static String accessTokenKey() {
        return TemplateConstant.TOKEN_ID_PREFIX_AT_REDIS + OAuth2TokenType.ACCESS_TOKEN.getValue() + ":"
                + Digests.shorten("access-token", TemplateConstant.SHOT_TOKEN_LENGTH);
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
                                                            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                                                            .scope("read")
                                                            .build();
        return OAuth2Authorization.withRegisteredClient(registeredClient)
                                  .id("auth-id")
                                  .principalName("tester")
                                  .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                                  .token(accessToken)
                                  .build();
    }
}
