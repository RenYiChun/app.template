package com.lrenyi.oauth2.service.oauth2.password;

import java.util.Collections;
import java.util.List;

/**
 * RBAC 用户认证用最小契约：用户名、密码、权限字符串列表。
 * 由业务实现 {@link IRbacService} 返回，供本模块构建 Spring Security 的 UserDetails。
 * <p>
 * 与 Spring Security 的 UserDetails 对齐：仅携带认证与鉴权所需信息，
 * Role/Permission 由业务在实现侧查询后扁平化为权限字符串。
 * </p>
 */
public record RbacUserCredentials(String username, String password, List<String> permissions) {

    @Override
    public List<String> permissions() {
        return permissions == null ? Collections.emptyList() : permissions;
    }
}
