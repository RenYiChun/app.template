package com.lrenyi.oauth2.service.config;

import java.util.List;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.dataforge.service.AuditLogService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@ConditionalOnProperty(name = "app.template.oauth2.enabled", havingValue = "true", matchIfMissing = true)
public class OAuth2AuditEventListener {

    private AuditLogService auditLogService;
    private TemplateConfigProperties properties;
    private MeterRegistry meterRegistry;

    @Autowired
    public void setProperties(TemplateConfigProperties properties) {
        this.properties = properties;
    }

    @Autowired(required = false)
    public void setAuditLogService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Autowired
    public void setMeterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @EventListener
    public void handleAuthenticationSuccess(AuthenticationSuccessEvent event) {
        Authentication authentication = event.getAuthentication();

        if (authentication instanceof OAuth2AccessTokenAuthenticationToken tokenAuth) {
            String grantType = "unknown";
            if (tokenAuth.getRegisteredClient() != null) {
                var grantTypes = tokenAuth.getRegisteredClient().getAuthorizationGrantTypes();
                if (!grantTypes.isEmpty()) {
                    grantType = grantTypes.iterator().next().getValue();
                }
            }
            Counter.builder("app.template.oauth2.token.issued")
                   .tag("grantType", grantType)
                   .register(meterRegistry).increment();
        }

        oauthEndpointVisitAudit(authentication, true, null);
    }


    private void oauthEndpointVisitAudit(Authentication authentication, boolean success, String message) {
        TemplateConfigProperties.AuditLogProperties audit = properties.getAudit();
        List<String> endpoints = audit.getOauth2Endpoints();
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null || !audit.isEnabled() || endpoints == null || endpoints.isEmpty()) {
            return;
        }
        HttpServletRequest request = attributes.getRequest();
        String uri = request.getRequestURI();
        if (!endpoints.contains(uri) || auditLogService == null) {
            return;
        }
        String userName = auditLogService.extractUserName(authentication);
        auditLogService.recordAuditLog(request, userName, "visit endpoint: " + uri, success, message);
    }

    @EventListener
    public void handleAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        String message = event.getException().getMessage();
        Authentication authentication = event.getAuthentication();

        String errorType = event.getException().getClass().getSimpleName();
        Counter.builder("app.template.oauth2.token.failed")
               .tag("grantType", "unknown")
               .tag("errorType", errorType)
               .register(meterRegistry).increment();

        oauthEndpointVisitAudit(authentication, false, message);
    }
}
