package com.lrenyi.template.core.flow.manager;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.flow.FlowJoiner;
import com.lrenyi.template.core.flow.ProgressTracker;
import com.lrenyi.template.core.flow.context.FlowResourceContext;
import com.lrenyi.template.core.flow.context.Orchestrator;
import com.lrenyi.template.core.flow.context.Registration;
import com.lrenyi.template.core.flow.exception.FlowExceptionHelper;
import com.lrenyi.template.core.flow.exception.FlowPhase;
import com.lrenyi.template.core.flow.health.FlowHealth;
import com.lrenyi.template.core.flow.health.FlowResourceHealthIndicator;
import com.lrenyi.template.core.flow.health.HealthStatus;
import com.lrenyi.template.core.flow.impl.BackpressureController;
import com.lrenyi.template.core.flow.impl.FlowFinalizer;
import com.lrenyi.template.core.flow.impl.FlowLauncher;
import com.lrenyi.template.core.flow.metrics.FlowMetrics;
import com.lrenyi.template.core.flow.resource.FlowResourceRegistry;
import com.lrenyi.template.core.flow.storage.FlowStorage;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Job生命周期管理器
 * 职责：专注于Job生命周期管理，不再直接管理资源
 * 管理的Job生命周期：
 * - Job注册与注销
 * - Launcher实例管理
 * - Job停止与清理
 * 资源管理已委托给 FlowResourceRegistry
 */
@Slf4j
@Getter
@Setter
public class FlowManager {
    private static volatile FlowManager instance;
    private static volatile TemplateConfigProperties.JobGlobal lastConfig;
    private final TemplateConfigProperties.JobGlobal globalConfig;
    private final FlowResourceRegistry resourceRegistry;
    
    // 维护 Job 注册信息，用于公平限流算法
    private final Map<String, Registration> registry = new ConcurrentHashMap<>();
    // 维护所有活跃的 Launcher 实例，用于监控和批量控制
    private final Map<String, FlowLauncher<?>> activeLaunchers = new ConcurrentHashMap<>();
    
    private FlowProgressDisplay flowProgressDisplay;
    private boolean printProgressDisplay = true;

    private FlowManager(TemplateConfigProperties.JobGlobal globalConfig) {
        this.globalConfig = globalConfig;
        // 获取或创建全局资源注册表
        this.resourceRegistry = FlowResourceRegistry.getInstance(globalConfig);
        // 设置 FlowManager 引用到 ResourceRegistry，用于解决循环依赖
        this.resourceRegistry.setFlowManager(this);
        log.info("FlowManager 启动");
    }
    
    private FlowManager init() {
        this.flowProgressDisplay = new FlowProgressDisplay(this);
        int second = globalConfig.getProgressDisplaySecond();
        if (second > 0) {
            this.flowProgressDisplay.start(1L, second, TimeUnit.SECONDS);
        }
        
        // 注册健康检查指示器
        FlowResourceHealthIndicator healthIndicator = new FlowResourceHealthIndicator(resourceRegistry, this);
        FlowHealth.registerIndicator(healthIndicator);
        
        return this;
    }
    
    private static FlowManager create(TemplateConfigProperties.JobGlobal globalConfig) {
        return new FlowManager(globalConfig).init();
    }
    
    /**
     * 获取 FlowManager 实例（单例模式）
     * 如果配置发生变化，会关闭旧实例并创建新实例
     *
     * @param globalConfig 全局配置
     *
     * @return FlowManager 实例
     */
    public static FlowManager getInstance(TemplateConfigProperties.JobGlobal globalConfig) {
        if (instance == null || !configEquals(lastConfig, globalConfig)) {
            synchronized (FlowManager.class) {
                if (instance == null || !configEquals(lastConfig, globalConfig)) {
                    if (instance != null) {
                        log.info("检测到配置变更，关闭旧实例并创建新实例");
                        try {
                            instance.shutdownAll();
                        } catch (Exception e) {
                            log.error("关闭旧实例失败", e);
                        }
                    }
                    instance = create(globalConfig);
                    lastConfig = globalConfig;
                }
            }
        }
        return instance;
    }
    
