package com.lrenyi.template.core.flow.resource;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.flow.context.Orchestrator;
import com.lrenyi.template.core.flow.executor.BoundedVirtualExecutor;
import com.lrenyi.template.core.flow.executor.DefaultFlowExecutorProvider;
import com.lrenyi.template.core.flow.executor.FlowExecutorProvider;
import com.lrenyi.template.core.flow.manager.FlowCacheManager;
import com.lrenyi.template.core.flow.metrics.FlowMetrics;
import com.lrenyi.template.core.flow.model.FlowConstants;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * 全局资源注册表
 * 职责：统一管理所有全局资源，提供资源获取接口
 * 管理的全局资源包括：
 * - 全局并发信号量（globalSemaphore）
 * - 流消费执行器（flowConsumerExecutor）
 * - 存储出口执行器（storageEgressExecutor）：仅用于 QueueFlowStorage 的定时 drain 等调度任务
 * - 缓存移除执行器（cacheRemovalExecutor）：专供 Caffeine 驱逐回调，避免高驱逐率时任务堆积导致 OOM
 * - 公平锁机制（fairLock、permitReleased）
 * - 缓存管理器（flowCacheManager）
 */
@Slf4j
@Getter
public class FlowResourceRegistry implements ResourceLifecycle {
    private static volatile FlowResourceRegistry instance;
    private static int lastConcurrencyLimit = -1;
    private static int lastProgressDisplaySecond = -1;

    private final TemplateConfigProperties.Flow flowConfig;
    private final Semaphore globalSemaphore;
    private final FlowExecutorProvider executorProvider;
    private final BoundedVirtualExecutor flowConsumerExecutor;
    private final ScheduledExecutorService storageEgressExecutor;
    /** 专供 Caffeine removal 回调，虚拟线程 per task，不共享调度队列，避免 DelayedWorkQueue 堆积 */
    private final ExecutorService cacheRemovalExecutor;
    private final Lock fairLock;
    private final Condition permitReleased;
    private final FlowCacheManager flowCacheManager;
    /**
     * 由 FlowManager 在初始化时注入，供 Storage 按 jobId 查找 Launcher，避免 Registry 依赖
     * FlowManager 类
     */
    @Setter
    private volatile ActiveLauncherLookup launcherLookup;

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

    private FlowResourceRegistry(TemplateConfigProperties.Flow flowConfig) {
        this.flowConfig = flowConfig;

        // 初始化全局并发信号量
        int concurrencyLimit = flowConfig.getConsumer().getConcurrencyLimit();
        this.globalSemaphore = new Semaphore(concurrencyLimit, true);
        log.info("FlowResourceRegistry 启动：初始物理并发池大小为 {}", concurrencyLimit);

        // 记录资源使用情况
        FlowMetrics.recordResourceUsage("semaphore_max_limit", concurrencyLimit);
        FlowMetrics.recordResourceUsage("semaphore_available", concurrencyLimit);

        // 委托 Provider 创建执行器；cacheRemovalExecutor 用有界提交，使 Caffeine 在无消费许可时阻塞提交，实现向前背压
        this.executorProvider = new DefaultFlowExecutorProvider(globalSemaphore, concurrencyLimit);
        this.flowConsumerExecutor = (BoundedVirtualExecutor) executorProvider.getFlowConsumerExecutor();
        this.storageEgressExecutor = executorProvider.getStorageEgressExecutor();
        this.cacheRemovalExecutor = executorProvider.getCacheRemovalExecutor();

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

    private static FlowResourceRegistry create(TemplateConfigProperties.Flow globalConfig) {
        return new FlowResourceRegistry(globalConfig);
    }

    /**
     * 获取全局资源注册表实例（单例模式）
     * 如果配置发生变化，会关闭旧实例并创建新实例
     *
     * @param globalConfig 全局配置
     * @return FlowResourceRegistry 实例
     */
    public static FlowResourceRegistry getInstance(TemplateConfigProperties.Flow globalConfig) {
        if (instance == null || configChanged(globalConfig)) {
            synchronized (FlowResourceRegistry.class) {
                if (instance == null || configChanged(globalConfig)) {
                    if (instance != null) {
                        log.info("检测到配置变更 [Limit: {} -> {}], 正在重新初始化资源...", 
                            lastConcurrencyLimit, globalConfig.getConsumer().getConcurrencyLimit());
                        try {
                            instance.shutdown();
                        } catch (Exception e) {
                            log.error("关闭旧资源失败", e);
                        }
                    }
                    instance = create(globalConfig);
                    lastConcurrencyLimit = globalConfig.getConsumer().getConcurrencyLimit();
                    lastProgressDisplaySecond = globalConfig.getMonitor().getProgressDisplaySecond();
                }
            }
        }
        return instance;
    }

    /**
     * 检测两个配置是否发生变更
     */
    private static boolean configChanged(TemplateConfigProperties.Flow config) {
        if (config == null) {
            return false;
        }
        return config.getConsumer().getConcurrencyLimit() != lastConcurrencyLimit
                || config.getMonitor().getProgressDisplaySecond() != lastProgressDisplaySecond;
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
        }
    }

