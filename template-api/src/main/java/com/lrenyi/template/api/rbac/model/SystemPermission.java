package com.lrenyi.template.api.rbac.model;

import lombok.Getter;

@Getter
public enum SystemPermission {
    // User Management
    //@formatter:off
    USER_CREATE("user:create", "创建用户"),
    USER_READ("user:read", "读取用户"),
    USER_UPDATE("user:update", "更新用户"),
    USER_DELETE("user:delete", "删除用户"),
    USER_LIST("user:list", "查看用户列表"),

    // Role & Permission Management
    ROLE_CREATE("role:create", "创建角色"),
    ROLE_READ("role:read", "读取角色"),
    ROLE_UPDATE("role:update", "更新角色"),
    ROLE_DELETE("role:delete", "删除角色"),
    ROLE_ASSIGN("role:assign", "为用户分配角色"),
    PERMISSION_READ("permission:read", "查看权限列表"),

    // Audit Management
    AUDIT_READ("audit:read", "查看审计日志");
    //@formatter:on
    private final String permission;
    private final String description;
    
    SystemPermission(String permission, String description) {
        this.permission = permission;
        this.description = description;
    }
}