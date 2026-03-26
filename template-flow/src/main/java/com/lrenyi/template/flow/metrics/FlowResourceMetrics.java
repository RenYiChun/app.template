package com.lrenyi.template.flow.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.ToDoubleFunction;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.internal.FlowLauncher;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import com.lrenyi.template.flow.storage.FlowStorage;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

/**
 * 注册 Flow 资源与完成水位 Gauge 指标。
 * <p>
 * 当前语义下保留 storage/sourceFinished/activeConsumers/inFlightPush，删除已失去主监控价值的 in-flight/pending/producer 等水位。
 */
public final class FlowResourceMetrics {

    private static final ConcurrentHashMap<String, List<Meter>> PER_JOB_METERS = new ConcurrentHashMap<>();

    private FlowResourceMetrics() {
    }

    /**
     * 在 FlowManager 初始化时调用，注册全局资源 limit/used Gauges（来自 limits.global）。
     */
    public static void register(FlowManager flowManager, MeterRegistry meterRegistry) {
        FlowResourceRegistry registry = flowManager.getResourceRegistry();
        TemplateConfigProperties.Flow.Global global = flowManager.getGlobalConfig().getLimits().getGlobal();

        // 全局：存储容量（limits.global.storageCapacity）
        Gauge.builder(FlowMetricNames.RESOURCES_STORAGE_LIMIT, registry, r -> global.getStorageCapacity())
             .description("存储容量限制上限（全局配置）")
             .register(meterRegistry);
        Gauge.builder(FlowMetricNames.RESOURCES_STORAGE_USED,
                      registry,
                      r -> globalUsed(r.getGlobalStorageSemaphore(),
                                      global.getStorageCapacity(),
                                      flowManager,
                                      FlowResourceMetrics::getStorageUsed
                      )
        ).description("存储容量当前使用（全局）").register(meterRegistry);

        // 全局：Sink 终端并发（limits.global.sinkConsumerThreads）
        Gauge.builder(FlowMetricNames.RESOURCES_SINK_CONCURRENCY_LIMIT, registry, r -> global.getSinkConsumerThreads())
             .description("Sink 终端全局并发限制上限（全局配置）")
             .register(meterRegistry);
        Gauge.builder(FlowMetricNames.RESOURCES_SINK_CONCURRENCY_USED,
                      registry,
                      r -> globalUsed(r.getGlobalSinkSemaphore(),
                                      global.getSinkConsumerThreads(),
                                      flowManager,
                                      unused -> 0D
                      )
        ).description("Sink 终端全局并发当前占用").register(meterRegistry);
    }

    /**
     * 全局 used：当 limit>0 且 semaphore 存在时，返回 limit - availablePermits；否则返回各 Job 使用量之和。
     */
    private static double globalUsed(Semaphore semaphore,
            int limit,
            FlowManager flowManager,
            ToDoubleFunction<FlowLauncher<?>> perJobExtractor) {
        if (limit > 0 && semaphore != null) {
            return Math.max(0, limit - semaphore.availablePermits());
        }
        return sumOverLaunchers(flowManager, perJobExtractor);
    }

    /**
     * Job 创建时调用，注册该 Job 的资源 limit/used Gauges（带 jobId 标签）。
     */
    public static void registerPerJob(FlowLauncher<?> launcher, MeterRegistry meterRegistry) {
        FlowMetricTags metricTags = FlowMetricTags.resolve(launcher.getJobId(), launcher.getMetricJobId());
        Tags tags = metricTags.toTags();
        List<Meter> meters = new ArrayList<>(6);
        meters.add(Gauge.builder(FlowMetricNames.RESOURCES_PER_JOB_STORAGE_LIMIT,
                                 launcher,
                                 FlowResourceMetrics::getStorageLimit
        ).tags(tags).description("存储容量限制上限（per-job）").register(meterRegistry));
        meters.add(Gauge.builder(FlowMetricNames.RESOURCES_PER_JOB_STORAGE_USED,
                                 launcher,
                                 FlowResourceMetrics::getStorageUsed
                        ).tags(tags).description("存储容量当前使用（per-job）")
                        .register(meterRegistry));
        meters.add(Gauge.builder(FlowMetricNames.RESOURCES_PER_JOB_ACTIVE_CONSUMERS_LIMIT,
                                 launcher,
                                 FlowResourceMetrics::getActiveConsumersLimit
                        ).tags(tags).description("活跃消费数上限（per-job）")
                        .register(meterRegistry));
        meters.add(Gauge.builder(FlowMetricNames.COMPLETION_SOURCE_FINISHED,
                                 launcher,
                                 FlowResourceMetrics::getCompletionSourceFinished
        ).tags(tags).description("Source 是否已读完（0/1），用于完成判定").register(meterRegistry));
        meters.add(Gauge.builder(FlowMetricNames.COMPLETION_IN_FLIGHT_PUSH,
                                 launcher,
                                 FlowResourceMetrics::getCompletionInFlightPush
        ).tags(tags).description("推送模式下 in-flight push 数量，用于完成判定").register(meterRegistry));
        meters.add(Gauge.builder(FlowMetricNames.COMPLETION_ACTIVE_CONSUMERS,
                                 launcher,
                                 FlowResourceMetrics::getCompletionActiveConsumers
        ).tags(tags).description("活跃消费数（ProgressTracker 的 activeConsumers），用于完成判定")
         .register(meterRegistry));
        PER_JOB_METERS.put(launcher.getJobId(), meters);
    }

    /**
     * Job 注销时调用，移除该 Job 的资源 Gauges。
     */
    public static void unregisterPerJob(String jobId, MeterRegistry meterRegistry) {
        List<Meter> meters = PER_JOB_METERS.remove(jobId);
        if (meters != null) {
            for (Meter m : meters) {
                meterRegistry.remove(m);
            }
        }
    }

    /**
     * 展示名（jobId 标签）变更时：按内部 jobId 移除旧 Gauges 后，用当前 {@link FlowLauncher#getMetricJobId()} 重新注册。
     */
    public static void reregisterPerJob(FlowLauncher<?> launcher, MeterRegistry meterRegistry) {
        unregisterPerJob(launcher.getJobId(), meterRegistry);
        registerPerJob(launcher, meterRegistry);
    }

    private static double sumOverLaunchers(FlowManager fm, ToDoubleFunction<FlowLauncher<?>> extractor) {
        return fm.getActiveLaunchers().values().stream().mapToDouble(extractor).sum();
    }

    private static double getStorageLimit(FlowLauncher<?> l) {
        FlowStorage<?> storage = l.getStorage();
        return storage != null ? storage.maxCacheSize() : 0;
    }

    private static double getStorageUsed(FlowLauncher<?> l) {
        FlowStorage<?> storage = l.getStorage();
        return storage != null ? storage.size() : 0;
    }

    private static double getActiveConsumersLimit(FlowLauncher<?> l) {
        return l.getFlow().getLimits().getPerJob().getConsumerThreads();
    }

    private static double getCompletionSourceFinished(FlowLauncher<?> l) {
        return l.getTracker().isSourceFinished() ? 1.0 : 0.0;
    }

    private static double getCompletionInFlightPush(FlowLauncher<?> l) {
        return l.getInFlightPushCount();
    }

    private static double getCompletionActiveConsumers(FlowLauncher<?> l) {
        return l.getTracker().getSnapshot().activeConsumers();
    }
}
