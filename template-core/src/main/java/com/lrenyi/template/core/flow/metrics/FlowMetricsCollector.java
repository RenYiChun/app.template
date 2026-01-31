package com.lrenyi.template.core.flow.metrics;

import java.util.Map;

/**
 * Flow 指标收集器接口
 * 用于收集框架运行时的各种指标
 */
public interface FlowMetricsCollector {
    
    /**
     * 记录资源使用情况
     *
     * @param resourceType 资源类型（如 "semaphore", "executor", "cache"）
     * @param usage        使用量
     */
    void recordResourceUsage(String resourceType, long usage);
    
    /**
     * 记录错误
     *
     * @param errorType 错误类型（如 "timeout", "eviction", "rejection"）
     * @param jobId     Job ID
     */
    void recordError(String errorType, String jobId);
    
    /**
     * 记录延迟
     *
     * @param operation 操作名称（如 "deposit", "consume", "match"）
     * @param latencyMs 延迟（毫秒）
     */
    void recordLatency(String operation, long latencyMs);
    
    /**
     * 记录计数器增量
     *
     * @param metricName 指标名称
     * @param increment  增量值
     */
    default void incrementCounter(String metricName, long increment) {
        // 默认实现为空，子类可以覆盖
    }
    
    /**
     * 记录计数器增量（增量为1）
     */
    default void incrementCounter(String metricName) {
        incrementCounter(metricName, 1);
    }
    
    /**
     * 获取所有指标
     *
     * @return 指标映射
     */
    Map<String, Object> getMetrics();
    
    /**
     * 获取指定指标的值
     *
     * @param metricName 指标名称
     *
     * @return 指标值，如果不存在则返回 null
     */
    default Object getMetric(String metricName) {
        return getMetrics().get(metricName);
    }
    
    /**
     * 重置所有指标
     */
    default void reset() {
        // 默认实现为空，子类可以覆盖
    }
}
