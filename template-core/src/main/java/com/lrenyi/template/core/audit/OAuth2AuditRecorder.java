package com.lrenyi.template.core.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;

/**
 * OAuth2 相关请求的审计记录契约。业务应用可实现此接口并注册为 Bean，将审计日志持久化或发送至其他系统。
 * 若未提供实现，OAuth2 审计将静默跳过。
 */
public interface OAuth2AuditRecorder {
    
    /**
     * 记录一条审计日志。
     */
    void recordAuditLog(HttpServletRequest request, String userName, String desc, boolean success, String exception);
    
    /**
     * 从 Authentication 提取用户名，用于 Bearer/JWT 等类型。默认返回 auth.getName()。
     */
    default String extractUserName(Authentication auth) {
        return auth != null ? auth.getName() : null;
    }
}
