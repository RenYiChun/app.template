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
 * RBAC 角色实体。
 */
@Getter
@Setter
@Entity
@Table(name = "sys_role")
@PlatformEntity(
        pathSegment = "roles",
        displayName = "角色",
        table = "sys_role",
        enableExport = false
)
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String roleCode;

    @Column(length = 128)
    private String roleName;
}
