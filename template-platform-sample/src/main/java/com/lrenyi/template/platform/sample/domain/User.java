package com.lrenyi.template.platform.sample.domain;

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

    @Column(length = 128)
    private String email;
}
