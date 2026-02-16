package com.lrenyi.template.platform.domain;

import com.lrenyi.template.platform.annotation.PlatformEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户-角色多对多关联。userId 与当前认证用户标识一致（如 username、sub）。仅开放列表/查询/新增/删除，用于分配与收回角色。
 */
@Getter
@Setter
@Entity
@Table(name = "sys_user_role")
@PlatformEntity(
        pathSegment = "user_roles",
        displayName = "用户角色",
        table = "sys_user_role",
        enableUpdate = false,
        enableUpdateBatch = false,
        enableExport = false,
        generateDtos = false
)
public class UserRole extends BaseEntity<Long> {

    @Column(nullable = false, length = 128)
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    private Role role;
}
