package com.lrenyi.template.core.flow.resource;

import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.flow.FlowConstants;
import com.lrenyi.template.core.flow.manager.FlowCacheManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * 全局资源注册表
 * 职责：统一管理所有全局资源，提供资源获取接口
 * 管理的全局资源包括：
 * - 全局并发信号量（globalSemaphore）
 * - 全局虚拟线程池（globalExecutor）
 * - 存储出口执行器（storageEgressExecutor）
 * - 公平锁机制（fairLock、permitReleased）
 * - 缓存管理器（flowCacheManager）
 */
@Slf4j
@Getter
public class FlowResourceRegistry implements ResourceLifecycle {
    private static volatile FlowResourceRegistry instance;
    private static volatile TemplateConfigProperties.JobGlobal lastConfig;
    
    private final TemplateConfigProperties.JobGlobal globalConfig;
    private final Semaphore globalSemaphore;
    private final ExecutorService globalExecutor;
    private final ScheduledExecutorService storageEgressExecutor;
    private final Lock fairLock;
    private final Condition permitReleased;
    private final FlowCacheManager flowCacheManager;
    /**
     * -- SETTER --
     * 设置 FlowManager 引用（由 FlowManager 在初始化时调用）
     * 用于解决存储层需要访问 FlowManager 的循环依赖问题
     */
    // FlowManager 引用（由 FlowManager 在初始化时设置，避免循环依赖）
    @Setter
    private volatile com.lrenyi.template.core.flow.manager.FlowManager flowManager;
    
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    
    // JVM 关闭钩子
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (instance != null && instance.isInitialized()) {
                try {
                    instance.shutdown();
                } catch (Exception e) {
                    log.error("FlowResourceRegistry shutdown hook failed.", e);
                }
            }
        }, FlowConstants.THREAD_NAME_SHUTDOWN_HOOK));
    }
    
    /**
     * 私有构造函数，通过 getInstance() 或测试构造函数创建实例
     */
    private FlowResourceRegistry(TemplateConfigProperties.JobGlobal globalConfig) {
        this.globalConfig = globalConfig;
        
        // 初始化全局并发信号量
        int globalSemaphoreMaxLimit = globalConfig.getGlobalSemaphoreMaxLimit();
        this.globalSemaphore = new Semaphore(globalSemaphoreMaxLimit, true);
        log.info("FlowResourceRegistry 启动：初始物理并发池大小为 {}", globalSemaphoreMaxLimit);
        
        // 初始化全局虚拟线程池
        this.globalExecutor = Executors.newVirtualThreadPerTaskExecutor();
        
        // 初始化存储出口执行器（单物理线程）
        this.storageEgressExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, FlowConstants.THREAD_NAME_STORAGE_EGRESS);
            t.setDaemon(true);
            return t;
        });
        
        // 初始化公平锁机制
        this.fairLock = new ReentrantLock();
        this.permitReleased = fairLock.newCondition();
        
        // 初始化缓存管理器
        this.flowCacheManager = new FlowCacheManager(this);
        
        this.initialized.set(true);
        log.info("FlowResourceRegistry 初始化完成");
    }
    
    @Override
    public void initialize() throws ResourceInitializationException {
        // 资源已在构造函数中初始化
        if (!initialized.get()) {
            throw new ResourceInitializationException("FlowResourceRegistry initialization failed");
        }
    }
    
    @Override
    public boolean isInitialized() {
        return initialized.get();
    }
    
    @Override
    public boolean isShutdown() {
        return shutdown.get();
    }
    
    @Override
    public String getResourceName() {
        return "FlowResourceRegistry";
    }
    
    private static FlowResourceRegistry create(TemplateConfigProperties.JobGlobal globalConfig) {
        return new FlowResourceRegistry(globalConfig);
    }
    
    /**
     * 获取全局资源注册表实例（单例模式）
     * 如果配置发生变化，会关闭旧实例并创建新实例
     *
     * @param globalConfig 全局配置
     * @return FlowResourceRegistry 实例
     */
    public static FlowResourceRegistry getInstance(TemplateConfigProperties.JobGlobal globalConfig) {
        if (instance == null || !configEquals(lastConfig, globalConfig)) {
            synchronized (FlowResourceRegistry.class) {
                if (instance == null || !configEquals(lastConfig, globalConfig)) {
                    if (instance != null) {
                        log.info("检测到配置变更，关闭旧实例并创建新实例");
                        try {
                            instance.shutdown();
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
        return config1.getGlobalSemaphoreMaxLimit() == config2.getGlobalSemaphoreMaxLimit() && config1.getProgressDisplaySecond() == config2.getProgressDisplaySecond();
    }
    
    /**
     * 重置单例实例（用于测试）
     * 注意：此方法仅用于测试，生产环境不应调用
     */
    public static void reset() {
        synchronized (FlowResourceRegistry.class) {
            if (instance != null) {
                try {
                    instance.shutdown();
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
    FlowResourceRegistry(TemplateConfigProperties.JobGlobal globalConfig, boolean unused) {
        this(globalConfig);
    }
    
    /**
     * 获取缓存管理器
     */
    public FlowCacheManager getCacheManager() {
        return flowCacheManager;
    }
    
    /**
     * 统一关闭所有全局资源
     *
     * <p>按照依赖顺序关闭：先关闭依赖其他资源的资源，最后关闭基础资源。</p>
     *
     * <p>关闭顺序：</p>
     * <ol>
     *   <li>缓存管理器（依赖执行器）</li>
     *   <li>存储出口执行器</li>
     *   <li>全局虚拟线程池（最后关闭）</li>
     * </ol>
     *
     * <p>如果关闭过程中出现异常，会记录错误但继续关闭其他资源，最后抛出 ResourceShutdownException。</p>
     *
     * @throws ResourceShutdownException 如果部分资源关闭失败
     */
    @Override
    public void shutdown() throws ResourceShutdownException {
        if (shutdown.get()) {
            log.debug("FlowResourceRegistry 已经关闭，跳过重复关闭");
            return;
        }
        
        log.info("FlowResourceRegistry 关闭：正在关闭所有全局资源...");
        java.util.List<Exception> errors = new java.util.ArrayList<>();
        
        // 1. 先关闭缓存管理器（依赖执行器）
        if (flowCacheManager != null) {
            try {
                flowCacheManager.invalidateAll();
            } catch (Exception e) {
                errors.add(e);
                log.error("关闭缓存管理器失败", e);
            }
        }
        
        // 2. 关闭存储出口执行器
        if (storageEgressExecutor != null && !storageEgressExecutor.isShutdown()) {
            try {
                shutdownExecutor("存储出口执行器", storageEgressExecutor);
            } catch (Exception e) {
                errors.add(e);
                log.error("关闭存储出口执行器失败", e);
            }
        }
        
        // 3. 关闭全局虚拟线程池（最后关闭）
        if (globalExecutor != null && !globalExecutor.isShutdown()) {
            try {
                shutdownExecutor("全局虚拟线程池", globalExecutor);
            } catch (Exception e) {
                errors.add(e);
                log.error("关闭全局虚拟线程池失败", e);
            }
        }
        
        shutdown.set(true);
        
        if (!errors.isEmpty()) {
            ResourceShutdownException exception = new ResourceShutdownException("部分资源关闭失败", errors);
            log.error("FlowResourceRegistry 关闭完成，但有 {} 个错误", errors.size());
            throw exception;
        }
        
        log.info("FlowResourceRegistry 关闭完成");
    }
    
    /**
     * 关闭执行器，带超时保护
     *
     * <p>关闭流程：</p>
     * <ol>
     *   <li>调用 shutdown() 优雅关闭</li>
     *   <li>等待指定超时时间</li>
     *   <li>如果超时，调用 shutdownNow() 强制关闭</li>
     *   <li>再等待一段时间确保完全关闭</li>
     * </ol>
     *
     * @param name     执行器名称（用于日志）
     * @param executor 要关闭的执行器
     */
    private void shutdownExecutor(String name, ExecutorService executor) throws ResourceShutdownException {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(FlowConstants.DEFAULT_SHUTDOWN_TIMEOUT_SECONDS,
                                           FlowConstants.DEFAULT_SHUTDOWN_TIMEOUT_UNIT
            )) {
                log.warn("{} 未能在{}秒内关闭，强制关闭", name, FlowConstants.DEFAULT_SHUTDOWN_TIMEOUT_SECONDS);
                executor.shutdownNow();
                // 再等待一段时间
                if (!executor.awaitTermination(FlowConstants.FORCE_SHUTDOWN_WAIT_SECONDS,
                                               FlowConstants.DEFAULT_SHUTDOWN_TIMEOUT_UNIT
                )) {
                    log.error("{} 强制关闭后仍未完全关闭", name);
                }
            }
        } catch (InterruptedException e) {
            log.warn("等待 {} 关闭时被中断", name, e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            throw new ResourceShutdownException("关闭 " + name + " 时被中断", e);
        }
    }
}
