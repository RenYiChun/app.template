package com.lrenyi.template.flow.manager;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.exception.FlowExceptionHelper;
import com.lrenyi.template.flow.exception.FlowPhase;
import com.lrenyi.template.flow.health.FlowHealth;
import com.lrenyi.template.flow.health.FlowResourceHealthIndicator;
import com.lrenyi.template.flow.health.HealthStatus;
import com.lrenyi.template.flow.internal.FlowLauncher;
import com.lrenyi.template.flow.metrics.FlowResourceMetrics;
import com.lrenyi.template.flow.resource.ActiveLauncherLookup;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import com.lrenyi.template.flow.util.FlowLogHelper;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

/**
 * Job生命周期管理器
 * 职责：专注于Job生命周期管理，不再直接管理资源
 */
@Slf4j
@Getter
@Setter
public class FlowManager implements ActiveLauncherLookup {
    private static final AtomicReference<FlowManager> instanceRef = new AtomicReference<>();
    private static int lastConcurrencyLimit = -1;
    private final TemplateConfigProperties.Flow globalConfig;
    private final FlowResourceRegistry resourceRegistry;
    private final MeterRegistry meterRegistry;

    private final Map<String, FlowLauncher<Object>> activeLaunchers = new ConcurrentHashMap<>();
    private final Map<String, ProgressTracker> completedTrackers = new ConcurrentHashMap<>();
    /** 显式注册的 jobId -> 显示名，用于监控指标 */
    private final Map<String, String> jobIdToDisplayName = new ConcurrentHashMap<>();

    FlowManager(TemplateConfigProperties.Flow globalConfig, MeterRegistry meterRegistry, boolean unused) {
        this(globalConfig, meterRegistry);
        log.trace("FlowManager initializing: {}", unused);
    }

