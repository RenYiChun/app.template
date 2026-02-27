package com.lrenyi.template.dataforge.support;

import com.lrenyi.template.core.exception.TemplateException;

/**
 * 携带 HTTP 状态码的异常，供 DataforgeExceptionHandler 统一处理并返回对应 4xx/5xx 状态。
 * 继承 {@link TemplateException}，复用框架统一异常体系。
 */
public abstract class HttpStatusException extends TemplateException {

    protected HttpStatusException(int statusCode, String message) {
        super(statusCode, message);
    }

    public int getStatusCode() {
        return getErrorCode();
    }
}
