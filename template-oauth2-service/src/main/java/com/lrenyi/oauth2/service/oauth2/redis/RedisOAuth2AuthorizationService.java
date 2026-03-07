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
        
        saveToken(authorization, authorization.getId(), "id");
        String state = authorization.getAttribute(OAuth2ParameterNames.STATE);
        if (StringUtils.hasText(state)) {
            saveToken(authorization, state, OAuth2ParameterNames.STATE);
        }
        Object[][] types = {{OAuth2AuthorizationCode.class, OAuth2ParameterNames.CODE},
                {OAuth2RefreshToken.class, OAuth2TokenType.REFRESH_TOKEN.getValue()},
                {OAuth2AccessToken.class, OAuth2TokenType.ACCESS_TOKEN.getValue()},
                {OidcIdToken.class, OidcParameterNames.ID_TOKEN},
                {OAuth2DeviceCode.class, OAuth2ParameterNames.DEVICE_CODE},
                {OAuth2UserCode.class, OAuth2ParameterNames.USER_CODE}};
        for (Object[] t : types) {
            Class<?> cls = (Class<?>) t[0];
            String type = (String) t[1];
            String value = getTokenValue(authorization, cls);
            if (StringUtils.hasText(value)) {
                saveToken(authorization, value, type);
            }
        }
    }
    
    private void saveToken(OAuth2Authorization authorization, String token, String tokenType) {
        Assert.hasText(token, "token cannot be empty");
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
                claims.forEach((k, value) -> forHash.put(key, k, String.valueOf(value)));
            }
        }
        
        stringRedisTemplate.expire(key, minutes, TimeUnit.MINUTES);
    }
    
    @Override
    public void remove(OAuth2Authorization authorization) {
        Assert.notNull(authorization, "authorization cannot be null");
        
        List<String> keys = new ArrayList<>();
        keys.add(buildKey("id", authorization.getId()));
        String state = authorization.getAttribute(OAuth2ParameterNames.STATE);
        if (StringUtils.hasText(state)) {
            keys.add(buildKey(OAuth2ParameterNames.STATE, Digests.shorten(state, TemplateConstant.SHOT_TOKEN_LENGTH)));
        }
        Object[][] types = {{OAuth2AuthorizationCode.class, OAuth2ParameterNames.CODE},
                {OAuth2RefreshToken.class, OAuth2TokenType.REFRESH_TOKEN.getValue()},
                {OAuth2AccessToken.class, OAuth2TokenType.ACCESS_TOKEN.getValue()},
                {OidcIdToken.class, OidcParameterNames.ID_TOKEN},
                {OAuth2DeviceCode.class, OAuth2ParameterNames.DEVICE_CODE},
                {OAuth2UserCode.class, OAuth2ParameterNames.USER_CODE}};
        for (Object[] t : types) {
            Class<?> cls = (Class<?>) t[0];
            String type = (String) t[1];
            String value = getTokenValue(authorization, cls);
            if (StringUtils.hasText(value)) {
                keys.add(buildKey(type, Digests.shorten(value, TemplateConstant.SHOT_TOKEN_LENGTH)));
            }
        }
        stringRedisTemplate.delete(keys);
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
    
    @SuppressWarnings("unchecked")
    private static String getTokenValue(OAuth2Authorization authorization, Class<?> tokenClass) {
        OAuth2Authorization.Token<?> t = authorization.getToken((Class) tokenClass);
        if (t == null || t.getToken() == null) {
            return null;
        }
        return t.getToken().getTokenValue();
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
        Consumer<Map<String, Object>> mapConsumer = metadata -> metadata.put(metadataName, accessToken.getClaims());
        OAuth2Authorization updatedAuthorization =
                OAuth2Authorization.from(authorization).token(newAccessToken, mapConsumer).build();
        save(updatedAuthorization);
        return updatedAuthorization;
    }
}
