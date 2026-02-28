package com.lrenyi.template.core.exception;

/**
 * 资源不存在。API 层映射为 HTTP 404。
 */
public class NotFoundException extends TemplateException {
    
    public NotFoundException(String message) {
        super(404, message);
    }
    
    public NotFoundException(String resource, Object id) {
        super(404, resource + " 不存在: " + id);
    }
}
