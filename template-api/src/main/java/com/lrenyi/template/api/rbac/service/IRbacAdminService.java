package com.lrenyi.template.api.rbac.service;

import com.lrenyi.template.api.rbac.model.Permission;
import com.lrenyi.template.api.rbac.model.Role;

import com.lrenyi.template.api.rbac.model.SystemPermission;
import com.lrenyi.template.api.rbac.model.SystemRole;
import java.util.Optional;
import java.util.Set;

/**
 * RBAC管理服务接口，提供对角色和权限的创建、更新、删除等管理功能。
 */
public interface IRbacAdminService {
    
    /**
     * 根据角色代码查找角色。
     *
     * @param roleCode 角色代码
     *
     * @return 如果找到，则返回包含角色的Optional，否则返回空的Optional
     */
    Optional<Role> findRoleByCode(String roleCode);
    
    /**
     * 根据权限字符串查找权限。
     *
     * @param permission 权限字符串 (e.g., "user:create")
     *
     * @return 如果找到，则返回包含权限的Optional，否则返回空的Optional
     */
    Optional<Permission> findPermissionByName(String permission);
    
    /**
     * 创建一个新的角色，并关联指定的权限。
     *
     * @param role        包含角色信息的枚举实例
     * @param permissions 与该角色关联的权限集合
     *
     * @return 创建后的角色对象
     */
    Role createRoleWithPermissions(SystemRole role, Set<Permission> permissions);
    
    /**
     * 创建一个新的权限。
     *
     * @param permission 包含权限信息的枚举实例
     *
     * @return 创建后的权限对象
     */
    Permission createPermission(SystemPermission permission);
}