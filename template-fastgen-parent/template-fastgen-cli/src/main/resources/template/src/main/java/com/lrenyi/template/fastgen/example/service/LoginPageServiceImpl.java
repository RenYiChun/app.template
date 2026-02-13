package com.lrenyi.template.fastgen.example.service;

import com.lrenyi.gen.service.LoginPageService;
import com.lrenyi.gen.domain.LoginPageRequest;
import com.lrenyi.template.fastgen.example.web.CaptchaHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.UUID;

/**
 * 登录域唯一 Service：提交登录（含验证码校验）与获取验证码。
 */
@Service
public class LoginPageServiceImpl implements LoginPageService {

    private static final String SESSION_PREFIX = "captcha:";

    private final HttpServletRequest httpRequest;

    public LoginPageServiceImpl(HttpServletRequest httpRequest) {
        this.httpRequest = httpRequest;
    }

    @Override
    public ResponseEntity<Map<String, Object>> handleSubmit(LoginPageRequest request) {
        String validated = validateAndRemove(
            httpRequest.getSession(false),
            request.getCaptchaKey(),
            request.getCaptchaCode()
        );
        if (validated == null) {
            return ResponseEntity.status(400).body(Map.of(
                "success", false,
                "message", "验证码错误或已失效，请刷新后重试"
            ));
        }

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

    @Override
    public Map<String, String> getCaptcha(HttpSession session) {
        String key = UUID.randomUUID().toString();
        String code = CaptchaHelper.generateCode();
        session.setAttribute(SESSION_PREFIX + key, code);
        try {
            String imageBase64 = CaptchaHelper.drawToBase64(code);
            return Map.of("key", key, "imageBase64", imageBase64);
        } catch (Exception e) {
            throw new RuntimeException("验证码生成失败", e);
        }
    }

    private static String validateAndRemove(HttpSession session, String key, String userInput) {
        if (session == null || key == null || key.isBlank() || userInput == null) {
            return null;
        }
        String stored = (String) session.getAttribute(SESSION_PREFIX + key);
        session.removeAttribute(SESSION_PREFIX + key);
        if (stored == null || !stored.equalsIgnoreCase(userInput.trim())) {
            return null;
        }
        return stored;
    }
}
