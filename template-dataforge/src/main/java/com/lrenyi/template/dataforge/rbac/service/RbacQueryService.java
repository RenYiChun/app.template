package com.lrenyi.template.dataforge.rbac.service;

import java.util.Set;

/**
 * 按用户标识查询其拥有的权限字符串集合（UserRole -> Role -> RolePermission -> Permission）。
 */
public interface RbacQueryService {

    /**
     * 根据用户标识返回该用户拥有的所有权限字符串。
     *
     * @param userId 用户标识，与认证中的用户标识一致（如 username、sub）
     * @return 权限字符串集合，无权限或用户不存在时返回空集合，不返回 null
     */
    Set<String> getPermissionStringsByUserId(String userId);
}
