package com.lrenyi.template.flow.health;

/**
 * 健康状态枚举
 */
public enum HealthStatus {
    /**
     * 健康：所有资源正常
     */
    HEALTHY,
    
    /**
     * 降级：部分功能受影响，但核心功能正常
     */
    DEGRADED,
    
    /**
     * 不健康：核心功能异常
     */
    UNHEALTHY
}
