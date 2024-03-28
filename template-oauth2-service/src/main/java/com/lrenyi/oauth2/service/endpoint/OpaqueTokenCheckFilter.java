package com.lrenyi.oauth2.service.endpoint;

import com.alibaba.fastjson2.JSON;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class OpaqueTokenCheckFilter extends OncePerRequestFilter {
    private static final String ENDPOINT_URI = "/opaque/token/check";
    private final RequestMatcher endpointMatcher;
    
    private OAuth2AuthorizationService oAuth2AuthorizationService;
    
    @Autowired
    public void setoAuth2AuthorizationService(OAuth2AuthorizationService oAuth2AuthorizationService) {
        this.oAuth2AuthorizationService = oAuth2AuthorizationService;
    }
    
    public OpaqueTokenCheckFilter() {
        this.endpointMatcher = createDefaultRequestMatcher();
    }
    
    private RequestMatcher createDefaultRequestMatcher() {
        String method = HttpMethod.POST.name();
        RequestMatcher requestMatcher =
                new AntPathRequestMatcher(OpaqueTokenCheckFilter.ENDPOINT_URI, method);
        RequestMatcher contextType = request -> {
            String contentType = request.getContentType();
            return StringUtils.hasText(contentType) && contentType.startsWith(
                    "application/x-www-form-urlencoded");
        };
        return new AndRequestMatcher(requestMatcher, contextType);
    }
    
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!endpointMatcher.matches(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        Map<String, String[]> parameterMap = request.getParameterMap();
        Map<String, Object> result = new HashMap<>();
        String token = parameterMap.get("token")[0];
        
        boolean active = false;
        OAuth2Authorization authorization =
                oAuth2AuthorizationService.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
        if (authorization != null) {
            Map<String, Object> attributes = authorization.getAttributes();
            Set<String> scopes = authorization.getAuthorizedScopes();
            Object username = attributes.get("username");
            if (username == null) {
                username = authorization.getPrincipalName();
            }
            result.put(OAuth2TokenIntrospectionClaimNames.USERNAME, username);
            result.put(OAuth2TokenIntrospectionClaimNames.SCOPE, String.join(" ", scopes));
            active = true;
        }
        result.put(OAuth2TokenIntrospectionClaimNames.ACTIVE, active);
        String value = JSON.toJSONString(result);
        response.setContentType("application/json");
        OutputStream outputStream = response.getOutputStream();
        outputStream.write(value.getBytes(StandardCharsets.UTF_8));
        outputStream.close();
    }
}
