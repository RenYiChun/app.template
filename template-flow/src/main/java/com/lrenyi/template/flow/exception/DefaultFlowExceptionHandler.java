package com.lrenyi.template.flow.exception;

import com.lrenyi.template.flow.api.FlowExceptionHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认 Flow 异常处理器
 * 记录日志，可以扩展为发送告警、记录指标等
 */
@Slf4j
public class DefaultFlowExceptionHandler implements FlowExceptionHandler {
    
    @Override
    public void handleException(FlowExceptionContext context) {
        String jobId = context.getJobId();
        String entryId = context.getEntryId();
        FlowPhase phase = context.getPhase();
        String errorType = context.getErrorType();
        Throwable exception = context.getException();
        
        // 构建详细的错误信息
        StringBuilder message = new StringBuilder();
        message.append("Flow异常 [jobId=").append(jobId);
        if (entryId != null) {
            message.append(", entryId=").append(entryId);
        }
        message.append(", phase=").append(phase);
        if (errorType != null) {
            message.append(", errorType=").append(errorType);
        }
        
        // 添加上下文信息
        if (!context.getContext().isEmpty()) {
            message.append(", context=").append(context.getContext());
        }
        message.append("]");
        
        // 根据异常类型和阶段选择日志级别
        if (isCriticalException(exception, phase)) {
            log.error(message.toString(), exception);
        } else {
            log.warn(message.toString(), exception);
        }
        
        // 可以扩展：发送告警、记录指标等
    }
    
    /**
     * 判断是否为严重异常
     */
    private boolean isCriticalException(Throwable exception, FlowPhase phase) {
        // 存储阶段的异常通常比较严重，可能导致数据丢失
        if (phase == FlowPhase.STORAGE) {
            return true;
        }
        
        // OutOfMemoryError、StackOverflowError 等严重错误
        return exception instanceof VirtualMachineError;
    }
}
