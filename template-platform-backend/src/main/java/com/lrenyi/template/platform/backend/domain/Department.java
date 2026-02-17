package com.lrenyi.template.platform.backend.domain;

import com.lrenyi.template.platform.annotation.PlatformEntity;
import com.lrenyi.template.platform.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 部门实体，支持树形结构。
 */
@Getter
@Setter
@Entity
@Table(name = "sys_department")
@PlatformEntity(pathSegment = "departments", displayName = "部门", table = "sys_department")
public class Department extends BaseEntity<Long> {

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(length = 64)
    private String leader;

    @Column(length = 20)
    private String phone;

    @Column(length = 128)
    private String email;

    @Column(length = 1)
    private String status; // 0-停用 1-正常
}
