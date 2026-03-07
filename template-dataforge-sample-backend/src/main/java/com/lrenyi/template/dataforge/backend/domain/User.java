package com.lrenyi.template.dataforge.backend.domain;

import com.lrenyi.template.dataforge.annotation.DataforgeDto;
import com.lrenyi.template.dataforge.annotation.DataforgeEntity;
import com.lrenyi.template.dataforge.annotation.DataforgeField;
import com.lrenyi.template.dataforge.annotation.DtoType;
import com.lrenyi.template.dataforge.jpa.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "users")
@DataforgeEntity(pathSegment = "users", displayName = "用户")
@DataforgeDto(parentFieldName = "createBy", include = {DtoType.PAGE_RESPONSE, DtoType.RESPONSE})
@DataforgeField(parentFieldName = "createBy", label = "创建人", columnOrder = 9)
@DataforgeDto(parentFieldName = "updateBy", include = {DtoType.PAGE_RESPONSE, DtoType.RESPONSE})
@DataforgeField(parentFieldName = "updateBy", label = "更新人", columnOrder = 10)
@DataforgeDto(parentFieldName = "createTime", include = {DtoType.PAGE_RESPONSE, DtoType.RESPONSE})
@DataforgeField(parentFieldName = "createTime", label = "创建时间", columnOrder = 7)
@DataforgeDto(parentFieldName = "updateTime", include = {DtoType.PAGE_RESPONSE, DtoType.RESPONSE})
@DataforgeField(parentFieldName = "updateTime", label = "更新时间", columnOrder = 8)
@DataforgeDto(parentFieldName = "remark", include = {DtoType.RESPONSE})
@DataforgeField(parentFieldName = "remark", label = "备注")
@DataforgeField(parentFieldName = "id", label = "编号", columnOrder = 1, columnWidth = 0)
public class User extends BaseEntity<Long> {
    
    @Column(nullable = false, length = 64)
    @DataforgeField(searchable = true, label = "用户名", searchOrder = 1, columnOrder = 2)
    @DataforgeDto(include = {DtoType.CREATE, DtoType.UPDATE, DtoType.RESPONSE, DtoType.PAGE_RESPONSE})
    private String username;
    
    @Column(length = 64)
    @DataforgeField(searchable = true, label = "昵称", searchOrder = 4, columnOrder = 3)
    @DataforgeDto(include = {DtoType.PAGE_RESPONSE})
    private String nickname;
    
    @Column(nullable = false, length = 128)
    @DataforgeDto(include = {DtoType.CREATE, DtoType.UPDATE})
    private String password;
    
    @Column(length = 128)
    @DataforgeField(searchable = true, label = "邮箱", searchOrder = 3, columnOrder = 4)
    @DataforgeDto(include = {DtoType.CREATE, DtoType.UPDATE, DtoType.RESPONSE, DtoType.PAGE_RESPONSE})
    private String email;
    
    @Column(length = 20)
    @DataforgeField(searchable = true, label = "电话号码", searchOrder = 2, columnOrder = 5)
    @DataforgeDto(include = {DtoType.PAGE_RESPONSE})
    private String phone;
    
    @Column(name = "department_id")
    private Long departmentId;
    
    @Column(length = 1)
    @DataforgeField(searchable = true, label = "状态", searchOrder = 5, columnOrder = 6)
    @DataforgeDto(include = {DtoType.PAGE_RESPONSE})
    private String status; // 0-停用 1-正常
    
    @Column(length = 256)
    private String avatar;
}
