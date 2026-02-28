package com.lrenyi.template.dataforge.rbac.resolver;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lrenyi.template.dataforge.config.DataforgeProperties;
import com.lrenyi.template.dataforge.permission.UserPermissionResolver;
import com.lrenyi.template.dataforge.rbac.service.RbacQueryService;
import org.springframework.security.core.Authentication;

/**
 * 基于 RbacQueryService 的默认权限解析：从 Authentication 取用户标识，查库得到权限集合。
 * 当 app.dataforge.rbac-cache-ttl-minutes &gt; 0 时使用 Caffeine 本地缓存，过期后反映角色权限动态变更。
 */
public class DefaultUserPermissionResolver implements UserPermissionResolver {
    
    private final RbacQueryService rbacQueryService;
    private final Cache<String, Set<String>> cache;
    
    public DefaultUserPermissionResolver(RbacQueryService rbacQueryService, DataforgeProperties properties) {
        this.rbacQueryService = rbacQueryService;
        int expireMinutes = properties.getRbacCacheTtlMinutes();
        if (expireMinutes > 0) {
            this.cache =
                    Caffeine.newBuilder().maximumSize(1024).expireAfterWrite(expireMinutes, TimeUnit.MINUTES).build();
        } else {
            this.cache = null;
        }
    }
    
    @Override
    public Set<String> getPermissions(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Collections.emptySet();
        }
        String userId = resolveUserId(authentication);
        if (userId == null || userId.isBlank()) {
            return Collections.emptySet();
        }
        if (cache != null) {
            return cache.get(userId, rbacQueryService::getPermissionStringsByUserId);
        }
        return rbacQueryService.getPermissionStringsByUserId(userId);
    }
    
    private static String resolveUserId(Authentication authentication) {
        String name = authentication.getName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        Object principal = authentication.getPrincipal();
        if (principal != null) {
            return principal.toString();
        }
        return null;
    }
}
