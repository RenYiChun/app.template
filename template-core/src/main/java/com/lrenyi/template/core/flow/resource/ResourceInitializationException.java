package com.lrenyi.template.core.flow.resource;

/**
 * 资源初始化异常
 */
public class ResourceInitializationException extends Exception {
    
    public ResourceInitializationException(String message) {
        super(message);
    }
    
    public ResourceInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
