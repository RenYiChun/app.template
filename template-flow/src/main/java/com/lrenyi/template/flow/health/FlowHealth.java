package com.lrenyi.template.flow.health;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Flow 健康检查工具类
 * 提供全局的健康检查功能
 */
public class FlowHealth {
    
    private static final List<FlowHealthIndicator> indicators = new CopyOnWriteArrayList<>();
    
    /**
     * 注册健康检查指示器
     */
    public static void registerIndicator(FlowHealthIndicator indicator) {
        if (indicator != null) {
            indicators.add(indicator);
        }
    }
    
    /**
     * 移除健康检查指示器
     */
    public static void removeIndicator(FlowHealthIndicator indicator) {
        indicators.remove(indicator);
    }
    
    /**
     * 执行所有健康检查
     *
     * @return 整体健康状态（取最差的状态）
     */
    public static HealthStatus checkHealth() {
        if (indicators.isEmpty()) {
            return HealthStatus.HEALTHY; // 没有指示器时认为健康
        }
        
        HealthStatus worstStatus = HealthStatus.HEALTHY;
        for (FlowHealthIndicator indicator : indicators) {
            try {
                HealthStatus status = indicator.checkHealth();
                if (status.ordinal() > worstStatus.ordinal()) {
                    worstStatus = status;
                }
            } catch (Exception e) {
                // 健康检查失败视为不健康
                return HealthStatus.UNHEALTHY;
            }
        }
        
        return worstStatus;
    }
    
    /**
     * 获取所有健康检查详情
     *
     * @return 详情映射
     */
    public static Map<String, Object> getHealthDetails() {
        Map<String, Object> allDetails = new java.util.HashMap<>();
        List<Map<String, Object>> indicatorDetails = new ArrayList<>();
        
        for (FlowHealthIndicator indicator : indicators) {
            try {
                Map<String, Object> details = new java.util.HashMap<>();
                details.put("name", indicator.getName());
                details.put("status", indicator.checkHealth().name());
                details.put("details", indicator.getDetails());
                indicatorDetails.add(details);
            } catch (Exception e) {
                Map<String, Object> errorDetails = new java.util.HashMap<>();
                errorDetails.put("name", indicator.getName());
                errorDetails.put("status", HealthStatus.UNHEALTHY.name());
                errorDetails.put("error", e.getMessage());
                indicatorDetails.add(errorDetails);
            }
        }
        
        allDetails.put("overallStatus", checkHealth().name());
        allDetails.put("indicators", indicatorDetails);
        
        return Collections.unmodifiableMap(allDetails);
    }
    
    /**
     * 清除所有指示器（用于测试）
     */
    public static void clearIndicators() {
        indicators.clear();
    }
}
