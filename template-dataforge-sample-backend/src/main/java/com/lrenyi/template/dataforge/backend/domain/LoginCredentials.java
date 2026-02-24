package com.lrenyi.template.dataforge.backend.domain;

/**
 * 登录凭证（域值对象），封装用户名与密码。
 */
public record LoginCredentials(String username, String password) {

    public String usernameTrimmed() {
        return username != null ? username.trim() : null;
    }
}
