package com.lrenyi.template.web.config;

import com.lrenyi.template.core.util.SpringContextUtil;
import java.util.Collection;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

@Component
public class RequestAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {
    private static final String DEFAULT_AUTHORITY_PREFIX = "SCOPE_";
    AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();
    
    @Override
    public AuthorizationDecision check(Supplier<Authentication> supplier, RequestAuthorizationContext context) {
        RolePermissionService bean = SpringContextUtil.getBean(RolePermissionService.class);
        if (bean == null) {
            throw new IllegalArgumentException("not find bean RolePermissionService");
        }
        Authentication authentication = supplier.get();
        boolean anonymous = this.trustResolver.isAnonymous(authentication);
        boolean success = authentication != null && !anonymous && authentication.isAuthenticated();
        if (!success) {
            throw new AccessDeniedException("请先登录系统，再进行访问");
        }
        String requestURI = context.getRequest().getRequestURI();
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        Set<String> scopes = authorities.stream()
                                        .map(GrantedAuthority::getAuthority)
                                        .map(data -> data.replace(DEFAULT_AUTHORITY_PREFIX, ""))
                                        .collect(Collectors.toSet());
        boolean authorization = bean.check(scopes, requestURI);
        return new AuthorizationDecision(authorization);
    }
}
