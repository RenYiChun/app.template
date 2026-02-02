package com.lrenyi.template.core.flow.resource;

import java.util.List;
import lombok.Getter;

/**
 * 资源关闭异常
 * 可能包含多个资源的关闭错误
 */
@Getter
public class ResourceShutdownException extends Exception {
    private final List<Exception> errors;
    
    public ResourceShutdownException(String message) {
        super(message);
        this.errors = List.of();
    }
    
    public ResourceShutdownException(String message, List<Exception> errors) {
        super(message);
        this.errors = errors != null ? List.copyOf(errors) : List.of();
    }
    
    public ResourceShutdownException(String message, Throwable cause) {
        super(message, cause);
        this.errors = List.of();
    }
    
    /**
     * 检查是否有多个错误
     *
     * @return true 如果有多个错误
     */
    public boolean hasMultipleErrors() {
        return !errors.isEmpty();
    }
}
