package com.lrenyi.template.flow.health;

import java.util.Map;

/**
 * Flow 健康检查指示器接口
 * 用于检查框架的健康状态
 */
public interface FlowHealthIndicator {
    
    /**
     * 检查健康状态
     * 
     * @return 健康状态
     */
    HealthStatus checkHealth();
    
    /**
     * 获取健康检查详情
     * 
     * @return 详情映射，包含各种健康指标
     */
    Map<String, Object> getDetails();
    
    /**
     * 获取健康检查名称
     * 
     * @return 名称
     */
    default String getName() {
        return getClass().getSimpleName();
    }
}
