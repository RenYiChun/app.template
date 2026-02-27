package com.lrenyi.oauth2.service.config;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.audit.OAuth2AuditRecorder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 对 OAuth2 相关路径的每次请求做访问审计（谁、何时、访问了哪个端点、结果如何）。
 * 匹配规则：请求 URI 在 {@code app.template.audit.oauth2-endpoints} 列表中，或以
 * {@code app.template.audit.oauth2-audit-path-prefix} 为前缀（默认 /oauth2）。
 * 与 {@link OAuth2AuditEventListener} 配合：本 Filter 负责全端点审计，Listener 仅负责 Token 签发/失败指标。
 */
public class OAuth2AuditFilter extends OncePerRequestFilter {
    
    private final TemplateConfigProperties properties;
    private final ObjectProvider<OAuth2AuditRecorder> auditRecorderProvider;
    private final ObjectProvider<OAuth2PrincipalNameExtractor> principalNameExtractorProvider;

    public OAuth2AuditFilter(TemplateConfigProperties properties,
            ObjectProvider<OAuth2AuditRecorder> auditRecorderProvider,
            ObjectProvider<OAuth2PrincipalNameExtractor> principalNameExtractorProvider) {
        this.properties = properties;
        this.auditRecorderProvider = auditRecorderProvider;
        this.principalNameExtractorProvider = principalNameExtractorProvider;
    }
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isAuditEffectivelyEnabled()) {
            return true;
        }
        TemplateConfigProperties.AuditLogProperties audit = properties.getAudit();
        String uri = request.getRequestURI();
        if (StringUtils.hasText(audit.getOauth2AuditPathPrefix()) && uri.startsWith(audit.getOauth2AuditPathPrefix())) {
            return false;
        }
        List<String> endpoints = audit.getOauth2Endpoints();
        return endpoints == null || !endpoints.contains(uri);
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        StatusCapturingResponseWrapper wrapped = new StatusCapturingResponseWrapper(response);
        try {
            filterChain.doFilter(request, wrapped);
        } finally {
            recordAudit(request, wrapped.getStatus());
        }
    }
    
    private void recordAudit(HttpServletRequest request, int status) {
        OAuth2AuditRecorder recorder = auditRecorderProvider.getIfAvailable();
        if (recorder == null) {
            return;
        }
        boolean success = status >= 200 && status < 400;
        String desc = "oauth2 endpoint: " + request.getMethod() + " " + request.getRequestURI();
        String message = success ? "HTTP " + status : "";
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userName = Optional.ofNullable(principalNameExtractorProvider.getIfAvailable())
                                  .flatMap(ext -> ext.extract(auth))
                                  .orElse(recorder.extractUserName(auth));
        if (userName == null) {
            userName = "";
        }
        recorder.record(request, userName, desc, success, message);
    }
    
    private static final class StatusCapturingResponseWrapper extends HttpServletResponseWrapper {
        private int status = 200;
        
        StatusCapturingResponseWrapper(HttpServletResponse response) {
            super(response);
        }
        
        @Override
        public void setStatus(int sc) {
            this.status = sc;
            super.setStatus(sc);
        }
        
        @Override
        public void sendError(int sc) throws IOException {
            this.status = sc;
            super.sendError(sc);
        }
        
        @Override
        public void sendError(int sc, String msg) throws IOException {
            this.status = sc;
            super.sendError(sc, msg);
        }
        
        @Override
        public int getStatus() {
            return status;
        }
    }
}
