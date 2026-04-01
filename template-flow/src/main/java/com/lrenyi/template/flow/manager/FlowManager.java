package com.lrenyi.template.flow.manager;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
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
import com.lrenyi.template.flow.metrics.FlowMetricTags;
import com.lrenyi.template.flow.metrics.FlowTerminalMetrics;
import com.lrenyi.template.flow.model.FlowConsumeExecutionMode;
import com.lrenyi.template.flow.resource.ActiveLauncherLookup;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import com.lrenyi.template.flow.util.FlowLogHelper;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
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
    private static String lastConfigFingerprint;
    /** 延迟秒数，需覆盖至少 2–3 个 Prometheus 抓取周期（scrape_interval 常为 15–60s），
     * 以便抓取到 Job 完成时的最终 0 值。否则时序停止后，Prometheus 会持续返回最后样本直至 stale（约 5min）。 */
    private static final int UNREGISTER_DELAY_SECONDS = 90;
    private final TemplateConfigProperties.Flow globalConfig;
    private final FlowResourceRegistry resourceRegistry;
    private final MeterRegistry meterRegistry;
    private final FlowTerminalMetrics terminalMetrics;

    private final Map<String, FlowLauncher<Object>> activeLaunchers = new ConcurrentHashMap<>();
    private final Map<String, ProgressTracker> completedTrackers = new ConcurrentHashMap<>();
    /** 显式注册的 jobId -> 显示名，用于监控指标 */
    private final Map<String, String> jobIdToDisplayName = new ConcurrentHashMap<>();
    private final Map<String, Long> jobGenerations = new ConcurrentHashMap<>();
    private final AtomicLong nextGeneration = new AtomicLong(0L);
    private final ScheduledExecutorService delayedUnregisterExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "flow-unregister-scheduler");
                thread.setDaemon(true);
                return thread;
            });

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
        this.terminalMetrics = new FlowTerminalMetrics(meterRegistry, delayedUnregisterExecutor);
        log.info("FlowManager 启动");
    }

    /**
     * 兼容旧调用方（无 MeterRegistry 参数），使用 SimpleMeterRegistry 作为 fallback。
     * 注意：这将为每个实例创建一个新的 Registry，如果可能，请传递共享的 Registry。
     * 配置变更时会重建实例，需委托给两参版本以执行 configChanged 检查。
     */
    public static FlowManager getInstance(TemplateConfigProperties.Flow globalConfig) {
        FlowManager current = instanceRef.get();
        if (current != null && !configChanged(globalConfig)) {
            return current;
        }
        log.warn("FlowManager.getInstance(flowConfig) 使用回退 SimpleMeterRegistry；建议改为显式传入应用的 MeterRegistry。");
        return getInstance(globalConfig, new SimpleMeterRegistry());
    }

    public static FlowManager getInstance(TemplateConfigProperties.Flow globalConfig, MeterRegistry meterRegistry) {
        FlowManager current = instanceRef.get();
        if (current == null || configChanged(globalConfig) || shouldRebindMeterRegistry(current, meterRegistry)) {
            synchronized (FlowManager.class) {
                current = instanceRef.get();
                if (current == null || configChanged(globalConfig) || shouldRebindMeterRegistry(current, meterRegistry)) {
                    if (current != null) {
                        if (shouldRebindMeterRegistry(current, meterRegistry)) {
                            log.info("检测到更高优先级的 MeterRegistry，正在重建 FlowManager...");
                        } else {
                            log.info("检测到 FlowManager 配置变更，正在重启管理器...");
                        }
                        try {
                            current.shutdownAll();
                        } catch (Exception e) {
                            log.error("关闭旧管理器失败", e);
                        }
                    }
                    FlowManager newInstance = create(globalConfig, meterRegistry);
                    instanceRef.set(newInstance);
                    lastConfigFingerprint = fingerprint(globalConfig);
                }
            }
        }
        return instanceRef.get();
    }

    private static boolean shouldRebindMeterRegistry(FlowManager current, MeterRegistry meterRegistry) {
        return current != null
                && current.getMeterRegistry() instanceof SimpleMeterRegistry
                && !(meterRegistry instanceof SimpleMeterRegistry)
                && current.getMeterRegistry() != meterRegistry;
    }

    private static boolean configChanged(TemplateConfigProperties.Flow config) {
        if (config == null) {
            return false;
        }
        return !Objects.equals(fingerprint(config), lastConfigFingerprint);
    }

    private static String fingerprint(TemplateConfigProperties.Flow config) {
        StringBuilder builder = new StringBuilder();
        appendFingerprint(builder, config, java.util.Collections.newSetFromMap(new IdentityHashMap<>()));
        return builder.toString();
    }

    private static void appendFingerprint(StringBuilder builder, Object value, Set<Object> visited) {
        if (value == null) {
            builder.append("null;");
            return;
        }
        Class<?> type = value.getClass();
        if (isSimpleValue(type)) {
            builder.append(type.getName()).append('=').append(value).append(';');
            return;
        }
        if (!visited.add(value)) {
            builder.append(type.getName()).append("@cycle;");
            return;
        }
        if (type.isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            builder.append(type.getComponentType().getName()).append('[').append(length).append("]{");
            for (int i = 0; i < length; i++) {
                appendFingerprint(builder, java.lang.reflect.Array.get(value, i), visited);
            }
            builder.append("};");
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            builder.append(type.getName()).append('[');
            for (Object item : iterable) {
                appendFingerprint(builder, item, visited);
            }
            builder.append("];");
            return;
        }
        if (value instanceof Map<?, ?> map) {
            builder.append(type.getName()).append('{');
            map.entrySet().stream()
               .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(String::valueOf)))
               .forEach(entry -> {
                   appendFingerprint(builder, entry.getKey(), visited);
                   appendFingerprint(builder, entry.getValue(), visited);
               });
            builder.append("};");
            return;
        }
        builder.append(type.getName()).append('{');
        java.lang.reflect.Field[] fields = type.getDeclaredFields();
        java.util.Arrays.sort(fields, java.util.Comparator.comparing(java.lang.reflect.Field::getName));
        for (java.lang.reflect.Field field : fields) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true); // NOSONAR
            builder.append(field.getName()).append('=');
            try {
                appendFingerprint(builder, field.get(value), visited);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("读取 Flow 配置字段失败: " + field.getName(), e);
            }
        }
        builder.append("};");
    }

    private static boolean isSimpleValue(Class<?> type) {
        return type.isPrimitive()
                || Number.class.isAssignableFrom(type)
                || CharSequence.class.isAssignableFrom(type)
                || Boolean.class == type
                || Character.class == type
                || Enum.class.isAssignableFrom(type);
    }

    public void shutdownAll() {
        log.info("系统关闭：正在注销所有任务...");
        try {
            stopAll(true);
        } catch (Exception e) {
            log.error("停止所有任务时发生异常", e);
        } finally {
            delayedUnregisterExecutor.shutdownNow();
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
        Long generation = jobGenerations.get(jobId);
        unregister(jobId, generation);
    }

    private void unregister(String jobId, Long expectedGeneration) {
        if (expectedGeneration != null) {
            Long currentGeneration = jobGenerations.get(jobId);
            if (!expectedGeneration.equals(currentGeneration)) {
                log.debug("Skip unregister for stale generation, jobId={}, expectedGeneration={}, currentGeneration={}",
                        jobId, expectedGeneration, currentGeneration);
                return;
            }
        }
        ProgressTracker tracker = getProgressTracker(jobId);
        // 1. 移除 Gauge 指标（按内部 jobId）
        FlowResourceMetrics.unregisterPerJob(jobId, meterRegistry);
        // 2–7. 移除带展示名标签的 Counter/Timer 等
        if (tracker != null) {
            removeMetricsForJob(jobId, tracker.getMetricJobId(), tracker.getStageDisplayName());
        }
        jobIdToDisplayName.remove(jobId);
        FlowLauncher<?> launcher = activeLaunchers.remove(jobId);
        if (expectedGeneration != null) {
            jobGenerations.remove(jobId, expectedGeneration);
        } else {
            jobGenerations.remove(jobId);
        }
        if (launcher != null) {
            completedTrackers.put(jobId, launcher.getTracker());
            launcher.releaseRuntimeResources();
            log.info("Job [{}] 已从管理器中注销", FlowLogHelper.formatJobContext(jobId, launcher.getMetricJobId()));
        }
    }

    /**
     * 移除指定内部 Job 对应的全部 per-job 指标（不含 {@link FlowResourceMetrics} 注册的 Gauges），
     * 用于展示名变更时清理旧序列或完整注销流程中的 Counter/Timer 部分。
     */
    public void removeMetricsForJob(String internalJobId, String metricJobId, String stageDisplayName) {
        if (internalJobId == null || internalJobId.isEmpty()) {
            return;
        }
        Tags tags = FlowMetricTags.resolve(internalJobId, metricJobId, stageDisplayName).toTags();
        removeJobMetrics(meterRegistry, "app.template.flow.production_acquired", tags);
        removeJobMetrics(meterRegistry, "app.template.flow.production_released", tags);
        removeJobMetrics(meterRegistry, "app.template.flow.terminated", tags);
        removeJobMetrics(meterRegistry, "app.template.flow.completion.source_finished", tags);
        removeJobMetrics(meterRegistry, "app.template.flow.completion.in_flight_push", tags);
        removeJobMetrics(meterRegistry, "app.template.flow.completion.active_consumers", tags);
        removeJobMetrics(meterRegistry, "app.template.flow.limits.storage.used", tags);
        removeJobMetrics(meterRegistry, "app.template.flow.limits.storage.limit", tags);
        removeJobMetrics(meterRegistry, "app.template.flow.deposit.duration", tags);
        removeJobMetrics(meterRegistry, "app.template.flow.finalize.duration", tags);
        removeJobMetrics(meterRegistry, "app.template.flow.egress.active_workers", tags);
        removeJobMetrics(meterRegistry, "app.template.flow.egress.worker_limit", tags);
        removeJobMetrics(meterRegistry, "app.template.flow.match.duration", tags);
        removeJobMetrics(meterRegistry, "app.template.flow.limits.acquire.duration", tags);
        removeJobMetrics(meterRegistry, "backpressure.dimension.acquire.attempts.per_job", tags);
        removeJobMetrics(meterRegistry, "backpressure.dimension.acquire.blocked.per_job", tags);
        removeJobMetrics(meterRegistry, "backpressure.dimension.acquire.timeout.per_job", tags);
        removeJobMetrics(meterRegistry, "backpressure.dimension.acquire.duration.per_job", tags);
        removeJobMetrics(meterRegistry, "backpressure.dimension.release.count.per_job", tags);
        removeJobMetrics(meterRegistry, "backpressure.manager.acquire.success.per_job", tags);
        removeJobMetrics(meterRegistry, "backpressure.manager.acquire.failed.per_job", tags);
        removeJobMetrics(meterRegistry, "backpressure.manager.lease.active.per_job", tags);
        removeJobMetrics(meterRegistry, "backpressure.manager.release.idempotent_hit.per_job", tags);
        removeJobMetrics(meterRegistry, "backpressure.manager.release.leak_detected.per_job", tags);
    }

    /**
     * 移除指定 jobId 的所有指标
     */
    private void removeJobMetrics(MeterRegistry registry, String metricName, Tags tags) {
        registry.find(metricName)
                .tags(tags)
                .meters()
                .forEach(registry::remove);
    }

    /**
     * 延迟执行 unregister，使 Prometheus 有机会抓取 Job 完成时的最终指标（storage=0、activeConsumers=0 等），
     * 避免 Grafana 因指标立即移除而显示过期值（如 storage 仍为运行中的 22K）。
     */
    public void scheduleUnregister(String jobId) {
        Long expectedGeneration = jobGenerations.get(jobId);
        if (expectedGeneration == null) {
            return;
        }
        delayedUnregisterExecutor.schedule(() -> {
            try {
                unregister(jobId, expectedGeneration);
            } catch (Exception e) {
                log.warn("延迟注销 Job [{}] 时发生异常", jobId, e);
            }
        }, UNREGISTER_DELAY_SECONDS, TimeUnit.SECONDS);
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
            FlowExceptionHelper.clearMeterRegistry();
            instanceRef.set(null);
            lastConfigFingerprint = null;
        }
    }

    public <T> FlowLauncher<T> createLauncher(String jobId,
        FlowJoiner<T> flowJoiner,
        ProgressTracker tracker,
        TemplateConfigProperties.Flow flowConfig) {
        return createLauncher(jobId, null, flowJoiner, tracker, flowConfig, null);
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
        return createLauncher(jobId, displayName, flowJoiner, tracker, flowConfig, null);
    }

    public <T> FlowLauncher<T> createLauncher(String jobId,
        String displayName,
        FlowJoiner<T> flowJoiner,
        ProgressTracker tracker,
        TemplateConfigProperties.Flow flowConfig,
        FlowConsumeExecutionMode consumeExecutionMode) {
        try {
            synchronized (activeLaunchers) {
                FlowLauncher<?> existing = activeLaunchers.get(jobId);
                if (existing != null && !existing.isStopped() && !existing.isCompleted()) {
                    throw new IllegalStateException(
                        "Job " + jobId + " 未结束，不能重复创建。请先对该 job 执行 stop 后再启动新任务。");
                }
                if (existing != null) {
                    unregister(jobId, jobGenerations.get(jobId));
                }
                completedTrackers.remove(jobId);

                if (displayName != null && !displayName.isEmpty()) {
                    jobIdToDisplayName.put(jobId, displayName);
                }
                String metricJobId = resolveMetricJobId(jobId);
                tracker.setMetricJobId(metricJobId);
                terminalMetrics.markStageRunning(jobId,
                        metricJobId,
                        tracker.getStageDisplayName(),
                        System.currentTimeMillis());
                terminalMetrics.markJobRunning(extractRootJobId(jobId), extractDisplayName(jobId, metricJobId),
                        System.currentTimeMillis());

                FlowLauncher<T> launcher =
                    buildLauncher(jobId, metricJobId, flowJoiner, tracker, flowConfig, consumeExecutionMode);
                jobGenerations.put(jobId, nextGeneration.incrementAndGet());
                activeLaunchers.put(jobId, (FlowLauncher<Object>) launcher);
                resourceRegistry.registerJob(jobId);
                FlowResourceMetrics.registerPerJob(launcher, meterRegistry);
                return launcher;
            }
        } catch (Exception e) {
            FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.PRODUCTION, "create_launcher_failed");
            throw e;
        }
    }

    <T> FlowLauncher<T> buildLauncher(String jobId,
        String metricJobId,
        FlowJoiner<T> flowJoiner,
        ProgressTracker tracker,
        TemplateConfigProperties.Flow flowConfig) {
        return buildLauncher(jobId, metricJobId, flowJoiner, tracker, flowConfig, null);
    }

    <T> FlowLauncher<T> buildLauncher(String jobId,
        String metricJobId,
        FlowJoiner<T> flowJoiner,
        ProgressTracker tracker,
        TemplateConfigProperties.Flow flowConfig,
        FlowConsumeExecutionMode consumeExecutionMode) {
        return FlowLauncherFactory.create(this, jobId, metricJobId, flowJoiner, tracker, flowConfig,
                consumeExecutionMode);
    }

    /** 解析用于指标标签的 jobId：Tracker 当前展示名优先，其次显式注册名，最后为内部 jobId */
    private String resolveMetricJobId(String jobId) {
        ProgressTracker tracker = getProgressTracker(jobId);
        if (tracker != null) {
            String m = tracker.getMetricJobId();
            if (m != null && !m.isEmpty()) {
                return m;
            }
        }
        String explicit = jobIdToDisplayName.get(jobId);
        return explicit != null ? explicit : jobId;
    }

    public boolean isStopped(String jobId) {
        FlowLauncher<?> launcher = activeLaunchers.get(jobId);
        return launcher == null || launcher.isStopped() || launcher.isCompleted();
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
        Map<String, FlowLauncher<Object>> activeOnly = new java.util.LinkedHashMap<>();
        activeLaunchers.forEach((jobId, launcher) -> {
            if (!launcher.isStopped() && !launcher.isCompleted()) {
                activeOnly.put(jobId, launcher);
            }
        });
        return Collections.unmodifiableMap(activeOnly);
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
        return tracker != null && tracker.isCompleted(false);
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
        return Math.max(1, getActiveLaunchers().size());
    }

    public Map<String, Object> getHealthStatus() {
        return FlowHealth.getHealthDetails();
    }

    public HealthStatus checkHealth() {
        return FlowHealth.checkHealth();
    }

    long currentGeneration(String jobId) {
        return jobGenerations.getOrDefault(jobId, -1L);
    }

    boolean unregisterIfGenerationMatches(String jobId, long expectedGeneration) {
        Long currentGeneration = jobGenerations.get(jobId);
        if (currentGeneration == null || currentGeneration.longValue() != expectedGeneration) {
            return false;
        }
        unregister(jobId, expectedGeneration);
        return true;
    }

    public void markStageTerminal(String internalJobId,
                                  String metricJobId,
                                  String stageDisplayName,
                                  long startTimeMillis,
                                  long endTimeMillis) {
        terminalMetrics.markStageTerminal(internalJobId, metricJobId, stageDisplayName, startTimeMillis, endTimeMillis);
    }

    public void markRootTerminal(String rootJobId,
                                 String displayName,
                                 long startTimeMillis,
                                 long endTimeMillis) {
        terminalMetrics.markJobTerminal(rootJobId, displayName, startTimeMillis, endTimeMillis);
    }

    private String extractRootJobId(String internalJobId) {
        int idx = internalJobId.indexOf(':');
        return idx >= 0 ? internalJobId.substring(0, idx) : internalJobId;
    }

    private String extractDisplayName(String internalJobId, String metricJobId) {
        String rootJobId = extractRootJobId(internalJobId);
        String suffix = internalJobId.startsWith(rootJobId) ? internalJobId.substring(rootJobId.length()) : "";
        if (!suffix.isEmpty() && metricJobId != null && metricJobId.endsWith(suffix)) {
            return metricJobId.substring(0, metricJobId.length() - suffix.length());
        }
        return metricJobId;
    }
}
