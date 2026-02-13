package com.lrenyi.template.fastgen.example.domain;

import com.lrenyi.template.fastgen.annotation.Column;
import com.lrenyi.template.fastgen.annotation.Domain;
import com.lrenyi.template.fastgen.annotation.FormField;
import com.lrenyi.template.fastgen.annotation.GeneratedValue;
import com.lrenyi.template.fastgen.annotation.Id;

/**
 * 示例领域实体。编译后 APT 会收集元数据并写入 META-INF/fastgen/snapshot.json。
 */
@Domain(table = "sys_user", displayName = "用户")
public class User {

    @Id
    @GeneratedValue
    private Long id;

    @Column(length = 50, nullable = false)
    @FormField(label = "用户名", required = true)
    private String username;

    @Column(nullable = false)
    @FormField(label = "密码", type = "password")
    private String password;

    @FormField(label = "邮箱", type = "email")
    private String email;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
