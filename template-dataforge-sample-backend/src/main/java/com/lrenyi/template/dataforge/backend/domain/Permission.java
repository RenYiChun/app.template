package com.lrenyi.template.dataforge.backend.domain;

import com.lrenyi.template.dataforge.annotation.DataforgeEntity;
import com.lrenyi.template.dataforge.jpa.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * RBAC 权限实体，权限字符串与 EntityMeta/ActionMeta 中使用的标识一致（如 "user:create"）。
 */
@Getter
@Setter
@Entity
@Table(name = "sys_permission")
@DataforgeEntity(
        pathSegment = "permissions", displayName = "权限", table = "sys_permission", enableExport = false
)
public class Permission extends BaseEntity<Long> {
    
    @Column(name = "permission", nullable = false, unique = true, length = 128)
    private String permissionCode;
    
    @Column(length = 128)
    private String name;
    
    @Column(length = 256)
    private String description;
}
