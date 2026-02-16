package com.lrenyi.template.platform.support;

import com.lrenyi.template.core.util.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 实体平台统一异常处理，直接返回 Result（与 Controller 一致，不再使用 ResponseEntity）。
 */
@RestControllerAdvice(basePackages = "com.lrenyi.template.platform.controller")
public class EntityPlatformExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(EntityPlatformExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Object> handleBadRequest(IllegalArgumentException e) {
        log.debug("Bad request: {}", e.getMessage());
        Result<Object> r = Result.getError(null, e.getMessage());
        r.setCode(400);
        return r;
    }

    @ExceptionHandler(IllegalStateException.class)
    public Result<Object> handleIllegalState(IllegalStateException e) {
        log.warn("Illegal state: {}", e.getMessage());
        return Result.getError(null, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Object> handleOther(Exception e) {
        log.error("Unhandled error", e);
        return Result.getError(null, e.getMessage());
    }
}
