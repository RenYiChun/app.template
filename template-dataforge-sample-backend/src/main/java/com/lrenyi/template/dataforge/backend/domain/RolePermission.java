package com.lrenyi.template.dataforge.backend.domain;

import com.lrenyi.template.dataforge.annotation.DataforgeEntity;
import com.lrenyi.template.dataforge.jpa.domain.BaseEntity;
import com.lrenyi.template.dataforge.backend.domain.Permission;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色-权限多对多关联。仅开放列表/查询/新增/删除，用于分配与收回权限。
 */
@Getter
@Setter
@Entity
@Table(name = "sys_role_permission")
@DataforgeEntity(
        pathSegment = "role_permissions",
        displayName = "角色权限",
        table = "sys_role_permission",
        enableUpdate = false,
        enableUpdateBatch = false,
        enableExport = false,
        generateDtos = false
)
public class RolePermission extends BaseEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @jakarta.persistence.JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @jakarta.persistence.JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;
}
