package com.lrenyi.template.platform.support;

/**
 * 携带 HTTP 状态码的异常，供 PlatformExceptionHandler 统一处理并返回对应 4xx/5xx 状态。
 */
public abstract class HttpStatusException extends RuntimeException {

    private final int statusCode;

    protected HttpStatusException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
