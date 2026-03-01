package com.lrenyi.oauth2.service.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.AllArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * OAuth2 Token 端点指标：监听认证成功/失败事件，打点 Token 签发/失败计数（grantType、clientId、errorType）。
 * 全端点访问审计由 {@link OAuth2AuditFilter} 统一负责。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "app.template.oauth2.enabled", havingValue = "true", matchIfMissing = true)
public class OAuth2AuditEventListener {
    
    private static final String UNKNOWN = "unknown";
    private final MeterRegistry meterRegistry;
    
    @EventListener
    public void handleAuthenticationSuccess(AuthenticationSuccessEvent event) {
        Authentication authentication = event.getAuthentication();
        
        if (authentication instanceof OAuth2AccessTokenAuthenticationToken tokenAuth) {
            String grantType = resolveGrantType(tokenAuth);
            RegisteredClient registeredClient = tokenAuth.getRegisteredClient();
            String clientId = registeredClient != null ? registeredClient.getId() : UNKNOWN;
            Counter.builder("app.template.oauth2.token.issued")
                   .tag("grantType", grantType)
                   .tag("clientId", sanitizeTagValue(clientId))
                   .register(meterRegistry)
                   .increment();
        }
    }
    
    private String resolveGrantType(OAuth2AccessTokenAuthenticationToken tokenAuth) {
        if (tokenAuth.getRegisteredClient() == null) {
            return UNKNOWN;
        }
        var grantTypes = tokenAuth.getRegisteredClient().getAuthorizationGrantTypes();
        if (grantTypes == null || grantTypes.isEmpty()) {
            return UNKNOWN;
        }
        String value = grantTypes.iterator().next().getValue();
        return StringUtils.hasText(value) ? value : UNKNOWN;
    }
    
    private String sanitizeTagValue(String value) {
        if (!StringUtils.hasText(value)) {
            return UNKNOWN;
        }
        return value.length() > 64 ? value.substring(0, 64) : value;
    }
    
    @EventListener
    public void handleAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        String grantType = resolveGrantTypeFromCurrentRequest();
        String errorType = event.getException().getClass().getSimpleName();
        Counter.builder("app.template.oauth2.token.failed")
               .tag("grantType", grantType)
               .tag("errorType", errorType)
               .register(meterRegistry)
               .increment();
    }
    
    private String resolveGrantTypeFromCurrentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return UNKNOWN;
        }
        String grantType = attrs.getRequest().getParameter("grant_type");
        return StringUtils.hasText(grantType) ? sanitizeTagValue(grantType) : UNKNOWN;
    }
}
