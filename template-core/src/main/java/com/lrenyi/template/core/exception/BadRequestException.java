package com.lrenyi.template.core.exception;

/**
 * 请求参数错误。API 层映射为 HTTP 400。
 */
public class BadRequestException extends TemplateException {

    public BadRequestException(String message) {
        super(400, message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(400, message, cause);
    }
}
