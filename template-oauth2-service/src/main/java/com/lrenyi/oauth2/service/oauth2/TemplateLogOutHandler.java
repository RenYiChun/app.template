package com.lrenyi.oauth2.service.oauth2;

import com.lrenyi.template.api.audit.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class TemplateLogOutHandler implements LogoutHandler, LogoutSuccessHandler {
    private OAuth2AuthorizationService oAuth2AuthorizationService;
    private AuditLogService auditLogService;
    
    @Autowired
    public void setAuditLogService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }
    
    @Autowired
    public void setoAuth2AuthorizationService(OAuth2AuthorizationService oAuth2AuthorizationService) {
        this.oAuth2AuthorizationService = oAuth2AuthorizationService;
    }
    
    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        String userName = authentication != null ? authentication.getName() : null;
        boolean success = false;
        String exceptionDetails = null;
        try {
            String tokenInfo = request.getHeader("Authorization");
            if (!StringUtils.hasLength(tokenInfo)) {
                return;
            }
            String[] split = tokenInfo.split(" ");
            if (split.length < 2) {
                return;
            }
            OAuth2TokenType token = OAuth2TokenType.ACCESS_TOKEN;
            OAuth2Authorization authorization = oAuth2AuthorizationService.findByToken(split[1], token);
            if (authorization != null) {
                userName = authorization.getAttribute(OAuth2TokenIntrospectionClaimNames.USERNAME);
                oAuth2AuthorizationService.remove(authorization);
                success = true;
                log.info("User {} logout successfully", userName);
            }
        } catch (Exception e) {
            exceptionDetails = e.getMessage();
            log.error("Logout failed for user {}", userName, e);
        } finally {
            // 记录审计日志
            auditLogService.recordAuditLog(request, userName, "logout", success, exceptionDetails);
        }
    }
    
    @Override
    public void onLogoutSuccess(HttpServletRequest request,
                                HttpServletResponse response,
                                Authentication authentication) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write("{\"message\":\"Logout successful\"}");
    }
}
