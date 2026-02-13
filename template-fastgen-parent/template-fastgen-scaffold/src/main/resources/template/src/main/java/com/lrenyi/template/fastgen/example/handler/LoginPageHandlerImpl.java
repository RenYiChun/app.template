package com.lrenyi.template.fastgen.example.handler;

import com.lrenyi.gen.service.LoginPageHandler;
import com.lrenyi.gen.domain.LoginPageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 登录页业务实现（扩展点示例）。
 * 使用方式：复制为 LoginPageHandlerImpl.java，实现生成的 LoginPageHandler 接口。
 * 可在此编写真实登录逻辑（查库、校验、发 Token 等）。
 */
@Service
public class LoginPageHandlerImpl implements LoginPageHandler {

    @Override
    public ResponseEntity<Map<String, Object>> handleSubmit(LoginPageRequest request) {
        // 示例：简单校验，实际可注入 UserService 查库、发 JWT 等
        if ("admin".equals(request.getUsername()) && "123456".equals(request.getPassword())) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "登录成功",
                "token", "demo-token-" + System.currentTimeMillis()
            ));
        }
        return ResponseEntity.status(401).body(Map.of(
            "success", false,
            "message", "账号或密码错误"
        ));
    }
}
