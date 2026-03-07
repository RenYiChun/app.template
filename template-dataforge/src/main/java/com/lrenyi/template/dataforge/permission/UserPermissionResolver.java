package com.lrenyi.template.dataforge.permission;

import java.util.Set;
import org.springframework.security.core.Authentication;

/**
 * 按当前用户解析其拥有的权限标识集合（用于动态 RBAC 校验）。
 * 实现方可从 DB/缓存查询 UserRole -> Role -> Permission，使角色权限变更在下次请求或缓存失效后生效。
 */
public interface UserPermissionResolver {
    
    /**
     * 根据当前认证信息返回该用户拥有的权限字符串集合。
     *
     * @param authentication 当前请求的认证信息，可为 null（未认证时返回空集合）
     * @return 权限标识集合（如 "user:create", "user:read"），未认证或无法解析时返回空集合，不返回 null
     */
    Set<String> getPermissions(Authentication authentication);
}
