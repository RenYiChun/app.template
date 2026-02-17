package com.lrenyi.template.platform.backend.auth;

import com.lrenyi.template.platform.support.HttpStatusException;

/**
 * 认证异常，携带 4xx 状态码与提示信息。由 platform 的 PlatformExceptionHandler 统一处理。
 */
public class AuthException extends HttpStatusException {

    public AuthException(int code, String message) {
        super(code, message);
    }
}
