package com.lrenyi.template.flow.exception;

import java.util.Map;
import com.lrenyi.template.flow.api.FlowExceptionHandler;
import com.lrenyi.template.flow.util.FlowLogHelper;
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
        String displayName = (String) context.getContext().get("displayName");
        StringBuilder message = new StringBuilder();
        message.append("Flow异常 [").append(FlowLogHelper.formatJobContext(jobId, displayName));
        if (entryId != null) {
            message.append(", entryId=").append(entryId);
        }
        message.append(", phase=").append(phase);
        if (errorType != null) {
            message.append(", errorType=").append(errorType);
        }
        
        // 添加上下文信息（排除 displayName，已用于 formatJobContext）
        Map<String, Object> ctx = new java.util.HashMap<>(context.getContext());
        ctx.remove("displayName");
        if (!ctx.isEmpty()) {
            message.append(", context=").append(ctx);
        }
        message.append("]");
        
        if (isExpectedInterruption(context, exception)) {
            log.debug(message.toString());
            return;
        }
        
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

    private boolean isExpectedInterruption(FlowExceptionContext context, Throwable exception) {
        return exception instanceof InterruptedException
                && (Boolean.TRUE.equals(context.getContext().get("expectedInterruption"))
                || isStorageAcquireInterrupted(context));
    }

    private boolean isStorageAcquireInterrupted(FlowExceptionContext context) {
        return context.getPhase() == FlowPhase.STORAGE
                && "storage_acquire_interrupted".equals(context.getErrorType());
    }
}
