package com.lrenyi.template.flow.exception;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import com.lrenyi.template.flow.api.FlowExceptionHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * Flow 异常处理辅助类
 * 提供统一的异常处理入口
 */
@Slf4j
public class FlowExceptionHelper {
    
    private static final List<FlowExceptionHandler> handlers = new CopyOnWriteArrayList<>();
    private static volatile FlowExceptionHandler defaultHandler = new DefaultFlowExceptionHandler();
    
    static {
        // 注册默认处理器
        handlers.add(defaultHandler);
    }
    
    /**
     * 注册异常处理器
     */
    public static void registerHandler(FlowExceptionHandler handler) {
        if (handler != null) {
            handlers.add(handler);
        }
    }
    
    /**
     * 移除异常处理器
     */
    public static void removeHandler(FlowExceptionHandler handler) {
        handlers.remove(handler);
    }
    
    /**
     * 设置默认异常处理器
     */
    public static void setDefaultHandler(FlowExceptionHandler handler) {
        if (handler != null) {
            defaultHandler = handler;
            // 如果默认处理器不在列表中，添加它
            if (!handlers.contains(handler)) {
                handlers.add(handler);
            }
        }
    }
    
    /**
     * 处理异常
     *
     * @param jobId     Job ID
     * @param entryId   Entry ID（可为 null）
     * @param exception 异常
     * @param phase     阶段
     */
    public static void handleException(String jobId, String entryId, Throwable exception, FlowPhase phase) {
        FlowExceptionContext context = new FlowExceptionContext(jobId, entryId, exception, phase);
        handleException(context);
    }
    
    /**
     * 处理异常
     *
     * @param context 异常上下文
     */
    public static void handleException(FlowExceptionContext context) {
        for (FlowExceptionHandler handler : handlers) {
            try {
                if (handler.shouldHandle(context)) {
                    handler.handleException(context);
                }
            } catch (Exception e) {
                // 防止处理器本身抛出异常导致处理链中断
                log.error("异常处理器执行失败", e);
            }
        }
    }
    
    /**
     * 清除所有处理器（用于测试）
     */
    public static void clearHandlers() {
        handlers.clear();
        handlers.add(defaultHandler);
    }
}
