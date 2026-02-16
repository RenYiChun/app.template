package com.lrenyi.template.platform.support;

import com.lrenyi.template.core.util.Result;
import com.lrenyi.template.platform.config.EntityPlatformProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 实体平台统一异常处理，直接返回 Result（与 Controller 一致，不再使用 ResponseEntity）。
 * 生产环境应配置 app.platform.expose-exception-message=false，避免向客户端泄露敏感信息。
 */
@RestControllerAdvice(basePackages = "com.lrenyi.template.platform.controller")
public class EntityPlatformExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(EntityPlatformExceptionHandler.class);

    private static final String GENERIC_ERROR_MESSAGE = "服务器内部错误";

    private final EntityPlatformProperties properties;

    public EntityPlatformExceptionHandler(EntityPlatformProperties properties) {
        this.properties = properties;
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
