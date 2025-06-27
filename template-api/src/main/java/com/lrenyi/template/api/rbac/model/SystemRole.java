package com.lrenyi.template.api.rbac.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;

import static com.lrenyi.template.api.rbac.model.SystemPermission.*;

public enum SystemRole {
    //-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-
    // 职责分离的三员（用户管理员、授权管理员、审计管理员）
    //-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-
    /**
     * 用户管理员：负责用户账户的创建、维护和删除，但不涉及权限分配。
     */
    USER_ADMIN("UserAdmin",
               "用户管理员",
               new HashSet<>(Arrays.asList(USER_CREATE, USER_READ, USER_UPDATE, USER_DELETE, USER_LIST))
    ),
    
    /**
     * 授权管理员：负责角色和权限的定义与分配，但不管理具体用户。
     */
    AUTHORIZATION_ADMIN("AuthorizationAdmin",
                        "授权管理员",
                        new HashSet<>(Arrays.asList(ROLE_CREATE,
                                                    ROLE_READ,
                                                    ROLE_UPDATE,
                                                    ROLE_DELETE,
                                                    ROLE_ASSIGN,
                                                    PERMISSION_READ
                        ))
    ),
    
    /**
     * 审计管理员：负责审查系统日志和操作记录，确保合规性，但不参与任何实际操作。
     */
    AUDIT_ADMIN("AuditAdmin", "审计管理员", new HashSet<>(Collections.singletonList(AUDIT_READ)));
    
    @Getter
    private final String roleCode;
    @Getter
    private final String roleName;
    private final Set<SystemPermission> permissions;
    
    SystemRole(String roleCode, String roleName, Set<SystemPermission> permissions) {
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.permissions = permissions;
    }
    
    public Set<SystemPermission> getPermissions() {
        return Collections.unmodifiableSet(permissions);
    }
}