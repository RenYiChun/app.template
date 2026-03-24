package com.lrenyi.template.flow.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.ToDoubleFunction;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.context.FlowResourceContext;
import com.lrenyi.template.flow.internal.FlowLauncher;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import com.lrenyi.template.flow.storage.FlowStorage;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

/**
 * 注册 Flow 资源限制/使用量 Gauge 指标，供 Grafana 等监控展示。
 * <p>
 * 全局指标：来自 limits.global.* 配置及全局信号量，无 jobId 标签。
 * Per-job 指标：来自 limits.per-job.* 配置及每 Job 信号量，带 jobId 标签。
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

        // 全局：生产在途（limits.global.inFlightProduction）
        Gauge.builder(FlowMetricNames.RESOURCES_IN_FLIGHT_PRODUCTION_LIMIT,
                      registry,
                      r -> global.getInFlightProduction()
        ).description("生产在途数量限制上限（全局配置）").register(meterRegistry);
        Gauge.builder(FlowMetricNames.RESOURCES_IN_FLIGHT_PRODUCTION_USED,
                      registry,
                      r -> globalUsed(r.getGlobalInFlightSemaphore(),
                                      global.getInFlightProduction(),
                                      flowManager,
                                      FlowResourceMetrics::getInFlightProductionUsed
                      )
        ).description("生产在途数量当前使用（全局）").register(meterRegistry);

        // 全局：生产线程数（limits.global.producerThreads）
        Gauge.builder(FlowMetricNames.RESOURCES_PRODUCER_THREADS_LIMIT, registry, r -> global.getProducerThreads())
             .description("生产线程数限制上限（全局配置）")
             .register(meterRegistry);
        Gauge.builder(FlowMetricNames.RESOURCES_PRODUCER_THREADS_USED,
                      registry,
                      r -> globalUsed(r.getGlobalProducerThreadsSemaphore(),
                                      global.getProducerThreads(),
                                      flowManager,
                                      FlowResourceMetrics::getProducerThreadsUsed
                      )
        ).description("生产线程数当前使用（全局）").register(meterRegistry);

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

        // 全局：在途消费（limits.global.inFlightConsumer）
        Gauge.builder(FlowMetricNames.RESOURCES_IN_FLIGHT_CONSUMER_LIMIT, registry, r -> global.getInFlightConsumer())
             .description("在途消费数量限制上限（全局配置）")
             .register(meterRegistry);
        Gauge.builder(FlowMetricNames.RESOURCES_IN_FLIGHT_CONSUMER_USED,
                      registry,
                      r -> globalUsed(r.getGlobalInFlightConsumerSemaphore(),
                                      global.getInFlightConsumer(),
                                      flowManager,
                                      FlowResourceMetrics::getInFlightConsumerUsed
                      )
        ).description("在途消费数量当前使用（全局）").register(meterRegistry);

        // 全局：消费线程数（limits.global.consumerThreads）
        Gauge.builder(FlowMetricNames.RESOURCES_CONSUMER_THREADS_LIMIT, registry, r -> global.getConsumerThreads())
             .description("消费线程数限制上限（全局配置）")
             .register(meterRegistry);
        Gauge.builder(FlowMetricNames.RESOURCES_CONSUMER_THREADS_USED,
                      registry,
                      r -> globalUsed(r.getGlobalSemaphore(),
                                      global.getConsumerThreads(),
                                      flowManager,
                                      FlowResourceMetrics::getConsumerThreadsUsed
                      )
        ).description("消费线程数当前使用（全局）").register(meterRegistry);

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
        String metricJobId = launcher.getMetricJobId();
        String jobId = launcher.getJobId();
        String tagJobId = (metricJobId != null && !metricJobId.isEmpty()) ? metricJobId : jobId;
        Tags tags = Tags.of(FlowMetricNames.TAG_JOB_ID, tagJobId);
        List<Meter> meters = new ArrayList<>(10);
        meters.add(Gauge.builder(FlowMetricNames.RESOURCES_PER_JOB_IN_FLIGHT_PRODUCTION_LIMIT,
                                 launcher,
                                 FlowResourceMetrics::getInFlightProductionLimit
        ).tags(tags).description("生产在途数量限制上限（per-job）").register(meterRegistry));
        meters.add(Gauge.builder(FlowMetricNames.RESOURCES_PER_JOB_IN_FLIGHT_PRODUCTION_USED,
                                 launcher,
                                 FlowResourceMetrics::getInFlightProductionUsed
        ).tags(tags).description("生产在途数量当前使用（per-job）").register(meterRegistry));
        meters.add(Gauge.builder(FlowMetricNames.RESOURCES_PER_JOB_PRODUCER_THREADS_LIMIT,
                                 launcher,
                                 FlowResourceMetrics::getProducerThreadsLimit
        ).tags(tags).description("生产线程数限制上限（per-job）").register(meterRegistry));
        meters.add(Gauge.builder(FlowMetricNames.RESOURCES_PER_JOB_PRODUCER_THREADS_USED,
                                 launcher,
                                 FlowResourceMetrics::getProducerThreadsUsed
        ).tags(tags).description("生产线程数当前使用（per-job）").register(meterRegistry));
        meters.add(Gauge.builder(FlowMetricNames.RESOURCES_PER_JOB_STORAGE_LIMIT,
                                 launcher,
                                 FlowResourceMetrics::getStorageLimit
        ).tags(tags).description("存储容量限制上限（per-job）").register(meterRegistry));
        meters.add(Gauge.builder(FlowMetricNames.RESOURCES_PER_JOB_STORAGE_USED,
                                 launcher,
                                 FlowResourceMetrics::getStorageUsed
                        ).tags(tags).description("存储容量当前使用（per-job）")
                        .register(meterRegistry));
        meters.add(Gauge.builder(FlowMetricNames.RESOURCES_PER_JOB_IN_FLIGHT_CONSUMER_LIMIT,
                                 launcher,
                                 FlowResourceMetrics::getInFlightConsumerLimit
        ).tags(tags).description("在途消费数量限制上限（per-job）").register(meterRegistry));
        meters.add(Gauge.builder(FlowMetricNames.RESOURCES_PER_JOB_IN_FLIGHT_CONSUMER_USED,
                                 launcher,
                                 FlowResourceMetrics::getInFlightConsumerUsed
        ).tags(tags).description("在途消费数量当前使用（per-job）").register(meterRegistry));
        meters.add(Gauge.builder(FlowMetricNames.RESOURCES_PER_JOB_CONSUMER_THREADS_LIMIT,
                                 launcher,
                                 FlowResourceMetrics::getConsumerThreadsLimit
        ).tags(tags).description("消费线程数限制上限（per-job）").register(meterRegistry));
        meters.add(Gauge.builder(FlowMetricNames.RESOURCES_PER_JOB_CONSUMER_THREADS_USED,
                                 launcher,
                                 FlowResourceMetrics::getConsumerThreadsUsed
        ).tags(tags).description("消费线程数当前使用（per-job）").register(meterRegistry));
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
        ).tags(tags).description("活跃消费数（ProgressTracker 的 activeConsumers），用于完成判定，与 in_flight_consumer_used 不同")
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

    private static double getInFlightProductionLimit(FlowLauncher<?> l) {
        return l.getFlow().getLimits().getPerJob().getInFlightProduction();
    }

    private static double getInFlightProductionUsed(FlowLauncher<?> l) {
        FlowResourceContext ctx = l.getResourceContext();
        Semaphore s = ctx != null ? ctx.getInFlightProductionSemaphore() : null;
        if (s == null) {
            return 0;
        }
        int limit = l.getFlow().getLimits().getPerJob().getInFlightProduction();
        return Math.max(0, limit - s.availablePermits());
    }

    private static double getProducerThreadsLimit(FlowLauncher<?> l) {
        return l.getFlow().getLimits().getPerJob().getProducerThreads();
    }

    private static double getProducerThreadsUsed(FlowLauncher<?> l) {
        FlowResourceContext ctx = l.getResourceContext();
        Semaphore s = ctx != null ? ctx.getJobProducerSemaphore() : null;
        if (s == null) {
            return 0;
        }
        int limit = l.getFlow().getLimits().getPerJob().getProducerThreads();
        return Math.max(0, limit - s.availablePermits());
    }

    private static double getStorageLimit(FlowLauncher<?> l) {
        FlowStorage<?> storage = l.getStorage();
        return storage != null ? storage.maxCacheSize() : 0;
    }

    private static double getStorageUsed(FlowLauncher<?> l) {
        FlowStorage<?> storage = l.getStorage();
        return storage != null ? storage.size() : 0;
    }

    private static double getInFlightConsumerLimit(FlowLauncher<?> l) {
        TemplateConfigProperties.Flow.PerJob perJob = l.getFlow().getLimits().getPerJob();
        return perJob.getEffectivePendingConsumer();
    }

    private static double getInFlightConsumerUsed(FlowLauncher<?> l) {
        FlowResourceContext ctx = l.getResourceContext();
        Semaphore s = ctx != null ? ctx.getPendingConsumerSlotSemaphore() : null;
        if (s == null) {
            return 0;
        }
        int limit = l.getFlow().getLimits().getPerJob().getEffectivePendingConsumer();
        return Math.max(0, limit - s.availablePermits());
    }

    private static double getConsumerThreadsLimit(FlowLauncher<?> l) {
        return l.getFlow().getLimits().getPerJob().getConsumerThreads();
    }

    private static double getConsumerThreadsUsed(FlowLauncher<?> l) {
        FlowResourceContext ctx = l.getResourceContext();
        Semaphore s = ctx != null ? ctx.getJobConsumerSemaphore() : null;
        if (s == null) {
            return 0;
        }
        int limit = l.getFlow().getLimits().getPerJob().getConsumerThreads();
        return Math.max(0, limit - s.availablePermits());
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
