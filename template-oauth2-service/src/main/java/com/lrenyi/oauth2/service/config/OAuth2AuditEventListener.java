package com.lrenyi.oauth2.service.config;

import com.lrenyi.template.api.audit.service.AuditLogService;
import com.lrenyi.template.core.TemplateConfigProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@ConditionalOnProperty(name = "app.template.oauth2.enabled", havingValue = "true", matchIfMissing = true)
public class OAuth2AuditEventListener {
    
    private AuditLogService auditLogService;
    private TemplateConfigProperties properties;
    
    @Autowired
    public void setProperties(TemplateConfigProperties properties) {
        this.properties = properties;
    }
    
    @Autowired(required = false)
    public void setAuditLogService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }
    
    @EventListener
    public void handleAuthenticationSuccess(AuthenticationSuccessEvent event) {
        Authentication authentication = event.getAuthentication();
        oauthEndpointVisitAudit(authentication, true, null);
    }
    
    
    private void oauthEndpointVisitAudit(Authentication authentication, boolean success, String message) {
        TemplateConfigProperties.AuditLogProperties audit = properties.getAudit();
        List<String> endpoints = audit.getOauth2Endpoints();
        // 获取当前请求信息
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
        oauthEndpointVisitAudit(authentication, false, message);
    }
}
