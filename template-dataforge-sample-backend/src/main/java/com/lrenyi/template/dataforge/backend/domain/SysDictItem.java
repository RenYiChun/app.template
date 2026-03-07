package com.lrenyi.template.dataforge.backend.domain;

import com.lrenyi.template.dataforge.annotation.DataforgeEntity;
import com.lrenyi.template.dataforge.jpa.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 数据字典项。
 */
@Getter
@Setter
@Entity
@Table(name = "sys_dict_item")
@DataforgeEntity(pathSegment = "sys_dict_items", displayName = "字典项", table = "sys_dict_item")
public class SysDictItem extends BaseEntity<Long> {
    
    @Column(nullable = false, length = 64, name = "dict_code")
    private String dictCode;
    
    @Column(nullable = false, length = 128, name = "item_text")
    private String itemText;
    
    @Column(nullable = false, length = 128, name = "item_value")
    private String itemValue;
    
    @Column(name = "sort_order")
    private Integer sortOrder;
    
    @Column(length = 1)
    private String status; // 0-停用 1-正常
    
    @Column(length = 256)
    private String description;
}
