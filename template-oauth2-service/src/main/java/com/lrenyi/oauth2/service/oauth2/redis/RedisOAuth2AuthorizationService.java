package com.lrenyi.oauth2.service.oauth2.redis;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.util.Digests;
import com.lrenyi.template.core.util.TemplateConstant;
import io.netty.buffer.ByteBufUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
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
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Slf4j
public class RedisOAuth2AuthorizationService implements OAuth2AuthorizationService {
    
    private final JdkSerializationStrategy strategy = new JdkSerializationStrategy();
    private final RedisTemplate<String, String> stringRedisTemplate;
    @Resource
    private TemplateConfigProperties templateConfigProperties;
    
    public RedisOAuth2AuthorizationService(RedisTemplate<String, String> stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    
    @Override
    public void save(OAuth2Authorization authorization) {
        Assert.notNull(authorization, "authorization cannot be null");
        
        // 保存二级索引：id -> authorization
        // 使用一个特殊的 token type "id" 来标识
        saveToken(authorization, authorization.getId(), "id");
        
        if (isState(authorization)) {
            String token = authorization.getAttribute(OAuth2ParameterNames.STATE);
            if (StringUtils.hasText(token)) {
                saveToken(authorization, token, OAuth2ParameterNames.STATE);
            }
        }
        
        if (isCode(authorization)) {
            OAuth2Authorization.Token<OAuth2AuthorizationCode> authorizationCode =
                    authorization.getToken(OAuth2AuthorizationCode.class);
            OAuth2AuthorizationCode authorizationCodeToken = authorizationCode.getToken();
            saveToken(authorization, authorizationCodeToken.getTokenValue(), OAuth2ParameterNames.CODE);
        }
        
        if (isRefreshToken(authorization)) {
            OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken =
                    authorization.getToken(OAuth2RefreshToken.class);
            OAuth2RefreshToken refreshTokenToken = refreshToken.getToken();
            saveToken(authorization, refreshTokenToken.getTokenValue(), OAuth2TokenType.REFRESH_TOKEN.getValue());
        }
        
        if (isAccessToken(authorization)) {
            OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getToken(OAuth2AccessToken.class);
            OAuth2AccessToken accessTokenToken = accessToken.getToken();
            saveToken(authorization, accessTokenToken.getTokenValue(), OAuth2TokenType.ACCESS_TOKEN.getValue());
        }
        
        if (isIdToken(authorization)) {
            OAuth2Authorization.Token<OidcIdToken> idToken = authorization.getToken(OidcIdToken.class);
            OidcIdToken idTokenToken = idToken.getToken();
            saveToken(authorization, idTokenToken.getTokenValue(), OidcParameterNames.ID_TOKEN);
        }
        
        if (isDeviceCode(authorization)) {
            OAuth2Authorization.Token<OAuth2DeviceCode> deviceCode = authorization.getToken(OAuth2DeviceCode.class);
            OAuth2DeviceCode deviceCodeToken = deviceCode.getToken();
            saveToken(authorization, deviceCodeToken.getTokenValue(), OAuth2ParameterNames.DEVICE_CODE);
        }
        
        if (isUserCode(authorization)) {
            OAuth2Authorization.Token<OAuth2UserCode> userCode = authorization.getToken(OAuth2UserCode.class);
            OAuth2UserCode userCodeToken = userCode.getToken();
            saveToken(authorization, userCodeToken.getTokenValue(), OAuth2ParameterNames.USER_CODE);
        }
    }
    
    private void saveToken(OAuth2Authorization authorization, String token, String tokenType) {
        byte[] serialize = strategy.serialize(authorization);
        String shorten = "id".equals(tokenType) ? token : Digests.shorten(token, TemplateConstant.SHOT_TOKEN_LENGTH);
        String key = buildKey(tokenType, shorten);
        
        long minutes = 10;
        if (authorization.getAccessToken() != null) {
            OAuth2AccessToken accessToken = authorization.getAccessToken().getToken();
            if (accessToken.getExpiresAt() != null) {
                Duration duration = Duration.between(Instant.now(), accessToken.getExpiresAt());
                long toMinutes = duration.toMinutes();
                if (toMinutes > 0) {
                    minutes = toMinutes;
                }
            }
        }
        
        HashOperations<String, String, String> forHash = stringRedisTemplate.opsForHash();
        forHash.put(key, TemplateConstant.AUTHORIZATION_DATA_KEY, ByteBufUtil.hexDump(serialize));
        
        // 保存 token 类型，方便后续查询
        forHash.put(key, "token_type", tokenType);
        forHash.put(key, "authorization_id", authorization.getId());
        
        // 存储 claims 等额外信息
        if (authorization.getAccessToken() != null) {
            Map<String, Object> claims = authorization.getAccessToken().getClaims();
            if (!CollectionUtils.isEmpty(claims)) {
                claims.forEach((k, value) -> forHash.put(key, k, value.toString()));
            }
        }
        
        stringRedisTemplate.expire(key, minutes, TimeUnit.MINUTES);
    }
    
    @Override
    public void remove(OAuth2Authorization authorization) {
        Assert.notNull(authorization, "authorization cannot be null");
        
        List<String> keys = new ArrayList<>();
        
        // 删除 id 索引
        keys.add(buildKey("id", authorization.getId()));
        
        if (isState(authorization)) {
            String token = authorization.getAttribute(OAuth2ParameterNames.STATE);
            if (StringUtils.hasText(token)) {
                keys.add(buildKey(OAuth2ParameterNames.STATE,
                                  Digests.shorten(token, TemplateConstant.SHOT_TOKEN_LENGTH)
                ));
            }
        }
        
        if (isCode(authorization)) {
            OAuth2Authorization.Token<OAuth2AuthorizationCode> authorizationCode =
                    authorization.getToken(OAuth2AuthorizationCode.class);
            OAuth2AuthorizationCode authorizationCodeToken = authorizationCode.getToken();
            keys.add(buildKey(OAuth2ParameterNames.CODE,
                              Digests.shorten(authorizationCodeToken.getTokenValue(),
                                              TemplateConstant.SHOT_TOKEN_LENGTH
                              )
            ));
        }
        
        if (isRefreshToken(authorization)) {
            OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken =
                    authorization.getToken(OAuth2RefreshToken.class);
            OAuth2RefreshToken refreshTokenToken = refreshToken.getToken();
            keys.add(buildKey(OAuth2TokenType.REFRESH_TOKEN.getValue(),
                              Digests.shorten(refreshTokenToken.getTokenValue(), TemplateConstant.SHOT_TOKEN_LENGTH)
            ));
        }
        
        if (isAccessToken(authorization)) {
            OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getToken(OAuth2AccessToken.class);
            OAuth2AccessToken accessTokenToken = accessToken.getToken();
            keys.add(buildKey(OAuth2TokenType.ACCESS_TOKEN.getValue(),
                              Digests.shorten(accessTokenToken.getTokenValue(), TemplateConstant.SHOT_TOKEN_LENGTH)
            ));
        }
        
        if (isIdToken(authorization)) {
            OAuth2Authorization.Token<OidcIdToken> idToken = authorization.getToken(OidcIdToken.class);
            OidcIdToken idTokenToken = idToken.getToken();
            keys.add(buildKey(OidcParameterNames.ID_TOKEN,
                              Digests.shorten(idTokenToken.getTokenValue(), TemplateConstant.SHOT_TOKEN_LENGTH)
            ));
        }
        
        if (isDeviceCode(authorization)) {
            OAuth2Authorization.Token<OAuth2DeviceCode> deviceCode = authorization.getToken(OAuth2DeviceCode.class);
            OAuth2DeviceCode deviceCodeToken = deviceCode.getToken();
            keys.add(buildKey(OAuth2ParameterNames.DEVICE_CODE,
                              Digests.shorten(deviceCodeToken.getTokenValue(), TemplateConstant.SHOT_TOKEN_LENGTH)
            ));
        }
        
        if (isUserCode(authorization)) {
            OAuth2Authorization.Token<OAuth2UserCode> userCode = authorization.getToken(OAuth2UserCode.class);
            OAuth2UserCode userCodeToken = userCode.getToken();
            keys.add(buildKey(OAuth2ParameterNames.USER_CODE,
                              Digests.shorten(userCodeToken.getTokenValue(), TemplateConstant.SHOT_TOKEN_LENGTH)
            ));
        }
        
        if (!keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }
    
    @Override
    public OAuth2Authorization findById(String id) {
        Assert.hasText(id, "id cannot be empty");
        String key = buildKey("id", id);
        HashOperations<String, String, String> forHash = stringRedisTemplate.opsForHash();
        String authorData = forHash.get(key, TemplateConstant.AUTHORIZATION_DATA_KEY);
        
        if (authorData != null) {
            byte[] value = ByteBufUtil.decodeHexDump(authorData);
            OAuth2Authorization authorization = strategy.deserialize(value, OAuth2Authorization.class);
            return updateToken(authorization);
        }
        return null;
    }
    
    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        Assert.hasText(token, "token cannot be empty");
        String shorten = Digests.shorten(token, TemplateConstant.SHOT_TOKEN_LENGTH);
        
        if (tokenType == null) {
            // 尝试所有可能的类型
            String[] types =
                    {OAuth2ParameterNames.STATE, OAuth2ParameterNames.CODE, OAuth2TokenType.ACCESS_TOKEN.getValue(),
                            OAuth2TokenType.REFRESH_TOKEN.getValue(), OidcParameterNames.ID_TOKEN,
                            OAuth2ParameterNames.DEVICE_CODE, OAuth2ParameterNames.USER_CODE};
            
            for (String type : types) {
                OAuth2Authorization result = findByTokenAndType(shorten, type);
                if (result != null) {
                    return result;
                }
            }
            return null;
        } else {
            return findByTokenAndType(shorten, tokenType.getValue());
        }
    }
    
    private OAuth2Authorization findByTokenAndType(String shortenToken, String type) {
        String key = buildKey(type, shortenToken);
        HashOperations<String, String, String> forHash = stringRedisTemplate.opsForHash();
        String authorData = forHash.get(key, TemplateConstant.AUTHORIZATION_DATA_KEY);
        
        if (authorData != null) {
            byte[] value = ByteBufUtil.decodeHexDump(authorData);
            OAuth2Authorization authorization = strategy.deserialize(value, OAuth2Authorization.class);
            return updateToken(authorization);
        }
        return null;
    }
    
    private String buildKey(String type, String shortenToken) {
        String typeKey;
        if ("id".equals(type)) {
            typeKey = "id:";
        } else {
            typeKey = type + ":";
        }
        return TemplateConstant.TOKEN_ID_PREFIX_AT_REDIS + typeKey + shortenToken;
    }
    
    private OAuth2Authorization updateToken(OAuth2Authorization authorization) {
        if (authorization == null) {
            return null;
        }
        
        if (templateConfigProperties == null || !templateConfigProperties.getSecurity().isSessionIdleTimeout()) {
            return authorization;
        }
        
        OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getAccessToken();
        if (accessToken == null) {
            return authorization;
        }
        
        TemplateConfigProperties.SecurityProperties security = templateConfigProperties.getSecurity();
        Long time = security.getSessionTimeOutSeconds();
        if (time == null) {
            return authorization;
        }
        
        OAuth2AccessToken originalToken = accessToken.getToken();
        Long lifetimeSeconds = security.getTokenMaxLifetimeSeconds();
        Instant issuedAt = originalToken.getIssuedAt();
        if (issuedAt != null && lifetimeSeconds != null) {
            long diffSeconds = Duration.between(issuedAt, Instant.now()).getSeconds();
            if (diffSeconds > lifetimeSeconds) {
                remove(authorization);
                return null;
            }
        }
        
        Instant plus = Instant.now().plus(Duration.ofSeconds(time));
        OAuth2AccessToken newAccessToken = new OAuth2AccessToken(originalToken.getTokenType(),
                                                                 originalToken.getTokenValue(),
                                                                 issuedAt,
                                                                 plus,
                                                                 originalToken.getScopes()
        );
        String metadataName = OAuth2Authorization.Token.CLAIMS_METADATA_NAME;
        Consumer<Map<String, Object>> mapConsumer = (metadata) -> metadata.put(metadataName, accessToken.getClaims());
        OAuth2Authorization updatedAuthorization =
                OAuth2Authorization.from(authorization).token(newAccessToken, mapConsumer).build();
        save(updatedAuthorization);
        return updatedAuthorization;
    }
    
    private static boolean isState(OAuth2Authorization authorization) {
        return authorization.getAttribute(OAuth2ParameterNames.STATE) != null;
    }
    
    private static boolean isCode(OAuth2Authorization authorization) {
        return authorization.getToken(OAuth2AuthorizationCode.class) != null;
    }
    
    private static boolean isRefreshToken(OAuth2Authorization authorization) {
        return authorization.getToken(OAuth2RefreshToken.class) != null;
    }
    
    private static boolean isAccessToken(OAuth2Authorization authorization) {
        return authorization.getToken(OAuth2AccessToken.class) != null;
    }
    
    private static boolean isIdToken(OAuth2Authorization authorization) {
        return authorization.getToken(OidcIdToken.class) != null;
    }
    
    private static boolean isDeviceCode(OAuth2Authorization authorization) {
        return authorization.getToken(OAuth2DeviceCode.class) != null;
    }
    
    private static boolean isUserCode(OAuth2Authorization authorization) {
        return authorization.getToken(OAuth2UserCode.class) != null;
    }
}
