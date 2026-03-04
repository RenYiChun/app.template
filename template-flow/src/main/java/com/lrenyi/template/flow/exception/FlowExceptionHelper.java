package com.lrenyi.template.flow.exception;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import com.lrenyi.template.flow.api.FlowExceptionHandler;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Flow 异常处理辅助类
 * 提供统一的异常处理入口，集中记录 ERRORS 指标
 */
@Slf4j
public class FlowExceptionHelper {

    private static final List<FlowExceptionHandler> handlers = new CopyOnWriteArrayList<>();
    private static final AtomicReference<FlowExceptionHandler> defaultHandler =
            new AtomicReference<>(new DefaultFlowExceptionHandler());
    private static final AtomicReference<MeterRegistry> meterRegistryRef = new AtomicReference<>();

    static {
        handlers.add(defaultHandler.get());
    }

    private FlowExceptionHelper() {
    }
    
    /**
     * 注入 MeterRegistry，用于集中记录 ERRORS 指标。
     * 由 FlowManager 初始化时调用。
     */
    public static void setMeterRegistry(MeterRegistry meterRegistry) {
        meterRegistryRef.set(meterRegistry);
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
            defaultHandler.set(handler);
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
        handleException(jobId, entryId, exception, phase, null);
    }
    
    /**
     * 处理异常并记录 ERRORS 指标
     *
     * @param jobId     Job ID
     * @param entryId   Entry ID（可为 null）
     * @param exception 异常
     * @param phase     阶段
     * @param errorType 错误类型，用于 ERRORS 指标的 errorType 标签；为 null 时不记录指标
     */
    public static void handleException(String jobId,
            String entryId,
            Throwable exception,
            FlowPhase phase,
            String errorType) {
        FlowExceptionContext context = new FlowExceptionContext(jobId, entryId, exception, phase, errorType);
        MeterRegistry registry = meterRegistryRef.get();
        if (registry != null && errorType != null) {
            Counter.builder(FlowMetricNames.ERRORS)
                   .tag(FlowMetricNames.TAG_ERROR_TYPE, errorType)
                   .tag(FlowMetricNames.TAG_PHASE, phase.name())
                   .register(registry)
                   .increment();
        }
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
        handlers.add(defaultHandler.get());
    }
    
    /**
     * 清除 MeterRegistry（用于测试隔离）
     */
    public static void clearMeterRegistry() {
        meterRegistryRef.set(null);
    }
}
