package com.lrenyi.template.fastgen.example.domain;

import com.lrenyi.template.fastgen.annotation.FormField;
import com.lrenyi.template.fastgen.annotation.Page;

/**
 * 示例页面描述。编译后 APT 会将其纳入 META-INF/fastgen/snapshot.json 的 pages。
 */
@Page(title = "登录页", layout = "centered", path = "/login", apiPath = "/api/auth/login", successPath = "/")
public class LoginPage {

    @FormField(label = "账号", required = true)
    private String username;

    @FormField(label = "密码", type = "password", required = true)
    private String password;

    @FormField(label = "验证码Key", type = "hidden")
    private String captchaKey;

    @FormField(label = "验证码", type = "captcha", required = true)
    private String captchaCode;

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

    public String getCaptchaKey() {
        return captchaKey;
    }

    public void setCaptchaKey(String captchaKey) {
        this.captchaKey = captchaKey;
    }

    public String getCaptchaCode() {
        return captchaCode;
    }

    public void setCaptchaCode(String captchaCode) {
        this.captchaCode = captchaCode;
    }
}
