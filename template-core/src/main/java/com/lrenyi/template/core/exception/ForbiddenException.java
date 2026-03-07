package com.lrenyi.template.core.exception;

/**
 * 权限不足。API 层映射为 HTTP 403。
 */
public class ForbiddenException extends TemplateException {
    
    public ForbiddenException(String message) {
        super(403, message);
    }
}
