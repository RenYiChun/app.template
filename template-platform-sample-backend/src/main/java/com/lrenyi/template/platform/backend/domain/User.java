package com.lrenyi.template.platform.backend.domain;

import com.lrenyi.template.platform.annotation.DtoExcludeFrom;
import com.lrenyi.template.platform.annotation.DtoType;
import com.lrenyi.template.platform.annotation.PlatformEntity;
import com.lrenyi.template.platform.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "users")
@PlatformEntity(pathSegment = "users", displayName = "用户")
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
