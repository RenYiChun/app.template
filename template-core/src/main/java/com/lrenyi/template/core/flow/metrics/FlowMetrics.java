package com.lrenyi.template.core.flow.metrics;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Flow 指标工具类
 * 提供全局的指标收集器访问
 */
public class FlowMetrics {
    
    private static final AtomicReference<FlowMetricsCollector> collectorRef = 
        new AtomicReference<>(new DefaultFlowMetricsCollector());
    
    /**
     * 获取当前指标收集器
     */
    public static FlowMetricsCollector getCollector() {
        return collectorRef.get();
    }
    
    /**
     * 设置指标收集器
     */
    public static void setCollector(FlowMetricsCollector collector) {
        if (collector != null) {
            collectorRef.set(collector);
        }
    }
    
    /**
     * 记录资源使用情况
     */
    public static void recordResourceUsage(String resourceType, long usage) {
        getCollector().recordResourceUsage(resourceType, usage);
    }
    
    /**
     * 记录错误
     */
    public static void recordError(String errorType, String jobId) {
        getCollector().recordError(errorType, jobId);
    }
    
    /**
     * 记录延迟
     */
    public static void recordLatency(String operation, long latencyMs) {
        getCollector().recordLatency(operation, latencyMs);
    }
    
    /**
     * 增加计数器
     */
    public static void incrementCounter(String metricName) {
        getCollector().incrementCounter(metricName);
    }
    
    /**
     * 增加计数器（指定增量）
     */
    public static void incrementCounter(String metricName, long increment) {
        getCollector().incrementCounter(metricName, increment);
    }
    
    /**
     * 获取所有指标
     */
    public static Map<String, Object> getMetrics() {
        return getCollector().getMetrics();
    }
}
