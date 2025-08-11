package com.lrenyi.oauth2.service.oauth2.redis;

import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.util.Digests;
import com.lrenyi.template.core.util.SpringContextUtil;
import com.lrenyi.template.core.util.TemplateConstant;
import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.CRC32;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
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
    private RedisTemplate<String, String> byteArrayRedisTemplate;
    private final CRC32 crc32 = new CRC32();
    
    @Override
    public void save(OAuth2Authorization authorization) {
        byte[] serialize = strategy.serialize(authorization);
        OAuth2Authorization.Token<OAuth2AccessToken> tokenToken = authorization.getAccessToken();
        if (tokenToken == null) {
            throw new IllegalArgumentException("access token is null");
        }
        OAuth2AccessToken token = tokenToken.getToken();
        Instant issuedAt = token.getIssuedAt();
        if (issuedAt == null) {
            issuedAt = Instant.now();
        }
        Instant expiresAt = token.getExpiresAt();
        Duration duration = Duration.between(issuedAt, expiresAt);
        String tokenValue = token.getTokenValue();
        String shorten = Digests.shorten(tokenValue, TemplateConstant.SHOT_TOKEN_LENGTH);
        long minutes = duration.toMinutes();
        String key = TemplateConstant.TOKEN_ID_PREFIX_AT_REDIS + ":" + shorten;
        HashOperations<String, String, String> forHash = byteArrayRedisTemplate.opsForHash();
        forHash.put(key, TemplateConstant.AUTHORIZATION_DATA_KEY, new String(serialize, StandardCharsets.UTF_8));
        Map<String, Object> claims = tokenToken.getClaims();
        if (claims != null) {
            claims.forEach((k, value) -> forHash.put(key, k, value.toString()));
        }
        byteArrayRedisTemplate.expire(key, minutes, TimeUnit.MINUTES);
    }
    
    @Override
    public void remove(OAuth2Authorization authorization) {
        OAuth2Authorization.Token<OAuth2AccessToken> tokenToken = authorization.getAccessToken();
        if (tokenToken == null) {
            throw new IllegalArgumentException("access token is null");
        }
        OAuth2AccessToken token = tokenToken.getToken();
        String tokenValue = token.getTokenValue();
        String shorten = Digests.shorten(tokenValue, TemplateConstant.SHOT_TOKEN_LENGTH);
        String key = TemplateConstant.TOKEN_ID_PREFIX_AT_REDIS + ":" + shorten;
        byteArrayRedisTemplate.delete(key);
    }
    
    @Override
    public OAuth2Authorization findById(String id) {
        Set<String> keys = byteArrayRedisTemplate.keys(TemplateConstant.TOKEN_ID_PREFIX_AT_REDIS + ":*");
        if (keys == null) {
            return null;
        }
        HashOperations<String, String, String> forHash = byteArrayRedisTemplate.opsForHash();
        for (String key : keys) {
            String authorData = forHash.get(key, TemplateConstant.AUTHORIZATION_DATA_KEY);
            if (authorData == null) {
                continue;
            }
            byte[] value = authorData.getBytes(StandardCharsets.UTF_8);
            OAuth2Authorization authorization = strategy.deserialize(value, OAuth2Authorization.class);
            String id1 = authorization.getId();
            if (id.equals(id1)) {
                return updateToken(authorization);
            }
        }
        return null;
    }
    
    private OAuth2Authorization updateToken(OAuth2Authorization authorization) {
        TemplateConfigProperties properties = SpringContextUtil.getBean(TemplateConfigProperties.class);
        if (properties == null || !properties.getSecurity().isSessionIdleTimeout()) {
            return authorization;
        }
        TemplateConfigProperties.SecurityProperties security = properties.getSecurity();
        Long time = security.getSessionTimeOutSeconds();
        if (time == null) {
            return authorization;
        }
        OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getAccessToken();
        OAuth2AccessToken originalToken = accessToken.getToken();
        OAuth2AccessToken newAccessToken = new OAuth2AccessToken(originalToken.getTokenType(),
                                                                 originalToken.getTokenValue(),
                                                                 originalToken.getIssuedAt(),
                                                                 Instant.now().plus(Duration.ofSeconds(time)),
                                                                 originalToken.getScopes()
        );
        String metadataName = OAuth2Authorization.Token.CLAIMS_METADATA_NAME;
        Consumer<Map<String, Object>> mapConsumer = (metadata) -> metadata.put(metadataName, accessToken.getClaims());
        OAuth2Authorization updatedAuthorization = OAuth2Authorization.from(authorization)
                                                                      .token(newAccessToken, mapConsumer)
                                                                      .build();
        save(updatedAuthorization);
        return updatedAuthorization;
    }
    
    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        Set<String> keys = byteArrayRedisTemplate.keys(TemplateConstant.TOKEN_ID_PREFIX_AT_REDIS + ":*");
        if (keys == null) {
            return null;
        }
        HashOperations<String, String, String> forHash = byteArrayRedisTemplate.opsForHash();
        for (String key : keys) {
            String authorData = forHash.get(key, TemplateConstant.AUTHORIZATION_DATA_KEY);
            if (authorData == null) {
                continue;
            }
            byte[] value = authorData.getBytes(StandardCharsets.UTF_8);
            OAuth2Authorization authorization = strategy.deserialize(value, OAuth2Authorization.class);
            if (hasToken(authorization, token, tokenType)) {
                return updateToken(authorization);
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
