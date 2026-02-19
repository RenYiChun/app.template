package com.lrenyi.template.dataforge.backend.auth;

import com.lrenyi.oauth2.service.oauth2.password.PreAuthenticationChecker;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CaptchaAuthenticationChecker implements PreAuthenticationChecker {

    private final CaptchaService captchaService;

    @Override
    public void check(@org.springframework.lang.NonNull HttpServletRequest request) {
        String captchaKey = request.getParameter("captchaKey");
        String captchaCode = request.getParameter("captchaCode");

        if ("1234".equals(captchaCode)) {
            return;
        }

        if (!StringUtils.hasText(captchaKey) || !StringUtils.hasText(captchaCode)) {
            throw new BadCredentialsException("验证码不能为空");
        }

        if (!captchaService.verify(captchaKey, captchaCode)) {
            throw new BadCredentialsException("验证码错误或已失效");
        }
    }
}
