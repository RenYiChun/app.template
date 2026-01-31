package com.lrenyi.template.core.flow.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认 Flow 指标收集器实现
 * 使用内存存储指标，适合单机环境
 */
@Slf4j
public class DefaultFlowMetricsCollector implements FlowMetricsCollector {
    
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final Map<String, List<Long>> latencies = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> resourceUsage = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();
    
    // 限制延迟记录的数量，避免内存泄漏
    private static final int MAX_LATENCY_RECORDS = 1000;
    
    @Override
    public void recordResourceUsage(String resourceType, long usage) {
        resourceUsage.computeIfAbsent(resourceType, k -> new AtomicLong(0)).set(usage);
    }
    
    @Override
    public void recordError(String errorType, String jobId) {
        String key = errorType + ":" + (jobId != null ? jobId : "unknown");
        errorCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    @Override
    public void recordLatency(String operation, long latencyMs) {
        List<Long> latencyList = latencies.computeIfAbsent(operation, k -> new CopyOnWriteArrayList<>());
        
        synchronized (latencyList) {
            latencyList.add(latencyMs);
            // 限制记录数量
            if (latencyList.size() > MAX_LATENCY_RECORDS) {
                latencyList.removeFirst();
            }
        }
    }
    
    @Override
    public void incrementCounter(String metricName, long increment) {
        counters.computeIfAbsent(metricName, k -> new AtomicLong(0)).addAndGet(increment);
    }
    
    @Override
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        // 计数器指标
        Map<String, Long> counterMetrics = new HashMap<>();
        counters.forEach((key, value) -> counterMetrics.put(key, value.get()));
        metrics.put("counters", counterMetrics);
        
        // 延迟指标（计算统计信息）
        Map<String, Map<String, Object>> latencyMetrics = new HashMap<>();
        latencies.forEach((operation, latencyList) -> {
            if (!latencyList.isEmpty()) {
                List<Long> sorted = new ArrayList<>(latencyList);
                Collections.sort(sorted);
                
                Map<String, Object> stats = new HashMap<>();
                stats.put("count", sorted.size());
                stats.put("min", sorted.getFirst());
                stats.put("max", sorted.getLast());
                stats.put("avg", sorted.stream().mapToLong(Long::longValue).average().orElse(0.0));
                
                // 计算中位数
                int mid = sorted.size() / 2;
                if (sorted.size() % 2 == 0) {
                    stats.put("median", (sorted.get(mid - 1) + sorted.get(mid)) / 2.0);
                } else {
                    stats.put("median", sorted.get(mid));
                }
                
                // 计算 P95 和 P99
                if (sorted.size() >= 20) {
                    int p95Index = (int) (sorted.size() * 0.95);
                    int p99Index = (int) (sorted.size() * 0.99);
                    stats.put("p95", sorted.get(p95Index));
                    stats.put("p99", sorted.get(p99Index));
                }
                
                latencyMetrics.put(operation, stats);
            }
        });
        metrics.put("latencies", latencyMetrics);
        
        // 资源使用指标
        Map<String, Long> resourceMetrics = new HashMap<>();
        resourceUsage.forEach((key, value) -> resourceMetrics.put(key, value.get()));
        metrics.put("resources", resourceMetrics);
        
        // 错误计数指标
        Map<String, Long> errorMetrics = new HashMap<>();
        errorCounts.forEach((key, value) -> errorMetrics.put(key, value.get()));
        metrics.put("errors", errorMetrics);
        
        return Collections.unmodifiableMap(metrics);
    }
    
    @Override
    public void reset() {
        counters.clear();
        latencies.clear();
        resourceUsage.clear();
        errorCounts.clear();
        log.debug("指标收集器已重置");
    }
}
