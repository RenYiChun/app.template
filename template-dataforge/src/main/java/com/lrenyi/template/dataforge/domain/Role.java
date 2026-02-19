package com.lrenyi.template.dataforge.domain;

import com.lrenyi.template.dataforge.annotation.DataforgeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * RBAC 角色实体。
 */
@Getter
@Setter
@Entity
@Table(name = "sys_role")
@DataforgeEntity(
        pathSegment = "roles",
        displayName = "角色",
        table = "sys_role",
        enableExport = false
)
public class Role extends BaseEntity<Long> {

    @Column(nullable = false, unique = true, length = 64)
    private String roleCode;

    @Column(length = 128)
    private String roleName;
}
