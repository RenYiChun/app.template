package com.lrenyi.oauth2.service.oauth2.redis;

import com.lrenyi.template.core.util.OAuth2Constant;
import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2DeviceCode;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2UserCode;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

@Slf4j
public class RedisOAuth2AuthorizationService implements OAuth2AuthorizationService {
    
    private final JdkSerializationStrategy strategy = new JdkSerializationStrategy();
    @Resource
    private RedisTemplate<String, byte[]> byteArrayRedisTemplate;
    
    @Override
    public void save(OAuth2Authorization authorization) {
        byte[] serialize = strategy.serialize(authorization);
        
        OAuth2Authorization.Token<OAuth2AccessToken> tokenToken = authorization.getAccessToken();
        if (tokenToken == null) {
            throw new IllegalArgumentException("access token is null");
        }
        String id = authorization.getId();
        Instant issuedAt = tokenToken.getToken().getIssuedAt();
        ValueOperations<String, byte[]> opsForValue = byteArrayRedisTemplate.opsForValue();
        if (issuedAt == null) {
            issuedAt = Instant.now();
        }
        Instant expiresAt = tokenToken.getToken().getExpiresAt();
        Duration duration = Duration.between(issuedAt, expiresAt);
        long minutes = duration.toMinutes();
        String key = OAuth2Constant.TOKEN_ID_KEY_IN_REDIS + ":" + id;
        opsForValue.set(key, serialize);
        byteArrayRedisTemplate.expire(key, minutes, TimeUnit.MINUTES);
    }
    
    @Override
    public void remove(OAuth2Authorization authorization) {
        OAuth2Authorization.Token<OAuth2AccessToken> tokenToken = authorization.getAccessToken();
        if (tokenToken == null) {
            throw new IllegalArgumentException("access token is null");
        }
        String id = authorization.getId();
        byteArrayRedisTemplate.delete(OAuth2Constant.TOKEN_ID_KEY_IN_REDIS + ":" + id);
    }
    
    @Override
    public OAuth2Authorization findById(String id) {
        byte[] value = byteArrayRedisTemplate.opsForValue()
                                             .get(OAuth2Constant.TOKEN_ID_KEY_IN_REDIS + ":" + id);
        return strategy.deserialize(value, OAuth2Authorization.class);
    }
    
    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        Set<String> keys = byteArrayRedisTemplate.keys(OAuth2Constant.TOKEN_ID_KEY_IN_REDIS + ":*");
        ValueOperations<String, byte[]> opsForValue = byteArrayRedisTemplate.opsForValue();
        if (keys != null) {
            for (String key : keys) {
                byte[] value = opsForValue.get(key);
                if (value == null) {
                    continue;
                }
                OAuth2Authorization authorization =
                        strategy.deserialize(value, OAuth2Authorization.class);
                if (hasToken(authorization, token, tokenType)) {
                    return authorization;
                }
            }
        }
        return null;
    }
    
    private static boolean hasToken(OAuth2Authorization authorization,
            String token,
            @Nullable OAuth2TokenType tokenType) {
        // @formatter:off
        if (tokenType == null) {
            return matchesState(authorization, token)
                    || matchesAuthorizationCode(authorization, token)
                    || matchesAccessToken(authorization, token)
                    || matchesIdToken(authorization, token)
                    || matchesRefreshToken(authorization, token)
                    || matchesDeviceCode(authorization, token)
                    || matchesUserCode(authorization, token);
        } else if (OAuth2ParameterNames.STATE.equals(tokenType.getValue())) {
            return matchesState(authorization, token);
        } else if (OAuth2ParameterNames.CODE.equals(tokenType.getValue())) {
            return matchesAuthorizationCode(authorization, token);
        } else if (OAuth2TokenType.ACCESS_TOKEN.equals(tokenType)) {
            return matchesAccessToken(authorization, token);
        } else if (OidcParameterNames.ID_TOKEN.equals(tokenType.getValue())) {
            return matchesIdToken(authorization, token);
        } else if (OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)) {
            return matchesRefreshToken(authorization, token);
        } else if (OAuth2ParameterNames.DEVICE_CODE.equals(tokenType.getValue())) {
            return matchesDeviceCode(authorization, token);
        } else if (OAuth2ParameterNames.USER_CODE.equals(tokenType.getValue())) {
            return matchesUserCode(authorization, token);
        }
        // @formatter:on
        return false;
    }
    
    private static boolean matchesState(OAuth2Authorization authorization, String token) {
        return token.equals(authorization.getAttribute(OAuth2ParameterNames.STATE));
    }
    
    private static boolean matchesAuthorizationCode(OAuth2Authorization authorization,
            String token) {
        OAuth2Authorization.Token<OAuth2AuthorizationCode> authorizationCode =
                authorization.getToken(OAuth2AuthorizationCode.class);
        return authorizationCode != null && authorizationCode.getToken()
                                                             .getTokenValue()
                                                             .equals(token);
    }
    
    private static boolean matchesAccessToken(OAuth2Authorization authorization, String token) {
        OAuth2Authorization.Token<OAuth2AccessToken> accessToken =
                authorization.getToken(OAuth2AccessToken.class);
        return accessToken != null && accessToken.getToken().getTokenValue().equals(token);
    }
    
    private static boolean matchesIdToken(OAuth2Authorization authorization, String token) {
        OAuth2Authorization.Token<OidcIdToken> idToken = authorization.getToken(OidcIdToken.class);
        return idToken != null && idToken.getToken().getTokenValue().equals(token);
    }
    
    private static boolean matchesRefreshToken(OAuth2Authorization authorization, String token) {
        OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken =
                authorization.getToken(OAuth2RefreshToken.class);
        return refreshToken != null && refreshToken.getToken().getTokenValue().equals(token);
    }
    
    private static boolean matchesDeviceCode(OAuth2Authorization authorization, String token) {
        OAuth2Authorization.Token<OAuth2DeviceCode> deviceCode =
                authorization.getToken(OAuth2DeviceCode.class);
        return deviceCode != null && deviceCode.getToken().getTokenValue().equals(token);
    }
    
    private static boolean matchesUserCode(OAuth2Authorization authorization, String token) {
        OAuth2Authorization.Token<OAuth2UserCode> userCode =
                authorization.getToken(OAuth2UserCode.class);
        return userCode != null && userCode.getToken().getTokenValue().equals(token);
    }
}
