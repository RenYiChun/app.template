package com.lrenyi.template.core.exception;

/**
 * 服务内部错误。API 层映射为 HTTP 500。
 */
public class ServiceException extends TemplateException {
    
    public ServiceException(String message) {
        super(500, message);
    }
    
    public ServiceException(String message, Throwable cause) {
        super(500, message, cause);
    }
}
