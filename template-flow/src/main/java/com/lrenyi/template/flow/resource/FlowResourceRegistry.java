package com.lrenyi.template.flow.resource;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.context.Orchestrator;
import com.lrenyi.template.flow.executor.BoundedVirtualExecutor;
import com.lrenyi.template.flow.executor.DefaultFlowExecutorProvider;
import com.lrenyi.template.flow.executor.FlowExecutorProvider;
import com.lrenyi.template.flow.manager.FlowCacheManager;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.model.FlowConstants;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 全局资源注册表
 */
@Slf4j
@Getter
public class FlowResourceRegistry implements ResourceLifecycle {
    private static final AtomicReference<FlowResourceRegistry> instanceRef = new AtomicReference<>();
    private static final AtomicInteger lastConcurrencyLimitRef = new AtomicInteger(-1);
    
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            FlowResourceRegistry inst = instanceRef.get();
            if (inst != null && inst.isInitialized() && !inst.isShutdown()) {
                log.info("FlowResourceRegistry: JVM shutdown hook 触发兜底关闭");
                try {
                    inst.shutdown();
                } catch (Exception e) {
                    log.error("FlowResourceRegistry shutdown hook failed.", e);
                }
            }
        }, FlowConstants.THREAD_NAME_SHUTDOWN_HOOK
        ));
    }
    
    private final TemplateConfigProperties.Flow flowConfig;
    private final Semaphore globalSemaphore;
    private final FlowExecutorProvider executorProvider;
    private final BoundedVirtualExecutor flowConsumerExecutor;
    private final ScheduledExecutorService storageEgressExecutor;
    private final ExecutorService cacheRemovalExecutor;
    private final Lock fairLock;
    private final Condition permitReleased;
    private final FlowCacheManager flowCacheManager;
    private final MeterRegistry meterRegistry;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicReference<ActiveLauncherLookup> launcherLookupRef = new AtomicReference<>();
    
    FlowResourceRegistry(TemplateConfigProperties.Flow flowConfig, MeterRegistry meterRegistry, boolean unused) {
        this(flowConfig, meterRegistry);
        log.trace("FlowResourceRegistry initialized: {}", unused);
    }
    
    private FlowResourceRegistry(TemplateConfigProperties.Flow flowConfig, MeterRegistry meterRegistry) {
        this.flowConfig = flowConfig;
        this.meterRegistry = meterRegistry;
        
        int concurrencyLimit = flowConfig.getConsumer().getConcurrencyLimit();
        this.globalSemaphore = new Semaphore(concurrencyLimit, true);
        log.info("FlowResourceRegistry 启动：初始物理并发池大小为 {}", concurrencyLimit);
        
        Gauge.builder(FlowMetricNames.SEMAPHORE_USED, globalSemaphore, s -> concurrencyLimit - s.availablePermits())
             .description("全局消费信号量已占用许可数")
             .register(meterRegistry);
        
        Gauge.builder(FlowMetricNames.SEMAPHORE_LIMIT, () -> concurrencyLimit)
             .description("全局消费信号量上限")
             .register(meterRegistry);
        
        this.executorProvider = new DefaultFlowExecutorProvider(globalSemaphore, concurrencyLimit);
        this.flowConsumerExecutor = (BoundedVirtualExecutor) executorProvider.getFlowConsumerExecutor();
        this.storageEgressExecutor = executorProvider.getStorageEgressExecutor();
        this.cacheRemovalExecutor = executorProvider.getCacheRemovalExecutor();
        
        this.fairLock = new ReentrantLock();
        this.permitReleased = fairLock.newCondition();
        
        this.flowCacheManager = new FlowCacheManager(this);
        
        this.initialized.set(true);
        log.info("FlowResourceRegistry 初始化完成");
    }
    
    public static FlowResourceRegistry getInstance(TemplateConfigProperties.Flow globalConfig,
            MeterRegistry meterRegistry) {
        FlowResourceRegistry current = instanceRef.get();
        if (current == null || configChanged(globalConfig)) {
            synchronized (FlowResourceRegistry.class) {
                current = instanceRef.get();
                if (current == null || configChanged(globalConfig)) {
                    if (current != null) {
                        log.info("检测到配置变更 [Limit: {} -> {}], 正在重新初始化资源...",
                                 lastConcurrencyLimitRef.get(),
                                 globalConfig.getConsumer().getConcurrencyLimit()
                        );
                        try {
                            current.shutdown();
                        } catch (Exception e) {
                            log.error("关闭旧资源失败", e);
                        }
                    }
                    FlowResourceRegistry newInstance = create(globalConfig, meterRegistry);
                    instanceRef.set(newInstance);
                    lastConcurrencyLimitRef.set(globalConfig.getConsumer().getConcurrencyLimit());
                    return newInstance;
                }
            }
        }
        return instanceRef.get();
    }
    
    private static boolean configChanged(TemplateConfigProperties.Flow config) {
        if (config == null) {
            return false;
        }
        return config.getConsumer().getConcurrencyLimit() != lastConcurrencyLimitRef.get();
    }
    
    private static FlowResourceRegistry create(TemplateConfigProperties.Flow globalConfig,
            MeterRegistry meterRegistry) {
        return new FlowResourceRegistry(globalConfig, meterRegistry);
    }
    
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
    
    private void shutdownExecutor(String name, ExecutorService executor) throws ResourceShutdownException {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(FlowConstants.DEFAULT_SHUTDOWN_TIMEOUT_SECONDS,
                                           FlowConstants.DEFAULT_SHUTDOWN_TIMEOUT_UNIT
            )) {
                log.warn("{} 未能在{}秒内关闭，强制关闭", name, FlowConstants.DEFAULT_SHUTDOWN_TIMEOUT_SECONDS);
                executor.shutdownNow();
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
    
    public static void reset() {
        synchronized (FlowResourceRegistry.class) {
            FlowResourceRegistry inst = instanceRef.get();
            if (inst != null) {
                try {
                    inst.shutdown();
                } catch (Exception e) {
                    log.warn("重置时关闭实例失败", e);
                }
            }
            instanceRef.set(null);
        }
    }
    
    public ActiveLauncherLookup getLauncherLookup() {
        return launcherLookupRef.get();
    }
    
    public void setLauncherLookup(ActiveLauncherLookup launcherLookup) {
        launcherLookupRef.set(launcherLookup);
    }
    
    @Override
    public void initialize() throws ResourceInitializationException {
        if (!initialized.get()) {
            throw new ResourceInitializationException("FlowResourceRegistry initialization failed");
        }
    }
    
    @Override
    public void shutdown() throws ResourceShutdownException {
        if (shutdown.get()) {
            log.debug("FlowResourceRegistry 已经关闭，跳过重复关闭");
            return;
        }
        
        log.info("FlowResourceRegistry 关闭：正在关闭所有全局资源...");
        java.util.List<Exception> errors = new java.util.ArrayList<>();
        
        if (flowCacheManager != null) {
            try {
                flowCacheManager.invalidateAll();
            } catch (Exception e) {
                errors.add(e);
                log.error("关闭缓存管理器失败", e);
            }
        }
        
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
    
    public FlowCacheManager getCacheManager() {
        return flowCacheManager;
    }
    
    public void submitConsumerToGlobal(Orchestrator orchestrator, Runnable task) {
        submitConsumerToGlobal(orchestrator, 1, task);
    }
    
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
}
