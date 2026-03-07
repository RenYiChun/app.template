package com.lrenyi.template.dataforge.permission;

import java.util.Collection;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 基于 Spring Security 的默认权限校验：从当前 Authentication 的 authorities 中判断是否包含所需权限之一。
 */
public class DefaultDataforgePermissionChecker implements DataforgePermissionChecker {
    
    @Override
    public boolean hasAnyPermission(Collection<String> requiredPermissions) {
        if (requiredPermissions == null || requiredPermissions.isEmpty()) {
            return false;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getAuthorities() == null) {
            return false;
        }
        var userAuthorities =
                auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        return requiredPermissions.stream().anyMatch(userAuthorities::contains);
    }
}