    /**
     * 比较两个配置是否相等
     * 用于检测配置变更
     */
    private static boolean configEquals(TemplateConfigProperties.JobGlobal config1,
                                        TemplateConfigProperties.JobGlobal config2) {
        if (config1 == config2) {
            return true;
        }
        if (config1 == null || config2 == null) {
            return false;
        }
        return config1.getGlobalSemaphoreMaxLimit() == config2.getGlobalSemaphoreMaxLimit()
                && config1.getProgressDisplaySecond() == config2.getProgressDisplaySecond();
    }
    
    /**
     * 重置单例实例（用于测试）
     * 注意：此方法仅用于测试，生产环境不应调用
     */
    public static void reset() {
        synchronized (FlowManager.class) {
            if (instance != null) {
                try {
                    instance.shutdownAll();
                } catch (Exception e) {
                    log.warn("重置时关闭实例失败", e);
                }
            }
            instance = null;
            lastConfig = null;
        }
    }
    
    /**
     * 包可见的构造函数，用于测试
     * 测试代码可以直接创建实例而不使用单例
     */
    FlowManager(TemplateConfigProperties.JobGlobal globalConfig, boolean unused) {
        this(globalConfig);
    }
    
    public <T> FlowLauncher<T> createLauncher(String jobId,
                                              FlowJoiner<T> flowJoiner,
                                              ProgressTracker tracker,
                                              TemplateConfigProperties.JobConfig jobConfig) {
        try {
            Registration registration = new Registration(jobId, jobConfig);
            registry.put(jobId, registration);
            
            // 创建Job级资源
            Semaphore jobProducerSemaphore = new Semaphore(jobConfig.getJobProducerLimit());
            
            // 创建FlowFinalizer（需要resourceContext，先创建临时finalizer获取storage）
            FlowFinalizer<T> finalizer = new FlowFinalizer<>(resourceRegistry);
            FlowCacheManager cacheManager = resourceRegistry.getCacheManager();
            FlowStorage<T> storage = cacheManager.getOrCreateStorage(jobId, flowJoiner, jobConfig, finalizer, tracker);
            
            BackpressureController backpressureController = new BackpressureController(storage);
            
            // 创建资源上下文
            FlowResourceContext resourceContext = FlowResourceContext.builder()
                                                                     .resourceRegistry(resourceRegistry)
                                                                     .flowManager(this)
                                                                     .jobProducerSemaphore(jobProducerSemaphore)
                                                                     .storage(storage)
                                                                     .backpressureController(backpressureController)
                                                                     .build();
            
            // 创建Launcher，传递resourceContext
            FlowLauncher<T> launcher = FlowLauncher.create(jobId,
                                                           flowJoiner,
                                                           this,
                                                           tracker,
                                                           registration,
                                                           resourceContext
            );
            
            activeLaunchers.put(jobId, launcher);
            
            // 记录指标
            FlowMetrics.incrementCounter("launcher_created");
            FlowMetrics.recordResourceUsage("active_launchers", activeLaunchers.size());
            
            return launcher;
        } catch (Exception e) {
            FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.PRODUCTION);
            FlowMetrics.recordError("create_launcher_failed", jobId);
            throw e;
        }
    }
    
    public boolean isStopped(String jobId) {
        return !activeLaunchers.containsKey(jobId);
    }
    
    /**
     * 注销 Job
     */
    public void unregister(String jobId) {
        registry.remove(jobId);
        activeLaunchers.remove(jobId);
        log.info("Job [{}] 已从管理器中注销", jobId);
    }
    
    /**
     * 停止所有运行中的 Job
     *
     * @param force 是否强制清理缓存
     */
    public void stopAll(boolean force) {
        log.info("正在停止所有运行中的任务，force={}", force);
        activeLaunchers.forEach((key, launcher) -> stopJob(force, key, launcher));
        flowProgressDisplay.stop();
        FlowMetrics.incrementCounter("jobs_stopped_all");
    }
    
    private void stopJob(boolean force, String key, FlowLauncher<?> launcher) {
        try {
            launcher.stop(force);
            unregister(key);
            FlowMetrics.incrementCounter("job_stopped");
        } catch (Exception e) {
            FlowExceptionHelper.handleException(launcher.getJobId(), null, e, FlowPhase.FINALIZATION);
            FlowMetrics.recordError("stop_job_failed", launcher.getJobId());
            log.error("停止 Job [{}] 时发生异常", launcher.getJobId(), e);
        }
    }
    
    public void stopById(String jobId, boolean force) {
        for (Map.Entry<String, FlowLauncher<?>> entry : activeLaunchers.entrySet()) {
            String key = entry.getKey();
            FlowLauncher<?> launcher = entry.getValue();
            if (launcher.getJobId().equals(jobId)) {
                stopJob(force, key, launcher);
                break;
            }
        }
    }
    
    public Map<String, FlowLauncher<?>> getActiveLaunchers() {
        return Collections.unmodifiableMap(activeLaunchers);
    }
    
    @SuppressWarnings("unchecked")
    public <T> FlowLauncher<T> getActiveLauncher(String jobId) {
        return (FlowLauncher<T>) activeLaunchers.get(jobId);
    }
    
    public ProgressTracker getProgressTracker(String jobId) {
        FlowLauncher<Object> activeLauncher = getActiveLauncher(jobId);
        Orchestrator taskOrchestrator = activeLauncher.getTaskOrchestrator();
        return taskOrchestrator.tracker();
    }
    
    public int getActiveJobCount() {
        return Math.max(1, registry.size());
    }
    
    /**
     * 获取框架健康状态
     * 
     * @return 健康状态详情
     */
    public Map<String, Object> getHealthStatus() {
        return FlowHealth.getHealthDetails();
    }
    
    /**
     * 检查框架健康状态
     * 
     * @return 健康状态枚举
     */
    public HealthStatus checkHealth() {
        return FlowHealth.checkHealth();
    }
    
    /**
     * 输出健康检查报告到日志
     * 包含所有健康指标的详细信息
     */
    public void logHealthReport() {
        HealthStatus status = FlowHealth.checkHealth();
        Map<String, Object> healthDetails = FlowHealth.getHealthDetails();
        
        StringBuilder report = new StringBuilder("\n");
        report.append("=".repeat(80)).append("\n");
        report.append("Flow Framework Health Report\n");
        report.append("=".repeat(80)).append("\n");
        report.append(String.format("Overall Status: %s%n", status.name()));
        report.append("-".repeat(80)).append("\n");
        
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> indicators = 
            (java.util.List<Map<String, Object>>) healthDetails.get("indicators");
        
        if (indicators != null && !indicators.isEmpty()) {
            for (Map<String, Object> indicator : indicators) {
                String name = (String) indicator.get("name");
                String indicatorStatus = (String) indicator.get("status");
                report.append(String.format("\n[%s] Status: %s%n", name, indicatorStatus));
                
                @SuppressWarnings("unchecked")
                Map<String, Object> details = (Map<String, Object>) indicator.get("details");
                if (details != null) {
                    details.forEach((key, value) -> {
                        if (value != null) {
                            report.append(String.format("  - %s: %s%n", key, value));
                        }
                    });
                }
            }
        }
        
        report.append("=".repeat(80)).append("\n");
        
        if (status == HealthStatus.UNHEALTHY) {
            log.error(report.toString());
        } else if (status == HealthStatus.DEGRADED) {
            log.warn(report.toString());
        } else {
            log.info(report.toString());
        }
    }
    
    /**
     * 获取框架指标
     *
     * @return 指标映射
     */
    public Map<String, Object> getMetrics() {
        return FlowMetrics.getMetrics();
    }
    
    /**
     * 关闭所有资源
     * 先停止所有 Job，然后关闭资源注册表
     */
    public void shutdownAll() {
        log.info("系统关闭：正在注销所有任务...");
        try {
            stopAll(true); // 强制清理资源
        } catch (Exception e) {
            log.error("停止所有任务时发生异常", e);
        }
        // 资源关闭由 FlowResourceRegistry 统一管理
        // 注意：FlowResourceRegistry 的关闭由 JVM 关闭钩子自动处理
    }
}