    /**
     * 包可见的构造函数，用于测试
     * 测试代码可以直接创建实例而不使用单例
     */
    FlowResourceRegistry(TemplateConfigProperties.Flow flowConfig, boolean unused) {
        this(flowConfig);
    }

    /**
     * 获取缓存管理器
     */
    public FlowCacheManager getCacheManager() {
        return flowCacheManager;
    }

    /**
     * 提交消费任务（带公平 acquire）
     */
    public void submitConsumerToGlobal(Orchestrator orchestrator, Runnable task) {
        submitConsumerToGlobal(orchestrator, 1, task);
    }

    /**
     * 提交消费任务（多 permit，如配对场景）
     */
    public void submitConsumerToGlobal(Orchestrator orchestrator, int permits, Runnable task) {
        BoundedVirtualExecutor.PermitStrategy strategy = new BoundedVirtualExecutor.PermitStrategy() {
            @Override
            public void acquire() throws InterruptedException {
                for (int i = 0; i < permits; i++) {
                    orchestrator.acquire();
                }
            }

            @Override
            public void release() {
                for (int i = 0; i < permits; i++) {
                    orchestrator.releaseWithoutSemaphore();
                }
                globalSemaphore.release(permits);
            }
        };
        flowConsumerExecutor.submitWithStrategy(strategy, task);
    }

    /**
     * 统一关闭所有全局资源
     *
     * <p>
     * 按照依赖顺序关闭：先关闭依赖其他资源的资源，最后关闭基础资源。
     * </p>
     *
     * <p>
     * 关闭顺序：
     * </p>
     * <ol>
     * <li>缓存管理器（依赖执行器）</li>
     * <li>缓存移除执行器（Caffeine 回调用）</li>
     * <li>存储出口执行器</li>
     * <li>流消费执行器（最后关闭）</li>
     * </ol>
     *
     * <p>
     * 如果关闭过程中出现异常，会记录错误但继续关闭其他资源，最后抛出 ResourceShutdownException。
     * </p>
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

        // 2-4. 关闭各执行器
        shutdownExecutorSafely("缓存移除执行器", cacheRemovalExecutor, errors);
        shutdownExecutorSafely("存储出口执行器", storageEgressExecutor, errors);
        shutdownExecutorSafely("流消费执行器", flowConsumerExecutor, errors);

        shutdown.set(true);

        if (!errors.isEmpty()) {
            ResourceShutdownException exception = new ResourceShutdownException("部分资源关闭失败", errors);
            log.error("FlowResourceRegistry 关闭完成，但有 {} 个错误", errors.size());
            throw exception;
        }

        log.info("FlowResourceRegistry 关闭完成");
    }

    /**
     * 安全关闭执行器：若执行器未关闭则调用 shutdownExecutor，异常收集到 errors
     */
    private void shutdownExecutorSafely(String name, ExecutorService executor, java.util.List<Exception> errors) {
        if (executor != null && !executor.isShutdown()) {
            try {
                shutdownExecutor(name, executor);
            } catch (Exception e) {
                errors.add(e);
                log.error("关闭{}失败", name, e);
            }
        }
    }

    /**
     * 关闭执行器，带超时保护
     *
     * <p>
     * 关闭流程：
     * </p>
     * <ol>
     * <li>调用 shutdown() 优雅关闭</li>
     * <li>等待指定超时时间</li>
     * <li>如果超时，调用 shutdownNow() 强制关闭</li>
     * <li>再等待一段时间确保完全关闭</li>
     * </ol>
     *
     * @param name     执行器名称（用于日志）
     * @param executor 要关闭的执行器
     */
    private void shutdownExecutor(String name, ExecutorService executor) throws ResourceShutdownException {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(FlowConstants.DEFAULT_SHUTDOWN_TIMEOUT_SECONDS,
                    FlowConstants.DEFAULT_SHUTDOWN_TIMEOUT_UNIT)) {
                log.warn("{} 未能在{}秒内关闭，强制关闭", name, FlowConstants.DEFAULT_SHUTDOWN_TIMEOUT_SECONDS);
                executor.shutdownNow();
                // 再等待一段时间
                if (!executor.awaitTermination(FlowConstants.FORCE_SHUTDOWN_WAIT_SECONDS,
                        FlowConstants.DEFAULT_SHUTDOWN_TIMEOUT_UNIT)) {
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
