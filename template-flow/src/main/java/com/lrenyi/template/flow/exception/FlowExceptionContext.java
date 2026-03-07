package com.lrenyi.template.flow.exception;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

/**
 * Flow 异常上下文
 * 包含异常发生的完整上下文信息
 */
@Getter
public class FlowExceptionContext {
    private final String jobId;
    private final String entryId;
    private final Throwable exception;
    private final FlowPhase phase;
    private final String errorType;
    private final Map<String, Object> context;

    public FlowExceptionContext(String jobId, String entryId, Throwable exception, FlowPhase phase) {
        this(jobId, entryId, exception, phase, null);
    }

    public FlowExceptionContext(String jobId, String entryId, Throwable exception, FlowPhase phase, String errorType) {
        this.jobId = jobId;
        this.entryId = entryId;
        this.exception = exception;
        this.phase = phase;
        this.errorType = errorType;
        this.context = new HashMap<>();
    }
    
    /**
     * 添加上下文信息
     */
    public FlowExceptionContext addContext(String key, Object value) {
        context.put(key, value);
        return this;
    }
    
    /**
     * 获取上下文信息
     */
    @SuppressWarnings("unchecked")
    public <T> T getContext(String key, Class<T> type) {
        Object value = context.get(key);
        if (type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
}
