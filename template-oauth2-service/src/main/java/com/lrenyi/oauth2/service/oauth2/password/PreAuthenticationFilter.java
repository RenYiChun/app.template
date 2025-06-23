package com.lrenyi.oauth2.service.oauth2.password;

import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.util.OAuth2Constant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.web.filter.OncePerRequestFilter;

public class PreAuthenticationFilter extends OncePerRequestFilter {
    private final TemplateConfigProperties templateConfigProperties;
    private final ObjectProvider<PreAuthenticationChecker> preAuthenticationCheckers;
    
    public PreAuthenticationFilter(ObjectProvider<PreAuthenticationChecker> preAuthenticationCheckers,
                                   TemplateConfigProperties templateConfigProperties) {
        this.preAuthenticationCheckers = preAuthenticationCheckers;
        this.templateConfigProperties = templateConfigProperties;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String grantType = request.getParameter("grant_type");
        boolean login = request.getRequestURI().equals("/oauth2/token");
        boolean skipPreAuthentication = templateConfigProperties.getOauth2().isSkipPreAuthentication();
        PreAuthenticationChecker checker = preAuthenticationCheckers.getIfAvailable();
        boolean isPasswordType = OAuth2Constant.GRANT_TYPE_PASSWORD.equalsIgnoreCase(grantType);
        if (checker != null && !skipPreAuthentication && login && isPasswordType) {
            try {
                checker.check(request);
            } catch (AuthenticationException e) {
                String description = "per authentication is incorrect for login";
                OAuth2Error error = new OAuth2Error(OAuth2Constant.PRE_AUTHENTICATION_FAIL, description, "");
                throw new OAuth2AuthenticationException(error);
            }
        }
        filterChain.doFilter(request, response);
    }
}