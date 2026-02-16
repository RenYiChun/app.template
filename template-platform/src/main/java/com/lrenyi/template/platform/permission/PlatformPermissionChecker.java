package com.lrenyi.template.platform.permission;

import java.util.Collection;

/**
 * 权限校验：判断当前请求是否拥有所需权限之一。
 * 业务方可实现此接口接入自定义 RBAC/ABAC，默认实现基于 Spring Security 的 Authentication.getAuthorities()。
 */
public interface PlatformPermissionChecker {

    /**
     * 当前用户是否拥有 requiredPermissions 中的任意一个权限。
     *
     * @param requiredPermissions 所需权限标识，为空时由调用方根据 defaultAllowIfNoPermission 处理
     * @return true 表示有权限，false 表示无权限
     */
    boolean hasAnyPermission(Collection<String> requiredPermissions);
}
