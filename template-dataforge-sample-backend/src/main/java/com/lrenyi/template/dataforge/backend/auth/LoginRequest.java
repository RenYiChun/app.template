package com.lrenyi.template.dataforge.backend.auth;

import com.lrenyi.template.dataforge.backend.domain.LoginCredentials;
import lombok.Getter;
import lombok.Setter;

/**
 * 登录请求体（应用层 DTO）。username、password 归入域 {@link LoginCredentials}，
 * captchaKey、captchaCode 为验证码机制，留于应用层。
 */
@Getter
@Setter
public class LoginRequest {
    
    private String username;
    private String password;
    private String captchaKey;
    private String captchaCode;
    
    /** 提取域内登录凭证 */
    public LoginCredentials toCredentials() {
        return new LoginCredentials(username, password);
    }
}
