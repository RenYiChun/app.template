package com.lrenyi.template.platform.rbac.resolver;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import com.lrenyi.template.platform.config.PlatformProperties;
import com.lrenyi.template.platform.permission.UserPermissionResolver;
import com.lrenyi.template.platform.rbac.service.RbacQueryService;
import org.springframework.security.core.Authentication;

/**
 * 基于 RbacQueryService 的默认权限解析：从 Authentication 取用户标识，查库得到权限集合。
 * 当 app.platform.rbac-cache-ttl-minutes &gt; 0 且 Caffeine 在 classpath 时使用本地缓存，过期后反映角色权限动态变更。
 */
public class DefaultUserPermissionResolver implements UserPermissionResolver {
    
    private final RbacQueryService rbacQueryService;
    private final PlatformProperties properties;
    private final Object cacheOrNull;
    
    public DefaultUserPermissionResolver(RbacQueryService rbacQueryService, PlatformProperties properties) {
        this.rbacQueryService = rbacQueryService;
        this.properties = properties;
        this.cacheOrNull = buildCacheIfEnabled();
    }
    
    private Object buildCacheIfEnabled() {
        int expireMinutes = properties.getRbacCacheTtlMinutes();
        if (expireMinutes <= 0) {
            return null;
        }
        try {
            Class<?> cacheBuilderClass = Class.forName("com.github.benmanes.caffeine.cache.CacheBuilder");
            Object builder = cacheBuilderClass.getMethod("newBuilder").invoke(null);
            Object withExpiry = cacheBuilderClass.getMethod("expireAfterWrite", long.class, TimeUnit.class)
                                                 .invoke(builder, expireMinutes, TimeUnit.MINUTES);
            return cacheBuilderClass.getMethod("build").invoke(withExpiry);
        } catch (Throwable ignored) {
            return null;
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
        if (cacheOrNull != null) {
            return getCachedPermissions(userId);
        }
        return rbacQueryService.getPermissionStringsByUserId(userId);
    }
    
    @SuppressWarnings("unchecked")
    private Set<String> getCachedPermissions(String userId) {
        try {
            Object cached = cacheOrNull.getClass().getMethod("getIfPresent", Object.class).invoke(cacheOrNull, userId);
            if (cached != null) {
                return (Set<String>) cached;
            }
            Set<String> permissions = rbacQueryService.getPermissionStringsByUserId(userId);
            cacheOrNull.getClass()
                       .getMethod("put", Object.class, Object.class)
                       .invoke(cacheOrNull, userId, permissions);
            return permissions;
        } catch (Throwable e) {
            return rbacQueryService.getPermissionStringsByUserId(userId);
        }
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
