package com.lrenyi.template.dataforge.backend.auth;

import com.lrenyi.template.dataforge.annotation.EntityAction;
import com.lrenyi.template.dataforge.backend.domain.Auth;
import com.lrenyi.template.dataforge.action.EntityActionExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;

@Component
@RequiredArgsConstructor
@EntityAction(entity = Auth.class, actionName = "captcha", method = RequestMethod.GET, summary = "获取验证码", requireId = false)
public class AuthCaptchaAction implements EntityActionExecutor {

    private final CaptchaService captchaService;

    @Override
    public Object execute(Object entityId, Object request) {
        CaptchaService.CaptchaResult result = captchaService.generate();
        return java.util.Map.of("key", result.key(), "imageBase64", result.imageBase64());
    }
}
