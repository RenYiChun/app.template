package com.lrenyi.template.platform.backend.domain;

import com.lrenyi.template.platform.annotation.PlatformEntity;
import com.lrenyi.template.platform.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 数据字典主表。
 */
@Getter
@Setter
@Entity
@Table(name = "sys_dict")
@PlatformEntity(pathSegment = "sys_dicts", displayName = "数据字典", table = "sys_dict")
public class SysDict extends BaseEntity<Long> {

    @Column(nullable = false, unique = true, length = 64, name = "dict_code")
    private String dictCode;

    @Column(nullable = false, length = 128, name = "dict_name")
    private String dictName;

    @Column(length = 256)
    private String description;
}
