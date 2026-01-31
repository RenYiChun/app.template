package com.lrenyi.template.core.flow.health;

import com.lrenyi.template.core.flow.manager.FlowManager;
import com.lrenyi.template.core.flow.resource.FlowResourceRegistry;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Flow 资源健康检查指示器
 * 检查资源使用率、泄漏、错误率等
 */
@Slf4j
public class FlowResourceHealthIndicator implements FlowHealthIndicator {
    
    private final FlowResourceRegistry resourceRegistry;
    private final FlowManager flowManager;
    
    // 健康阈值配置
    private static final double SEMAPHORE_USAGE_WARNING_THRESHOLD = 0.8; // 80%
    private static final double SEMAPHORE_USAGE_CRITICAL_THRESHOLD = 0.95; // 95%
    private static final int ACTIVE_JOB_WARNING_THRESHOLD = 100;
    private static final int ACTIVE_JOB_CRITICAL_THRESHOLD = 200;
    
    public FlowResourceHealthIndicator(FlowResourceRegistry resourceRegistry, FlowManager flowManager) {
        this.resourceRegistry = resourceRegistry;
        this.flowManager = flowManager;
    }
    
    @Override
    public HealthStatus checkHealth() {
        Map<String, Object> details = getDetails();
        
        // 检查资源注册表状态
        if (!resourceRegistry.isInitialized() || resourceRegistry.isShutdown()) {
            return HealthStatus.UNHEALTHY;
        }
        
        // 检查信号量使用率
        Double semaphoreUsage = (Double) details.get("semaphoreUsage");
        if (semaphoreUsage != null) {
            if (semaphoreUsage >= SEMAPHORE_USAGE_CRITICAL_THRESHOLD) {
                return HealthStatus.UNHEALTHY;
            }
            if (semaphoreUsage >= SEMAPHORE_USAGE_WARNING_THRESHOLD) {
                return HealthStatus.DEGRADED;
            }
        }
        
        // 检查活跃 Job 数量
        Integer activeJobs = (Integer) details.get("activeJobs");
        if (activeJobs != null) {
            if (activeJobs >= ACTIVE_JOB_CRITICAL_THRESHOLD) {
                return HealthStatus.DEGRADED;
            }
            if (activeJobs >= ACTIVE_JOB_WARNING_THRESHOLD) {
                return HealthStatus.DEGRADED;
            }
        }
        
        // 检查是否有资源泄漏迹象
        Boolean resourceLeakDetected = (Boolean) details.get("resourceLeakDetected");
        if (Boolean.TRUE.equals(resourceLeakDetected)) {
            return HealthStatus.DEGRADED;
        }
        
        return HealthStatus.HEALTHY;
    }
    
    @Override
    public Map<String, Object> getDetails() {
        Map<String, Object> details = new HashMap<>();
        
        // 资源注册表状态
        details.put("resourceRegistryInitialized", resourceRegistry.isInitialized());
        details.put("resourceRegistryShutdown", resourceRegistry.isShutdown());
        
        // 信号量使用情况
        int maxLimit = resourceRegistry.getGlobalConfig().getGlobalSemaphoreMaxLimit();
        int available = resourceRegistry.getGlobalSemaphore().availablePermits();
        int used = maxLimit - available;
        double usage = maxLimit > 0 ? (double) used / maxLimit : 0.0;
        details.put("semaphoreMaxLimit", maxLimit);
        details.put("semaphoreAvailable", available);
        details.put("semaphoreUsed", used);
        details.put("semaphoreUsage", usage);
        
        // 活跃 Job 数量
        int activeJobs = flowManager.getActiveJobCount();
        details.put("activeJobs", activeJobs);
        
        // 活跃 Launcher 数量
        int activeLaunchers = flowManager.getActiveLaunchers().size();
        details.put("activeLaunchers", activeLaunchers);
        
        // 检查资源泄漏（简单检查：如果注册的 Job 数和 Launcher 数不一致，可能有泄漏）
        boolean resourceLeakDetected = activeJobs != activeLaunchers && activeLaunchers > 0;
        details.put("resourceLeakDetected", resourceLeakDetected);
        
        // 执行器状态
        details.put("globalExecutorShutdown", resourceRegistry.getGlobalExecutor().isShutdown());
        details.put("storageEgressExecutorShutdown", 
            resourceRegistry.getStorageEgressExecutor().isShutdown());
        
        return details;
    }
    
    @Override
    public String getName() {
        return "FlowResourceHealth";
    }
}
