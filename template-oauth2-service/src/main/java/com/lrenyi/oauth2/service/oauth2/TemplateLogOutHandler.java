package com.lrenyi.oauth2.service.oauth2;

import java.io.IOException;
import com.lrenyi.template.core.audit.OAuth2AuditRecorder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
    private final OAuth2AuthorizationService oAuth2AuthorizationService;
    private final ObjectProvider<OAuth2AuditRecorder> auditRecorderProvider;
    private final MeterRegistry meterRegistry;
    
    public TemplateLogOutHandler(OAuth2AuthorizationService oAuth2AuthorizationService,
            ObjectProvider<OAuth2AuditRecorder> auditRecorderProvider,
            MeterRegistry meterRegistry) {
        this.oAuth2AuthorizationService = oAuth2AuthorizationService;
        this.auditRecorderProvider = auditRecorderProvider;
        this.meterRegistry = meterRegistry;
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
                Counter.builder("app.template.oauth2.logout")
                       .register(meterRegistry).increment();
                log.info("User {} logout successfully", userName);
            }
        } catch (Exception e) {
            exceptionDetails = e.getMessage();
            log.error("Logout failed for user {}", userName, e);
        } finally {
            OAuth2AuditRecorder recorder = auditRecorderProvider.getIfAvailable();
            if (recorder != null) {
                recorder.record(request, userName != null ? userName : "", "logout", success, exceptionDetails);
            }
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
