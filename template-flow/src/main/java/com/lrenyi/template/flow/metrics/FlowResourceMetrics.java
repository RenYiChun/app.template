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
import com.lrenyi.template.flow.storage.FlowStorage;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

/**
 * 注册 Flow 资源限制/使用量 Gauge 指标，供 Grafana 等监控展示。
 * <p>
 * 全局指标按 Job 聚合（sum），适用于 global 视图；per-job 指标带 jobId 标签，适用于 per-job 视图。
 */
public final class FlowResourceMetrics {
    
    private static final ConcurrentHashMap<String, List<Meter>> PER_JOB_METERS = new ConcurrentHashMap<>();
    
    private FlowResourceMetrics() {
    }
    
    /**
     * 在 FlowManager 初始化时调用，注册资源 limit/used Gauges。
     */
    public static void register(FlowManager flowManager, MeterRegistry meterRegistry) {
        Gauge.builder(FlowMetricNames.RESOURCES_IN_FLIGHT_PRODUCTION_LIMIT,
                      flowManager,
                      fm -> sumOverLaunchers(fm, FlowResourceMetrics::getInFlightProductionLimit)
        ).description("生产在途数量限制上限（配置值）").register(meterRegistry);
        Gauge.builder(FlowMetricNames.RESOURCES_IN_FLIGHT_PRODUCTION_USED,
                      flowManager,
                      fm -> sumOverLaunchers(fm, FlowResourceMetrics::getInFlightProductionUsed)
        ).description("生产在途数量当前使用").register(meterRegistry);
        
        Gauge.builder(FlowMetricNames.RESOURCES_PRODUCER_THREADS_LIMIT,
                      flowManager,
                      fm -> sumOverLaunchers(fm, FlowResourceMetrics::getProducerThreadsLimit)
        ).description("生产线程数限制上限（配置值）").register(meterRegistry);
        Gauge.builder(FlowMetricNames.RESOURCES_PRODUCER_THREADS_USED,
                      flowManager,
                      fm -> sumOverLaunchers(fm, FlowResourceMetrics::getProducerThreadsUsed)
        ).description("生产线程数当前使用").register(meterRegistry);
        
        Gauge.builder(FlowMetricNames.RESOURCES_STORAGE_LIMIT,
                      flowManager,
                      fm -> sumOverLaunchers(fm, FlowResourceMetrics::getStorageLimit)
        ).description("存储容量限制上限（配置值）").register(meterRegistry);
        Gauge.builder(FlowMetricNames.RESOURCES_STORAGE_USED,
                      flowManager,
                      fm -> sumOverLaunchers(fm, FlowResourceMetrics::getStorageUsed)
        ).description("存储容量当前使用").register(meterRegistry);
        
        Gauge.builder(FlowMetricNames.RESOURCES_IN_FLIGHT_CONSUMER_LIMIT,
                      flowManager,
                      fm -> sumOverLaunchers(fm, FlowResourceMetrics::getInFlightConsumerLimit)
        ).description("在途消费数量限制上限（配置值）").register(meterRegistry);
        Gauge.builder(FlowMetricNames.RESOURCES_IN_FLIGHT_CONSUMER_USED,
                      flowManager,
                      fm -> sumOverLaunchers(fm, FlowResourceMetrics::getInFlightConsumerUsed)
        ).description("在途消费数量当前使用").register(meterRegistry);
        
        Gauge.builder(FlowMetricNames.RESOURCES_CONSUMER_THREADS_LIMIT,
                      flowManager,
                      fm -> sumOverLaunchers(fm, FlowResourceMetrics::getConsumerThreadsLimit)
        ).description("消费线程数限制上限（配置值）").register(meterRegistry);
        Gauge.builder(FlowMetricNames.RESOURCES_CONSUMER_THREADS_USED,
                      flowManager,
                      fm -> sumOverLaunchers(fm, FlowResourceMetrics::getConsumerThreadsUsed)
        ).description("消费线程数当前使用").register(meterRegistry);
    }
    
    /**
     * Job 创建时调用，注册该 Job 的资源 limit/used Gauges（带 jobId 标签）。
     */
    public static void registerPerJob(FlowLauncher<?> launcher, MeterRegistry meterRegistry) {
        String metricJobId = launcher.getMetricJobId();
        Tags tags = Tags.of(FlowMetricNames.TAG_JOB_ID, metricJobId);
        List<Meter> meters = new ArrayList<>(10);
        meters.add(Gauge.builder(FlowMetricNames.RESOURCES_IN_FLIGHT_PRODUCTION_LIMIT,
                                 launcher,
                                 FlowResourceMetrics::getInFlightProductionLimit
        ).tags(tags).description("生产在途数量限制上限（配置值）").register(meterRegistry));
        meters.add(Gauge.builder(FlowMetricNames.RESOURCES_IN_FLIGHT_PRODUCTION_USED,
                                 launcher,
                                 FlowResourceMetrics::getInFlightProductionUsed
        ).tags(tags).description("生产在途数量当前使用").register(meterRegistry));
        meters.add(Gauge.builder(FlowMetricNames.RESOURCES_PRODUCER_THREADS_LIMIT,
                                 launcher,
                                 FlowResourceMetrics::getProducerThreadsLimit
        ).tags(tags).description("生产线程数限制上限（配置值）").register(meterRegistry));
        meters.add(Gauge.builder(FlowMetricNames.RESOURCES_PRODUCER_THREADS_USED,
                                 launcher,
                                 FlowResourceMetrics::getProducerThreadsUsed
        ).tags(tags).description("生产线程数当前使用").register(meterRegistry));
        meters.add(Gauge.builder(FlowMetricNames.RESOURCES_STORAGE_LIMIT,
                                 launcher,
                                 FlowResourceMetrics::getStorageLimit
        ).tags(tags).description("存储容量限制上限（配置值）").register(meterRegistry));
        meters.add(Gauge.builder(FlowMetricNames.RESOURCES_STORAGE_USED, launcher, FlowResourceMetrics::getStorageUsed)
                        .tags(tags)
                        .description("存储容量当前使用")
                        .register(meterRegistry));
        meters.add(Gauge.builder(FlowMetricNames.RESOURCES_IN_FLIGHT_CONSUMER_LIMIT,
                                 launcher,
                                 FlowResourceMetrics::getInFlightConsumerLimit
        ).tags(tags).description("在途消费数量限制上限（配置值）").register(meterRegistry));
        meters.add(Gauge.builder(FlowMetricNames.RESOURCES_IN_FLIGHT_CONSUMER_USED,
                                 launcher,
                                 FlowResourceMetrics::getInFlightConsumerUsed
        ).tags(tags).description("在途消费数量当前使用").register(meterRegistry));
        meters.add(Gauge.builder(FlowMetricNames.RESOURCES_CONSUMER_THREADS_LIMIT,
                                 launcher,
                                 FlowResourceMetrics::getConsumerThreadsLimit
        ).tags(tags).description("消费线程数限制上限（配置值）").register(meterRegistry));
        meters.add(Gauge.builder(FlowMetricNames.RESOURCES_CONSUMER_THREADS_USED,
                                 launcher,
                                 FlowResourceMetrics::getConsumerThreadsUsed
        ).tags(tags).description("消费线程数当前使用").register(meterRegistry));
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
}
