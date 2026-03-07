package com.lrenyi.template.dataforge.permission;

import java.util.Collection;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 基于 UserPermissionResolver 的权限校验：每次请求从 Resolver 获取当前用户权限集合，再判断是否包含所需权限之一。
 * 支持角色权限动态变更（Resolver 查库或短 TTL 缓存）。
 */
public class RbacPermissionChecker implements DataforgePermissionChecker {
    
    private final UserPermissionResolver userPermissionResolver;
    
    public RbacPermissionChecker(UserPermissionResolver userPermissionResolver) {
        this.userPermissionResolver = userPermissionResolver;
    }
    
    @Override
    public boolean hasAnyPermission(Collection<String> requiredPermissions) {
        if (requiredPermissions == null || requiredPermissions.isEmpty()) {
            return false;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        Set<String> userPermissions = userPermissionResolver.getPermissions(auth);
        if (userPermissions == null || userPermissions.isEmpty()) {
            return false;
        }
        return requiredPermissions.stream().anyMatch(userPermissions::contains);
    }
}
