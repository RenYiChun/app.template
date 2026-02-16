package com.lrenyi.template.platform.domain;

import com.lrenyi.template.platform.annotation.PlatformEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@PlatformEntity(
        pathSegment = "permissions",
        displayName = "权限",
        table = "sys_permission",
        enableExport = false
)
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String permission;

    @Column(length = 128)
    private String name;

    @Column(length = 256)
    private String description;
}
