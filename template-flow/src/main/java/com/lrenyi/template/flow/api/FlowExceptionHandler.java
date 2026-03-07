package com.lrenyi.template.flow.api;

import com.lrenyi.template.flow.exception.FlowExceptionContext;

/**
 * Flow 异常处理器接口
 * 用于统一处理框架中的异常
 */
public interface FlowExceptionHandler {
    
    /**
     * 处理异常
     *
     * @param context 异常上下文
     */
    void handleException(FlowExceptionContext context);
    
    /**
     * 检查是否应该处理该异常
     *
     * @param context 异常上下文
     *
     * @return true 如果应该处理，false 否则
     */
    default boolean shouldHandle(FlowExceptionContext context) {
        return true;
    }
}
