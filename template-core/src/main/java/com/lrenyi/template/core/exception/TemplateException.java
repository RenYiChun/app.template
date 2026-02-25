package com.lrenyi.template.core.exception;

import lombok.Getter;

/**
 * 框架统一异常基类。
 * <p>
 * errorCode 为业务错误码（非 HTTP 状态码），各模块可定义自己的错误码区间。
 * httpStatus 由 API 层的全局异常处理器根据异常子类自动映射，领域层无需关心。
 */
@Getter
public class TemplateException extends RuntimeException {

    private final int errorCode;

    public TemplateException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public TemplateException(int errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