    private FlowManager(TemplateConfigProperties.Flow globalConfig, MeterRegistry meterRegistry) {
        this.globalConfig = globalConfig;
        this.meterRegistry = meterRegistry;
        this.meterRegistry.config().meterFilter(new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.@NonNull Id id,
                @NonNull DistributionStatisticConfig config) {
                if (id.getName().startsWith("app.template.flow")) {
                    return DistributionStatisticConfig.builder().percentilesHistogram(true).build().merge(config);
                }
                return config;
            }
        });
        this.resourceRegistry = FlowResourceRegistry.getInstance(globalConfig, meterRegistry);
        this.resourceRegistry.setLauncherLookup(this);
        FlowExceptionHelper.setMeterRegistry(meterRegistry);
        log.info("FlowManager 启动");
    }

    /**
     * 兼容旧调用方（无 MeterRegistry 参数），使用 SimpleMeterRegistry 作为 fallback。
     * 注意：这将为每个实例创建一个新的 Registry，如果可能，请传递共享的 Registry。
     * 配置变更时会重建实例，需委托给两参版本以执行 configChanged 检查。
     */
    public static FlowManager getInstance(TemplateConfigProperties.Flow globalConfig) {
        return getInstance(globalConfig, new SimpleMeterRegistry());
    }

    public static FlowManager getInstance(TemplateConfigProperties.Flow globalConfig, MeterRegistry meterRegistry) {
        FlowManager current = instanceRef.get();
        if (current == null || configChanged(globalConfig)) {
            synchronized (FlowManager.class) {
                current = instanceRef.get();
                if (current == null || configChanged(globalConfig)) {
                    if (current != null) {
                        log.info("检测到 FlowManager 配置变更 [Limit: {} -> {}], 正在重启管理器...",
                                 lastConcurrencyLimit,
                                 globalConfig.getLimits().getGlobal().getConsumerThreads()
                        );
                        try {
                            current.shutdownAll();
                        } catch (Exception e) {
                            log.error("关闭旧管理器失败", e);
                        }
                    }
                    FlowManager newInstance = create(globalConfig, meterRegistry);
                    instanceRef.set(newInstance);
                    lastConcurrencyLimit = globalConfig.getLimits().getGlobal().getConsumerThreads();
                }
            }
        }
        return instanceRef.get();
    }

    private static boolean configChanged(TemplateConfigProperties.Flow config) {
        if (config == null) {
            return false;
        }
        return config.getLimits().getGlobal().getConsumerThreads() != lastConcurrencyLimit;
    }

    public void shutdownAll() {
        log.info("系统关闭：正在注销所有任务...");
        try {
            stopAll(true);
        } catch (Exception e) {
            log.error("停止所有任务时发生异常", e);
        }
    }

    private static FlowManager create(TemplateConfigProperties.Flow globalConfig, MeterRegistry meterRegistry) {
        return new FlowManager(globalConfig, meterRegistry).init();
    }

    public void stopAll(boolean force) {
        log.info("正在停止所有运行中的任务，force={}", force);
        activeLaunchers.forEach((key, launcher) -> stopJob(force, launcher));
    }

    private FlowManager init() {
        FlowResourceHealthIndicator healthIndicator = new FlowResourceHealthIndicator(resourceRegistry, this);
        FlowHealth.registerIndicator(healthIndicator);
        FlowResourceMetrics.register(this, meterRegistry);

        return this;
    }

    private void stopJob(boolean force, FlowLauncher<?> launcher) {
        try {
            launcher.stop(force);
        } catch (Exception e) {
            FlowExceptionHelper.handleException(launcher.getJobId(),
                                                null,
                                                e,
                                                FlowPhase.FINALIZATION,
                                                "stop_job_failed",
                                                launcher.getMetricJobId()
            );
            log.error("停止 Job [{}] 时发生异常", launcher.getJobId(), e);
        }
    }

    public void unregister(String jobId) {
        FlowResourceMetrics.unregisterPerJob(jobId, meterRegistry);
        resourceRegistry.deregisterJob(jobId);
        jobIdToDisplayName.remove(jobId);
        FlowLauncher<?> launcher = activeLaunchers.remove(jobId);
        if (launcher != null) {
            completedTrackers.put(jobId, launcher.getTracker());
            ExecutorService producerExecutor = launcher.getProducerExecutor();
            if (producerExecutor != null && !producerExecutor.isShutdown()) {
                producerExecutor.shutdown();
            }
            log.info("Job [{}] 已从管理器中注销", FlowLogHelper.formatJobContext(jobId, launcher.getMetricJobId()));
        }
    }

    /**
     * 停止 Job 并注销所有相关指标。
     * <p>
     * 此方法在 Job 被显式停止时调用,会注销所有 per-job 指标(包括背压指标)。
     *
     * @param jobId Job ID
     * @param launcher FlowLauncher 实例
     */
    void stopJobAndUnregisterMetrics(String jobId, FlowLauncher<?> launcher) {
        // 先注销背压指标
        launcher.getBackpressureManager().unregisterMetrics(meterRegistry);
        // 然后停止 Job
        launcher.stop(true);
    }

    public static void reset() {
        synchronized (FlowManager.class) {
            FlowManager current = instanceRef.get();
            if (current != null) {
                try {
                    current.shutdownAll();
                } catch (Exception e) {
                    log.warn("重置时关闭实例失败", e);
                }
            }
            instanceRef.set(null);
        }
    }

    public <T> FlowLauncher<T> createLauncher(String jobId,
        FlowJoiner<T> flowJoiner,
        ProgressTracker tracker,
        TemplateConfigProperties.Flow flowConfig) {
        return createLauncher(jobId, null, flowJoiner, tracker, flowConfig);
    }

    /**
     * 创建 Launcher，支持显式指定监控展示名。
     *
     * @param jobId       业务 jobId
     * @param displayName 监控展示名，null 时使用 jobId 原样
     */
    public <T> FlowLauncher<T> createLauncher(String jobId,
        String displayName,
        FlowJoiner<T> flowJoiner,
        ProgressTracker tracker,
        TemplateConfigProperties.Flow flowConfig) {
        try {
            if (activeLaunchers.containsKey(jobId)) {
                throw new IllegalStateException(
                    "Job " + jobId + " 未结束，不能重复创建。请先对该 job 执行 stop 后再启动新任务。");
            }
            completedTrackers.remove(jobId);

            if (displayName != null && !displayName.isEmpty()) {
                jobIdToDisplayName.put(jobId, displayName);
            }
            String metricJobId = resolveMetricJobId(jobId);
            tracker.setMetricJobId(metricJobId);

            FlowLauncher<T> launcher =
                FlowLauncherFactory.create(this, jobId, metricJobId, flowJoiner, tracker, flowConfig);
            activeLaunchers.put(jobId, (FlowLauncher<Object>) launcher);
            resourceRegistry.registerJob(jobId);
            FlowResourceMetrics.registerPerJob(launcher, meterRegistry);
            return launcher;
        } catch (Exception e) {
            FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.PRODUCTION, "create_launcher_failed");
            throw e;
        }
    }

    /** 解析用于指标标签的 jobId：显式注册 > 原样 */
    private String resolveMetricJobId(String jobId) {
        String explicit = jobIdToDisplayName.get(jobId);
        return explicit != null ? explicit : jobId;
    }

    public boolean isStopped(String jobId) {
        return !activeLaunchers.containsKey(jobId);
    }

    public void stopById(String jobId, boolean force) {
        for (Map.Entry<String, FlowLauncher<Object>> entry : activeLaunchers.entrySet()) {
            FlowLauncher<?> launcher = entry.getValue();
            if (launcher.getJobId().equals(jobId)) {
                stopJob(force, launcher);
                break;
            }
        }
    }

    public Map<String, FlowLauncher<Object>> getActiveLaunchers() {
        return Collections.unmodifiableMap(activeLaunchers);
    }

    public ProgressTracker getProgressTracker(String jobId) {
        ProgressTracker completed = completedTrackers.get(jobId);
        if (completed != null) {
            return completed;
        }
        FlowLauncher<?> launcher = activeLaunchers.get(jobId);
        return launcher != null ? launcher.getTracker() : null;
    }

    public boolean isCompleted(String jobId) {
        ProgressTracker tracker = getProgressTracker(jobId);
        return tracker != null && tracker.isCompleted();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> FlowLauncher<T> getActiveLauncher(String jobId) {
        return (FlowLauncher<T>) activeLaunchers.get(jobId);
    }

    /**
     * 当前活跃 Job 数。返回至少 1 以避免 fair share 等计算中的除零，无 Job 时仍返回 1。
     */
    public int getActiveJobCount() {
        return Math.max(1, activeLaunchers.size());
    }

    public Map<String, Object> getHealthStatus() {
        return FlowHealth.getHealthDetails();
    }

    public HealthStatus checkHealth() {
        return FlowHealth.checkHealth();
    }
}
