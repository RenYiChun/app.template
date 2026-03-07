package com.lrenyi.template.dataforge.backend.config;

import com.lrenyi.template.core.audit.OAuth2AuditRecorder;
import com.lrenyi.template.dataforge.audit.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * OAuth2 审计记录实现，委托给 AuditLogService 持久化到 OperationLog。
 */
@Component
@ConditionalOnBean(AuditLogService.class)
public class OAuth2AuditRecorderImpl implements OAuth2AuditRecorder {
    
    private final AuditLogService auditLogService;
    
    public OAuth2AuditRecorderImpl(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }
    
    @Override
    public void recordAuditLog(HttpServletRequest request,
            String userName,
            String desc,
            boolean success,
            String exception) {
        auditLogService.recordAuditLog(request, userName, desc, success, exception);
    }
    
    @Override
    public String extractUserName(Authentication auth) {
        return auditLogService.extractUserName(auth);
    }
}
