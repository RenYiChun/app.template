package com.lrenyi.oauth2.service.oauth2;

import com.lrenyi.template.core.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TemplateLogOutHandler implements LogoutHandler {
    private OAuth2AuthorizationService oAuth2AuthorizationService;
    
    @Autowired
    public void setoAuth2AuthorizationService(OAuth2AuthorizationService oAuth2AuthorizationService) {
        this.oAuth2AuthorizationService = oAuth2AuthorizationService;
    }
    
    @Override
    public void logout(HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) {
        String tokenInfo = request.getHeader("Authorization");
        if (!StringUtils.hasLength(tokenInfo)) {
            return;
        }
        String[] split = tokenInfo.split(" ");
        if (split.length < 2) {
            return;
        }
        OAuth2Authorization authorization =
                oAuth2AuthorizationService.findByToken(split[1], OAuth2TokenType.ACCESS_TOKEN);
        if (authorization != null) {
            oAuth2AuthorizationService.remove(authorization);
        }
    }
}
