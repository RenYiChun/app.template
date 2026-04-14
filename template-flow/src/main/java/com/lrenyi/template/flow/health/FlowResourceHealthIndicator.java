package com.lrenyi.template.flow.health;

import java.util.HashMap;
import java.util.Map;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Flow 资源健康检查指示器
 * 检查资源使用率、泄漏、错误率等
 */
@Slf4j
public class FlowResourceHealthIndicator implements FlowHealthIndicator {
    
    // 健康阈值配置
    private static final double SEMAPHORE_USAGE_WARNING_THRESHOLD = 0.8; // 80%
    private static final double SEMAPHORE_USAGE_CRITICAL_THRESHOLD = 0.95; // 95%
    private static final int ACTIVE_JOB_WARNING_THRESHOLD = 100;
    private static final int ACTIVE_JOB_CRITICAL_THRESHOLD = 200;
    private final FlowResourceRegistry resourceRegistry;
    private final FlowManager flowManager;
    
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
        Double semaphoreUsage = (Double) details.get("consumerThreadsUsage");
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
        
        // 全局消费许可使用情况（global<=0 时视为未启用全局保护）
        int globalLimit = resourceRegistry.getFlowConfig().getLimits().getGlobal().getConsumerThreads();
        int effectiveLimit = globalLimit > 0 ? globalLimit : 0;
        int available = resourceRegistry.getGlobalSemaphore() != null
                ? resourceRegistry.getGlobalSemaphore().availablePermits()
                : 0;
        int used = effectiveLimit > 0 ? Math.max(0, effectiveLimit - available) : 0;
        double usage = effectiveLimit > 0 ? (double) used / effectiveLimit : 0.0;
        details.put("consumerThreadsLimit", effectiveLimit);
        details.put("consumerThreadsAvailable", available);
        details.put("consumerThreadsUsed", used);
        details.put("consumerThreadsUsage", usage);
        
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
        details.put("flowConsumerExecutorShutdown", resourceRegistry.getFlowConsumerExecutor().isShutdown());
        details.put("storageEgressExecutorShutdown", resourceRegistry.getStorageEgressExecutor().isShutdown());
        
        return details;
    }
    
    @Override
    public String getName() {
        return "FlowResourceHealth";
    }
}
