package com.lrenyi.template.api.config;

import com.lrenyi.template.core.exception.TemplateException;
import com.lrenyi.template.core.util.MCode;
import com.lrenyi.template.core.util.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 框架级全局异常处理器。
 * <p>
 * 统一将 {@link TemplateException} 子类映射为 HTTP 响应，所有模块共享同一错误响应契约。
 * 优先级低于模块自定义 Handler（通过 {@link Order} 控制），
 * 模块可以 {@code @RestControllerAdvice(basePackages=...)} 覆盖特定包的行为。
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 10)
@ConditionalOnProperty(name = "app.template.enabled", havingValue = "true", matchIfMissing = true)
public class GlobalExceptionHandler {

    @ExceptionHandler(TemplateException.class)
    public ResponseEntity<Result<Object>> handleTemplateException(TemplateException e) {
        int code = e.getErrorCode();
        int httpStatus = mapToHttpStatus(code);
        log.debug("TemplateException [code={}, httpStatus={}]: {}", code, httpStatus, e.getMessage());
        Result<Object> result = Result.getError(null, e.getMessage());
        result.setCode(code);
        return ResponseEntity.status(httpStatus).body(result);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Object>> handleIllegalArgument(IllegalArgumentException e) {
        log.debug("Bad request: {}", e.getMessage());
        Result<Object> result = Result.getError(null, e.getMessage());
        result.setCode(MCode.BAD_REQUEST.getCode());
        return ResponseEntity.badRequest().body(result);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Object>> handleNotFound(NoResourceFoundException e) {
        Result<Object> result = Result.getError(null, "资源不存在");
        result.setCode(MCode.NOT_EXIT_RESOURCE.getCode());
        return ResponseEntity.status(MCode.NOT_EXIT_RESOURCE.getCode()).body(result);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Object>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        log.debug("Validation failed: {}", message);
        Result<Object> result = Result.getError(null, message);
        result.setCode(MCode.BAD_REQUEST.getCode());
        return ResponseEntity.badRequest().body(result);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Object>> handleGeneric(Exception e) {
        log.error("未处理异常", e);
        Result<Object> result = Result.getError(null, "服务器内部错误");
        result.setCode(MCode.EXCEPTION.getCode());
        return ResponseEntity.internalServerError().body(result);
    }

    private static int mapToHttpStatus(int errorCode) {
        return switch (errorCode) {
            case 400 -> 400;
            case 401 -> 401;
            case 403 -> 403;
            case 404 -> 404;
            default -> errorCode >= 400 && errorCode < 600 ? errorCode : 500;
        };
    }
}
