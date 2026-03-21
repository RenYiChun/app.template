package com.lrenyi.template.flow.resource;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.executor.DefaultFlowExecutorProvider;
import com.lrenyi.template.flow.executor.FlowExecutorProvider;
import com.lrenyi.template.flow.manager.FlowCacheManager;
import com.lrenyi.template.flow.model.FlowConstants;
import com.lrenyi.template.flow.util.FlowLogHelper;
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
    private static volatile String lastConfigFingerprint;
    
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
    private final Semaphore globalInFlightSemaphore;
    private final Semaphore globalStorageSemaphore;
    private final Semaphore globalProducerThreadsSemaphore;
    private final Semaphore globalInFlightConsumerSemaphore;
    private final LongAdder globalPendingConsumerAdder;
    private final FlowExecutorProvider executorProvider;
    private final ExecutorService flowConsumerExecutor;
    private final ExecutorService flowProducerExecutor;
    private final ScheduledExecutorService storageEgressExecutor;
    private final ExecutorService cacheRemovalExecutor;
    private final Lock fairLock;
    private final Condition permitReleased;
    private final AtomicInteger activeJobCount = new AtomicInteger(0);
    private final Lock fairShareLock = new ReentrantLock(true);
    private final Map<String, Condition> fairShareConditions = new ConcurrentHashMap<>();
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
        
        TemplateConfigProperties.Flow.Limits limits = flowConfig.getLimits();
        TemplateConfigProperties.Flow.Global global = limits.getGlobal();
        boolean fair = global.isFairScheduling();
        
        int concurrencyLimit = global.getConsumerThreads();
        this.globalSemaphore = concurrencyLimit > 0 ? new Semaphore(concurrencyLimit, fair) : null;
        log.info("FlowResourceRegistry 启动：全局消费并发限制={}（<=0 时禁用）", concurrencyLimit);
        
        int globalInFlight = global.getInFlightProduction();
        this.globalInFlightSemaphore = globalInFlight > 0 ? new Semaphore(globalInFlight, fair) : null;
        
        int globalStorage = global.getStorageCapacity();
        this.globalStorageSemaphore = globalStorage > 0 ? new Semaphore(globalStorage, fair) : null;
        
        int globalProducerThreads = global.getProducerThreads();
        this.globalProducerThreadsSemaphore =
                globalProducerThreads > 0 ? new Semaphore(globalProducerThreads, fair) : null;
        
        int globalInFlightConsumer = global.getInFlightConsumer();
        this.globalInFlightConsumerSemaphore =
                globalInFlightConsumer > 0 ? new Semaphore(globalInFlightConsumer, fair) : null;
        
        this.globalPendingConsumerAdder = new LongAdder();
        
        this.executorProvider = new DefaultFlowExecutorProvider(concurrencyLimit);
        this.flowConsumerExecutor = executorProvider.getFlowConsumerExecutor();
        this.flowProducerExecutor = executorProvider.getFlowProducerExecutor();
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
                                 globalConfig.getLimits().getGlobal().getConsumerThreads()
                        );
                        try {
                            current.shutdown();
                        } catch (Exception e) {
                            log.error("关闭旧资源失败", e);
                        }
                    }
                    FlowResourceRegistry newInstance = create(globalConfig, meterRegistry);
                    instanceRef.set(newInstance);
                    lastConcurrencyLimitRef.set(globalConfig.getLimits().getGlobal().getConsumerThreads());
                    lastConfigFingerprint = fingerprint(globalConfig);
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
        return !Objects.equals(fingerprint(config), lastConfigFingerprint);
    }

    private static String fingerprint(TemplateConfigProperties.Flow config) {
        TemplateConfigProperties.Flow.Global global = config.getLimits().getGlobal();
        return String.join("|",
                String.valueOf(global.getConsumerThreads()),
                String.valueOf(global.getInFlightProduction()),
                String.valueOf(global.getStorageCapacity()),
                String.valueOf(global.getProducerThreads()),
                String.valueOf(global.getInFlightConsumer()),
                String.valueOf(global.isFairScheduling()));
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
            lastConfigFingerprint = null;
            lastConcurrencyLimitRef.set(-1);
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
        shutdownExecutorSafely("流生产执行器", flowProducerExecutor, errors);
        
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
    
    
    /**
     * 释放全局存储额度（Storage 离库时调用）
     */
    public void releaseGlobalStorage(int n) {
        if (globalStorageSemaphore != null && n > 0) {
            globalStorageSemaphore.release(n);
        }
    }
    
    // ─── 动态 Fair Share 协调 ─────────────────────────────────────────────────
    
    /** Job 创建时调用，用于动态 fair share 的 activeJobCount */
    public void registerJob(String jobId) {
        activeJobCount.incrementAndGet();
        log.trace("FairShare: job registered, {}, activeJobCount={}", FlowLogHelper.formatJobContext(jobId, null),
                activeJobCount.get());
    }
    
    /** Job 注销时调用，唤醒等待配额的其他 Job */
    public void deregisterJob(String jobId) {
        int remaining = activeJobCount.decrementAndGet();
        if (remaining < 0) {
            activeJobCount.set(0);
            log.warn("FairShare: activeJobCount underflow, {}", FlowLogHelper.formatJobContext(jobId, null));
        }
        log.trace("FairShare: job deregistered, {}, activeJobCount={}", FlowLogHelper.formatJobContext(jobId, null),
                activeJobCount.get());
        signalAllDimensions();
    }
    
    /** 当前活跃 Job 数（用于动态 fair share 配额计算） */
    public int getActiveJobCount() {
        return Math.max(1, activeJobCount.get());
    }
    
    /**
     * 在持有配额前等待，直到 holding &lt; ceil(globalLimit / activeJobCount) 或超时/中断。
     *
     * @param dimensionId   维度 ID
     * @param globalLimit   该维度的全局上限（&lt;=0 时直接返回）
     * @param holdingSupplier 当前 Job 在该维度的持有数
     * @param timeoutMs    超时毫秒
     * @param stopCheck    停止检查（返回 true 时提前退出）
     */
    public void awaitFairShare(String dimensionId,
            int globalLimit,
            IntSupplier holdingSupplier,
            long timeoutMs,
            BooleanSupplier stopCheck) throws InterruptedException, TimeoutException {
        if (globalLimit <= 0) {
            return;
        }
        Condition cond = fairShareConditions.computeIfAbsent(dimensionId, k -> fairShareLock.newCondition());
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        fairShareLock.lock();
        try {
            while (true) {
                int active = getActiveJobCount();
                int quota = (globalLimit + active - 1) / active;
                if (holdingSupplier.getAsInt() < quota) {
                    return;
                }
                if (stopCheck != null && stopCheck.getAsBoolean()) {
                    return;
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new TimeoutException("fair share await timeout, dimensionId=" + dimensionId);
                }
                cond.await(remaining, TimeUnit.NANOSECONDS);
            }
        } finally {
            fairShareLock.unlock();
        }
    }
    
    /** 某维度释放 lease 后调用，唤醒等待该维度配额的 Job */
    public void signalFairShare(String dimensionId) {
        Condition cond = fairShareConditions.get(dimensionId);
        if (cond != null) {
            fairShareLock.lock();
            try {
                cond.signalAll();
            } finally {
                fairShareLock.unlock();
            }
        }
    }
    
    private void signalAllDimensions() {
        fairShareLock.lock();
        try {
            for (Condition cond : fairShareConditions.values()) {
                cond.signalAll();
            }
        } finally {
            fairShareLock.unlock();
        }
    }
}
