package com.lrenyi.template.core.flow.manager;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.flow.api.FlowJoiner;
import com.lrenyi.template.core.flow.api.ProgressTracker;
import com.lrenyi.template.core.flow.context.Orchestrator;
import com.lrenyi.template.core.flow.context.Registration;
import com.lrenyi.template.core.flow.exception.FlowExceptionHelper;
import com.lrenyi.template.core.flow.exception.FlowPhase;
import com.lrenyi.template.core.flow.health.FlowHealth;
import com.lrenyi.template.core.flow.health.FlowResourceHealthIndicator;
import com.lrenyi.template.core.flow.health.HealthStatus;
import com.lrenyi.template.core.flow.internal.FlowLauncher;
import com.lrenyi.template.core.flow.metrics.FlowMetricNames;
import com.lrenyi.template.core.flow.resource.ActiveLauncherLookup;
import com.lrenyi.template.core.flow.resource.FlowResourceRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Job生命周期管理器
 * 职责：专注于Job生命周期管理，不再直接管理资源
 */
@Slf4j
@Getter
@Setter
public class FlowManager implements ActiveLauncherLookup {
    private static volatile FlowManager instance;
    private static int lastConcurrencyLimit = -1;
    private final TemplateConfigProperties.Flow globalConfig;
    private final FlowResourceRegistry resourceRegistry;
    private final MeterRegistry meterRegistry;

    private final Map<String, Registration> registry = new ConcurrentHashMap<>();
    private final Map<String, FlowLauncher<?>> activeLaunchers = new ConcurrentHashMap<>();

    private FlowManager(TemplateConfigProperties.Flow globalConfig, MeterRegistry meterRegistry) {
        this.globalConfig = globalConfig;
        this.meterRegistry = meterRegistry;
        this.resourceRegistry = FlowResourceRegistry.getInstance(globalConfig, meterRegistry);
        this.resourceRegistry.setLauncherLookup(this);
        log.info("FlowManager 启动");
    }

    private FlowManager init() {
        Gauge.builder(FlowMetricNames.LAUNCHERS_ACTIVE, activeLaunchers, Map::size)
             .description("当前正在运行的 Job 数量")
             .register(meterRegistry);

        FlowResourceHealthIndicator healthIndicator = new FlowResourceHealthIndicator(resourceRegistry, this);
        FlowHealth.registerIndicator(healthIndicator);

        return this;
    }

    private static FlowManager create(TemplateConfigProperties.Flow globalConfig, MeterRegistry meterRegistry) {
        return new FlowManager(globalConfig, meterRegistry).init();
    }

    public static FlowManager getInstance(TemplateConfigProperties.Flow globalConfig, MeterRegistry meterRegistry) {
        if (instance == null || configChanged(globalConfig)) {
            synchronized (FlowManager.class) {
                if (instance == null || configChanged(globalConfig)) {
                    if (instance != null) {
                        log.info("检测到 FlowManager 配置变更 [Limit: {} -> {}], 正在重启管理器...",
                            lastConcurrencyLimit, globalConfig.getConsumer().getConcurrencyLimit());
                        try {
                            instance.shutdownAll();
                        } catch (Exception e) {
                            log.error("关闭旧管理器失败", e);
                        }
                    }
                    instance = create(globalConfig, meterRegistry);
                    lastConcurrencyLimit = globalConfig.getConsumer().getConcurrencyLimit();
                }
            }
        }
        return instance;
    }

    /**
     * 兼容旧调用方（无 MeterRegistry 参数），使用 SimpleMeterRegistry 作为 fallback
     */
    public static FlowManager getInstance(TemplateConfigProperties.Flow globalConfig) {
        return getInstance(globalConfig, new SimpleMeterRegistry());
    }

    private static boolean configChanged(TemplateConfigProperties.Flow config) {
        if (config == null) {
            return false;
        }
        return config.getConsumer().getConcurrencyLimit() != lastConcurrencyLimit;
    }

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
        }
    }

    FlowManager(TemplateConfigProperties.Flow globalConfig, MeterRegistry meterRegistry, boolean unused) {
        this(globalConfig, meterRegistry);
    }

    public <T> FlowLauncher<T> createLauncher(String jobId,
            FlowJoiner<T> flowJoiner,
            ProgressTracker tracker,
            TemplateConfigProperties.Flow flowConfig) {
        try {
            Registration registration = new Registration(jobId, flowConfig);
            registry.put(jobId, registration);

            FlowLauncher<T> launcher = FlowLauncherFactory.create(this, jobId, flowJoiner, tracker, registration);
            activeLaunchers.put(jobId, launcher);

            Counter.builder(FlowMetricNames.JOB_STARTED)
                   .tag(FlowMetricNames.TAG_JOB_ID, jobId)
                   .register(meterRegistry)
                   .increment();

            return launcher;
        } catch (Exception e) {
            FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.PRODUCTION);
            Counter.builder(FlowMetricNames.ERRORS)
                   .tag(FlowMetricNames.TAG_ERROR_TYPE, "create_launcher_failed")
                   .tag(FlowMetricNames.TAG_PHASE, "PRODUCTION")
                   .register(meterRegistry)
                   .increment();
            throw e;
        }
    }

    public boolean isStopped(String jobId) {
        return !activeLaunchers.containsKey(jobId);
    }

    public void unregister(String jobId) {
        FlowLauncher<?> launcher = activeLaunchers.remove(jobId);
        if (launcher != null) {
            ExecutorService producerExecutor = launcher.getProducerExecutor();
            if (producerExecutor != null && !producerExecutor.isShutdown()) {
                producerExecutor.shutdown();
            }
        }
        registry.remove(jobId);
        log.info("Job [{}] 已从管理器中注销", jobId);
    }

    public void stopAll(boolean force) {
        log.info("正在停止所有运行中的任务，force={}", force);
        activeLaunchers.forEach((key, launcher) -> stopJob(force, key, launcher));
    }

    private void stopJob(boolean force, String key, FlowLauncher<?> launcher) {
        try {
            launcher.stop(force);
            unregister(key);
            Counter.builder(FlowMetricNames.JOB_STOPPED)
                   .tag(FlowMetricNames.TAG_JOB_ID, launcher.getJobId())
                   .register(meterRegistry)
                   .increment();
        } catch (Exception e) {
            FlowExceptionHelper.handleException(launcher.getJobId(), null, e, FlowPhase.FINALIZATION);
            Counter.builder(FlowMetricNames.ERRORS)
                   .tag(FlowMetricNames.TAG_ERROR_TYPE, "stop_job_failed")
                   .tag(FlowMetricNames.TAG_PHASE, "FINALIZATION")
                   .register(meterRegistry)
                   .increment();
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

    @Override
    public FlowLauncher<?> getActiveLauncher(String jobId) {
        return activeLaunchers.get(jobId);
    }

    public ProgressTracker getProgressTracker(String jobId) {
        @SuppressWarnings("unchecked")
        FlowLauncher<Object> activeLauncher = (FlowLauncher<Object>) getActiveLauncher(jobId);
        Orchestrator taskOrchestrator = activeLauncher.getTaskOrchestrator();
        return taskOrchestrator.tracker();
    }

    public int getActiveJobCount() {
        return Math.max(1, registry.size());
    }

    public Map<String, Object> getHealthStatus() {
        return FlowHealth.getHealthDetails();
    }

    public HealthStatus checkHealth() {
        return FlowHealth.checkHealth();
    }

    public void shutdownAll() {
        log.info("系统关闭：正在注销所有任务...");
        try {
            stopAll(true);
        } catch (Exception e) {
            log.error("停止所有任务时发生异常", e);
        }
    }
}
