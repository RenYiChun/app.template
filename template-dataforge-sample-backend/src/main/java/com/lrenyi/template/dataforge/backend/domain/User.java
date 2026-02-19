package com.lrenyi.template.dataforge.backend.domain;

import com.lrenyi.template.dataforge.annotation.DtoExcludeFrom;
import com.lrenyi.template.dataforge.annotation.DtoType;
import com.lrenyi.template.dataforge.annotation.DataforgeEntity;
import com.lrenyi.template.dataforge.domain.BaseEntity;
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
    private String username;

    @Column(length = 64)
    private String nickname;

    @Column(nullable = false, length = 128)
    @DtoExcludeFrom(DtoType.RESPONSE)
    private String password;

    @Column(length = 128)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(length = 1)
    private String status; // 0-停用 1-正常

    @Column(length = 256)
    private String avatar;
}
