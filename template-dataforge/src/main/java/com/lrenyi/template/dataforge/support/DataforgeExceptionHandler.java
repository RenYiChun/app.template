package com.lrenyi.template.dataforge.support;

import com.lrenyi.template.core.util.Result;
import com.lrenyi.template.dataforge.config.DataforgeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 实体平台统一异常处理，直接返回 Result（与 Controller 一致，不再使用 ResponseEntity）。
 * HttpStatusException 及其子类返回对应 HTTP 状态码。
 * 生产环境应配置 app.dataforge.expose-exception-message=false，避免向客户端泄露敏感信息。
 */
@RestControllerAdvice(basePackages = "com.lrenyi.template.dataforge.controller")
public class DataforgeExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(DataforgeExceptionHandler.class);

    private static final String GENERIC_ERROR_MESSAGE = "服务器内部错误";

    private final DataforgeProperties properties;

    public DataforgeExceptionHandler(DataforgeProperties properties) {
        this.properties = properties;
    }

    @ExceptionHandler(HttpStatusException.class)
    public ResponseEntity<Result<Object>> handleHttpStatusException(HttpStatusException e) {
        log.debug("HTTP status exception: {} - {}", e.getStatusCode(), e.getMessage());
        String message = properties.isExposeExceptionMessage() ? e.getMessage() : GENERIC_ERROR_MESSAGE;
        Result<Object> r = Result.getError(null, message);
        r.setCode(e.getStatusCode());
        return ResponseEntity.status(e.getStatusCode()).body(r);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Object> handleBadRequest(IllegalArgumentException e) {
        log.debug("Bad request: {}", e.getMessage());
        String message = properties.isExposeExceptionMessage() ? e.getMessage() : "请求参数错误";
        Result<Object> r = Result.getError(null, message);
        r.setCode(400);
        return r;
    }

    @ExceptionHandler(IllegalStateException.class)
    public Result<Object> handleIllegalState(IllegalStateException e) {
        log.warn("Illegal state: {}", e.getMessage());
        String message = properties.isExposeExceptionMessage() ? e.getMessage() : GENERIC_ERROR_MESSAGE;
        return Result.getError(null, message);
    }

    @ExceptionHandler(Exception.class)
    public Result<Object> handleOther(Exception e) {
        log.error("Unhandled error", e);
        String message = properties.isExposeExceptionMessage() ? e.getMessage() : GENERIC_ERROR_MESSAGE;
        return Result.getError(null, message);
    }
}
