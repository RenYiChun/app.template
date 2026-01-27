package com.lrenyi.template.core.flow.manager;

import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.flow.FlowJoiner;
import com.lrenyi.template.core.flow.ProgressTracker;
import com.lrenyi.template.core.flow.context.Orchestrator;
import com.lrenyi.template.core.flow.context.Registration;
import com.lrenyi.template.core.flow.impl.FlowFinalizer;
import com.lrenyi.template.core.flow.impl.FlowLauncher;
import jakarta.annotation.PreDestroy;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * 全局流量管理器 (动态配置版)
 * 职责：管控全局并发红线，维护全局虚拟线程池，托管所有 Launcher 实例并支持生命周期控制
 */
@Slf4j
@Getter
@Setter
public class FlowManager {
    private static volatile FlowManager instance;
    private final TemplateConfigProperties.JobGlobal globalConfig;
    private FlowCacheManager flowCacheManager;
    private final Semaphore globalSemaphore;
    private final ExecutorService globalExecutor = Executors.newVirtualThreadPerTaskExecutor();
    // 维护 Job 注册信息，用于公平限流算法
    private final Map<String, Registration> registry = new ConcurrentHashMap<>();
    // 维护所有活跃的 Launcher 实例，用于监控和批量控制
    private final Map<String, FlowLauncher<?>> activeLaunchers = new ConcurrentHashMap<>();
    private final Lock fairLock = new ReentrantLock();
    private final Condition permitReleased = fairLock.newCondition();
    private FlowProgressDisplay flowProgressDisplay;
    private boolean printProgressDisplay = true;
    private FlowFinalizer<?> finalizer;
    
    private FlowManager(TemplateConfigProperties.JobGlobal globalConfig) {
        this.globalConfig = globalConfig;
        int globalSemaphoreMaxLimit = globalConfig.getGlobalSemaphoreMaxLimit();
        this.globalSemaphore = new Semaphore(globalSemaphoreMaxLimit, true);
        log.info("FlowManager 启动：初始物理并发池大小为 {}", globalSemaphoreMaxLimit);
    }
    
    private FlowManager init() {
        this.flowCacheManager = new FlowCacheManager();
        this.flowProgressDisplay = new FlowProgressDisplay(this);
        this.finalizer = new FlowFinalizer<>(this);
        int second = globalConfig.getProgressDisplaySecond();
        if (second > 0) {
            this.flowProgressDisplay.start(1L, second, TimeUnit.SECONDS);
        }
        return this;
    }
    
    public static FlowManager create(TemplateConfigProperties.JobGlobal globalConfig) {
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
        
        // 这里的构造函数也需要同步修改
        FlowLauncher<T> launcher = FlowLauncher.create(jobId, flowJoiner, this, tracker, registration);
        
        activeLaunchers.put(jobId, launcher);
        return launcher;
    }
    
    public boolean isRunning(String jobId) {
        return activeLaunchers.containsKey(jobId);
    }
    
    public void stop(String jobId) {
        activeLaunchers.remove(jobId);
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
        Orchestrator<Object> taskOrchestrator = activeLauncher.getTaskOrchestrator();
        return taskOrchestrator.getTracker();
    }
    
    public int getActiveJobCount() {
        return Math.max(1, registry.size());
    }
    
    @PreDestroy
    public void shutdownAll() {
        log.info("系统关闭：正在注销所有任务并关闭全局虚拟线程池...");
        stopAll(true); // 强制清理资源
        flowCacheManager.invalidateAll();
        globalExecutor.shutdown();
    }
}