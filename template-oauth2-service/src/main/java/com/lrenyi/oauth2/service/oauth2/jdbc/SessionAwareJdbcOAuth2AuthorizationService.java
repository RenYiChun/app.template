package com.lrenyi.oauth2.service.oauth2.jdbc;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;
import com.lrenyi.template.core.TemplateConfigProperties;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

/**
 * 包装 {@link org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService}，
 * 在 findById/findByToken 时根据 {@link TemplateConfigProperties#getSecurity()} 的 sessionIdleTimeout 配置
 * 延长 access token 过期时间并写回库，与 Redis 方案的 updateToken 行为一致。
 */
public class SessionAwareJdbcOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private final OAuth2AuthorizationService delegate;
    private final TemplateConfigProperties templateConfigProperties;

    public SessionAwareJdbcOAuth2AuthorizationService(OAuth2AuthorizationService delegate,
                                                       TemplateConfigProperties templateConfigProperties) {
        this.delegate = delegate;
        this.templateConfigProperties = templateConfigProperties;
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        delegate.save(authorization);
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        OAuth2Authorization authorization = delegate.findById(id);
        return authorization != null ? updateTokenIfNeeded(authorization) : null;
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        OAuth2Authorization authorization = delegate.findByToken(token, tokenType);
        return authorization != null ? updateTokenIfNeeded(authorization) : null;
    }

    private OAuth2Authorization updateTokenIfNeeded(OAuth2Authorization authorization) {
        if (templateConfigProperties == null || !templateConfigProperties.getSecurity().isSessionIdleTimeout()) {
            return authorization;
        }
        TemplateConfigProperties.SecurityProperties security = templateConfigProperties.getSecurity();
        Long sessionTimeOutSeconds = security.getSessionTimeOutSeconds();
        if (sessionTimeOutSeconds == null) {
            return authorization;
        }
        OAuth2Authorization.Token<OAuth2AccessToken> accessTokenToken = authorization.getAccessToken();
        if (accessTokenToken == null) {
            return authorization;
        }
        OAuth2AccessToken originalToken = accessTokenToken.getToken();
        Long lifetimeSeconds = security.getTokenMaxLifetimeSeconds();
        Instant issuedAt = originalToken.getIssuedAt();
        if (issuedAt != null && lifetimeSeconds != null) {
            long diffSeconds = Duration.between(issuedAt, Instant.now()).getSeconds();
            if (diffSeconds > lifetimeSeconds) {
                remove(authorization);
                return null;
            }
        }
        Instant newExpiresAt = Instant.now().plus(Duration.ofSeconds(sessionTimeOutSeconds));
        OAuth2AccessToken newAccessToken = new OAuth2AccessToken(
                originalToken.getTokenType(),
                originalToken.getTokenValue(),
                issuedAt != null ? issuedAt : Instant.now(),
                newExpiresAt,
                originalToken.getScopes());
        String metadataName = OAuth2Authorization.Token.CLAIMS_METADATA_NAME;
        Consumer<Map<String, Object>> mapConsumer = (metadata) ->
                metadata.put(metadataName, accessTokenToken.getClaims());
        OAuth2Authorization updated = OAuth2Authorization.from(authorization)
                .token(newAccessToken, mapConsumer)
                .build();
        delegate.save(updated);
        return updated;
    }
}
