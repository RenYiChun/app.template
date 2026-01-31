package com.lrenyi.template.core.flow.manager;

import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.flow.FlowJoiner;
import com.lrenyi.template.core.flow.ProgressTracker;
import com.lrenyi.template.core.flow.context.FlowResourceContext;
import com.lrenyi.template.core.flow.context.Orchestrator;
import com.lrenyi.template.core.flow.context.Registration;
import com.lrenyi.template.core.flow.impl.BackpressureController;
import com.lrenyi.template.core.flow.impl.FlowFinalizer;
import com.lrenyi.template.core.flow.impl.FlowLauncher;
import com.lrenyi.template.core.flow.resource.FlowResourceRegistry;
import jakarta.annotation.PreDestroy;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
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
        log.info("FlowManager 启动");
    }
    
    private FlowManager init() {
        this.flowProgressDisplay = new FlowProgressDisplay(this);
        int second = globalConfig.getProgressDisplaySecond();
        if (second > 0) {
            this.flowProgressDisplay.start(1L, second, TimeUnit.SECONDS);
        }
        return this;
    }
    
    private static FlowManager create(TemplateConfigProperties.JobGlobal globalConfig) {
        return new FlowManager(globalConfig).init();
    }
    
    public static FlowManager getInstance(TemplateConfigProperties.JobGlobal globalConfig) {
        if (instance == null) {
            synchronized (FlowManager.class) {
                if (instance == null) {
                    instance = create(globalConfig);
                }
            }
        }
        return instance;
    }
    
    public <T> FlowLauncher<T> createLauncher(String jobId,
                                              FlowJoiner<T> flowJoiner,
                                              ProgressTracker tracker,
                                              TemplateConfigProperties.JobConfig jobConfig) {
        
        Registration registration = new Registration(jobId, jobConfig);
        registry.put(jobId, registration);
        
        // 创建Job级资源
        Semaphore jobProducerSemaphore = new Semaphore(jobConfig.getJobProducerLimit());
        
        // 创建FlowFinalizer（需要resourceContext，先创建临时finalizer获取storage）
        FlowFinalizer<T> finalizer = new FlowFinalizer<>(resourceRegistry);
        com.lrenyi.template.core.flow.manager.FlowCacheManager cacheManager = resourceRegistry.getCacheManager();
        com.lrenyi.template.core.flow.storage.FlowStorage<T> storage = cacheManager.getOrCreateStorage(jobId,
                                                                                                       flowJoiner,
                                                                                                       jobConfig,
                                                                                                       finalizer,
                                                                                                       tracker
        );
        
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
        FlowLauncher<T> launcher = FlowLauncher.create(jobId, flowJoiner, this, tracker, registration, resourceContext);
        
        activeLaunchers.put(jobId, launcher);
        return launcher;
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
    }
    
    private void stopJob(boolean force, String key, FlowLauncher<?> launcher) {
        try {
            launcher.stop(force);
            unregister(key);
        } catch (Exception e) {
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
    
    
    // ========== 向后兼容的getter方法 ==========
    
    /**
     * 获取全局并发信号量（向后兼容）
     */
    public Semaphore getGlobalSemaphore() {
        return resourceRegistry.getGlobalSemaphore();
    }
    
    /**
     * 获取全局虚拟线程池（向后兼容）
     */
    public ExecutorService getGlobalExecutor() {
        return resourceRegistry.getGlobalExecutor();
    }
    
    /**
     * 获取公平锁（向后兼容）
     */
    public java.util.concurrent.locks.Lock getFairLock() {
        return resourceRegistry.getFairLock();
    }
    
    /**
     * 获取许可释放条件变量（向后兼容）
     */
    public java.util.concurrent.locks.Condition getPermitReleased() {
        return resourceRegistry.getPermitReleased();
    }
    
    /**
     * 获取缓存管理器（向后兼容）
     */
    public FlowCacheManager getFlowCacheManager() {
        return resourceRegistry.getCacheManager();
    }

    @PreDestroy
    public void shutdownAll() {
        log.info("系统关闭：正在注销所有任务...");
        stopAll(true); // 强制清理资源
        // 资源关闭由 FlowResourceRegistry 统一管理
    }
}