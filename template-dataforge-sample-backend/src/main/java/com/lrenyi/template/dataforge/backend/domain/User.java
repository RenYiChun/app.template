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
public class User extends BaseEntity<Long> {
    
    @Column(nullable = false, length = 64)
    @DataforgeField(searchable = true, label = "用户名", order = 1)
    @DataforgeDto(include = {DtoType.CREATE, DtoType.UPDATE, DtoType.RESPONSE, DtoType.PAGE_RESPONSE})
    private String username;
    
    @Column(length = 64)
    @DataforgeField(searchable = true, label = "昵称", order = 4)
    @DataforgeDto(include = {DtoType.PAGE_RESPONSE})
    private String nickname;
    
    @Column(nullable = false, length = 128)
    @DataforgeDto(include = {DtoType.CREATE, DtoType.UPDATE})
    private String password;
    
    @Column(length = 128)
    @DataforgeField(searchable = true, label = "邮箱", order = 3)
    @DataforgeDto(include = {DtoType.CREATE, DtoType.UPDATE, DtoType.RESPONSE, DtoType.PAGE_RESPONSE})
    private String email;
    
    @Column(length = 20)
    @DataforgeField(searchable = true, label = "电话号码", order = 2)
    @DataforgeDto(include = {DtoType.PAGE_RESPONSE})
    private String phone;
    
    @Column(name = "department_id")
    private Long departmentId;
    
    @Column(length = 1)
    @DataforgeField(searchable = true, label = "状态", order = 5)
    @DataforgeDto(include = {DtoType.PAGE_RESPONSE})
    private String status; // 0-停用 1-正常
    
    @Column(length = 256)
    private String avatar;
}
