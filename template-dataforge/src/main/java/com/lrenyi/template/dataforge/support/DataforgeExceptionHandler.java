package com.lrenyi.template.dataforge.support;

import com.lrenyi.template.core.util.Result;
import com.lrenyi.template.dataforge.config.DataforgeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Dataforge 模块异常处理，优先级高于框架全局 Handler。
 * HttpStatusException 及其子类返回对应 HTTP 状态码。
 * 生产环境应配置 app.dataforge.expose-exception-message=false，避免向客户端泄露敏感信息。
 */
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE - 20)
@RestControllerAdvice(basePackages = "com.lrenyi.template.dataforge.controller")
public class DataforgeExceptionHandler {
    
    private static final String GENERIC_ERROR_MESSAGE = "服务器内部错误";
    
    private final DataforgeProperties properties;
    
    public DataforgeExceptionHandler(DataforgeProperties properties) {
        this.properties = properties;
    }
    
    @ExceptionHandler(DataforgeHttpException.class)
    public ResponseEntity<Result<Object>> handleDataforgeHttpException(DataforgeHttpException e) {
        log.debug("Dataforge HTTP exception: status={}, code={}, message={}", e.getHttpStatus(), e.getErrorCode(), e.getMessage());
        String message = properties.isExposeExceptionMessage() ? e.getMessage() : GENERIC_ERROR_MESSAGE;
        Result<Object> r = Result.getError(null, message);
        r.setCode(e.getErrorCode());
        return ResponseEntity.status(e.getHttpStatus()).body(r);
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
    public ResponseEntity<Result<Object>> handleBadRequest(IllegalArgumentException e) {
        log.debug("Bad request: {}", e.getMessage());
        String message = properties.isExposeExceptionMessage() ? e.getMessage() : "请求参数错误";
        Result<Object> r = Result.getError(null, message);
        r.setCode(400);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(r);
    }
    
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Result<Object>> handleIllegalState(IllegalStateException e) {
        log.warn("Illegal state: {}", e.getMessage());
        String message = properties.isExposeExceptionMessage() ? e.getMessage() : GENERIC_ERROR_MESSAGE;
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.getError(null, message));
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Object>> handleOther(Exception e) {
        log.error("Unhandled error", e);
        String message = properties.isExposeExceptionMessage() ? e.getMessage() : GENERIC_ERROR_MESSAGE;
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.getError(null, message));
    }
}
