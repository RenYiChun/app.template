package com.lrenyi.template.core.flow.resource;

import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.flow.manager.FlowCacheManager;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import lombok.Getter;
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
public class FlowResourceRegistry {
    private static volatile FlowResourceRegistry instance;
    
    private final TemplateConfigProperties.JobGlobal globalConfig;
    private final Semaphore globalSemaphore;
    private final ExecutorService globalExecutor;
    private final ScheduledExecutorService storageEgressExecutor;
    private final Lock fairLock;
    private final Condition permitReleased;
    private final FlowCacheManager flowCacheManager;
    
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
            Thread t = new Thread(r, "flow-storage-egress");
            t.setDaemon(true);
            return t;
        });
        
        // 初始化公平锁机制
        this.fairLock = new ReentrantLock();
        this.permitReleased = fairLock.newCondition();
        
        // 初始化缓存管理器
        this.flowCacheManager = new FlowCacheManager(this);
        
        log.info("FlowResourceRegistry 初始化完成");
    }
    
    private static FlowResourceRegistry create(TemplateConfigProperties.JobGlobal globalConfig) {
        return new FlowResourceRegistry(globalConfig);
    }
    
    /**
     * 获取全局资源注册表实例（单例模式）
     */
    public static FlowResourceRegistry getInstance(TemplateConfigProperties.JobGlobal globalConfig) {
        if (instance == null) {
            synchronized (FlowResourceRegistry.class) {
                if (instance == null) {
                    instance = create(globalConfig);
                }
            }
        }
        return instance;
    }
    
    /**
     * 获取缓存管理器
     */
    public FlowCacheManager getCacheManager() {
        return flowCacheManager;
    }
    
    /**
     * 统一关闭所有全局资源
     */
    @PreDestroy
    public void shutdown() {
        log.info("FlowResourceRegistry 关闭：正在关闭所有全局资源...");
        
        // 关闭缓存管理器
        if (flowCacheManager != null) {
            flowCacheManager.invalidateAll();
        }
        
        // 关闭存储出口执行器
        if (storageEgressExecutor != null) {
            storageEgressExecutor.shutdown();
            try {
                if (!storageEgressExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("存储出口执行器未能在5秒内关闭，强制关闭");
                    storageEgressExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.warn("等待存储出口执行器关闭时被中断", e);
                storageEgressExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // 关闭全局虚拟线程池
        if (globalExecutor != null) {
            globalExecutor.shutdown();
            try {
                if (!globalExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("全局虚拟线程池未能在5秒内关闭，强制关闭");
                    globalExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.warn("等待全局虚拟线程池关闭时被中断", e);
                globalExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        log.info("FlowResourceRegistry 关闭完成");
    }
}
