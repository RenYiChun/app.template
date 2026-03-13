package com.lrenyi.template.flow.backpressure;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import com.lrenyi.template.flow.backpressure.dimension.ConsumerConcurrencyDimension;
import com.lrenyi.template.flow.backpressure.dimension.InFlightConsumerDimension;
import com.lrenyi.template.flow.backpressure.dimension.InFlightProductionDimension;
import com.lrenyi.template.flow.backpressure.dimension.ProducerConcurrencyDimension;
import com.lrenyi.template.flow.backpressure.dimension.StorageDimension;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import com.lrenyi.template.flow.resource.PermitPair;
import com.lrenyi.template.flow.util.FlowLogHelper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 背压管理器（每 Job 一个实例）：
 * <ul>
 *   <li>通过 Java SPI 加载 {@link ResourceBackpressureDimension} 实现</li>
 *   <li>同 ID 多个实现时，只执行 order 最小的实现</li>
 *   <li>{@link #acquire} 返回 {@link DimensionLease}，close() 触发幂等资源释放</li>
 *   <li>维护 activeLeases 注册表，支持泄露检测与在线观测</li>
 * </ul>
 */
@Slf4j
public class BackpressureManager {

    private static final BooleanSupplier NEVER_STOP = () -> false;

    private final String jobId;
    private final String metricJobId;
    private final DimensionContext baseCtx;
    private final Map<String, ResourceBackpressureDimension> dimensionMap;
    private final ConcurrentHashMap<String, DimensionLease> activeLeases = new ConcurrentHashMap<>();
    private final AtomicInteger activeLeasesGaugeGlobal = new AtomicInteger(0);
    private final AtomicInteger activeLeasesGaugePerJob = new AtomicInteger(0);
    private final AtomicLong leaseIdSeq = new AtomicLong(0);

    // Pre-registered metrics (avoid hot-path lookup); null when all dimensions have global disabled
    private final Counter acquireSuccessGlobal;
    private final Counter acquireSuccessPerJob;
    private final Counter acquireFailedGlobal;
    private final Counter acquireFailedPerJob;
    private final Counter acquireFailedOther;
    private final Counter idempotentHitGlobal;
    private final Counter idempotentHitPerJob;
    private final Counter leakDetectedGlobal;
    private final Counter leakDetectedPerJob;
    private final boolean metricsEnabled;

    public BackpressureManager(DimensionContext baseCtx, MeterRegistry meterRegistry) {
        this.jobId = baseCtx.getJobId();
        this.metricJobId = baseCtx.getMetricJobIdForTags();
        this.baseCtx = baseCtx;
        this.dimensionMap = loadDimensions();
        this.metricsEnabled = hasAnyDimensionWithLimitsEnabled();
        if (metricsEnabled) {
            this.acquireSuccessGlobal = Counter.builder(BackpressureMetricNames.MANAGER_ACQUIRE_SUCCESS_GLOBAL)
                                               .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
                                               .register(meterRegistry);
            this.acquireSuccessPerJob = Counter.builder(BackpressureMetricNames.MANAGER_ACQUIRE_SUCCESS_PER_JOB)
                                               .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
                                               .register(meterRegistry);
            this.acquireFailedGlobal = Counter.builder(BackpressureMetricNames.MANAGER_ACQUIRE_FAILED_GLOBAL)
                                              .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
                                              .register(meterRegistry);
            this.acquireFailedPerJob = Counter.builder(BackpressureMetricNames.MANAGER_ACQUIRE_FAILED_PER_JOB)
                                              .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
                                              .register(meterRegistry);
            this.acquireFailedOther = Counter.builder(BackpressureMetricNames.MANAGER_ACQUIRE_FAILED_OTHER)
                                             .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
                                             .register(meterRegistry);
            this.idempotentHitGlobal = Counter.builder(BackpressureMetricNames.MANAGER_RELEASE_IDEMPOTENT_HIT_GLOBAL)
                                              .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
                                              .register(meterRegistry);
            this.idempotentHitPerJob = Counter.builder(BackpressureMetricNames.MANAGER_RELEASE_IDEMPOTENT_HIT_PER_JOB)
                                              .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
                                              .register(meterRegistry);
            this.leakDetectedGlobal = Counter.builder(BackpressureMetricNames.MANAGER_RELEASE_LEAK_DETECTED_GLOBAL)
                                             .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
                                             .register(meterRegistry);
            this.leakDetectedPerJob = Counter.builder(BackpressureMetricNames.MANAGER_RELEASE_LEAK_DETECTED_PER_JOB)
                                             .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
                                             .register(meterRegistry);
            Gauge.builder(BackpressureMetricNames.MANAGER_LEASE_ACTIVE_GLOBAL,
                          activeLeasesGaugeGlobal,
                          AtomicInteger::get
            ).tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId).register(meterRegistry);
            Gauge.builder(BackpressureMetricNames.MANAGER_LEASE_ACTIVE_PER_JOB,
                          activeLeasesGaugePerJob,
                          AtomicInteger::get
            ).tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId).register(meterRegistry);
        } else {
            this.acquireSuccessGlobal = null;
            this.acquireSuccessPerJob = null;
            this.acquireFailedGlobal = null;
            this.acquireFailedPerJob = null;
            this.acquireFailedOther = null;
            this.idempotentHitGlobal = null;
            this.idempotentHitPerJob = null;
            this.leakDetectedGlobal = null;
            this.leakDetectedPerJob = null;
        }
    }

    private boolean hasAnyDimensionWithLimitsEnabled() {
        if (dimensionMap.isEmpty()) {
            return false;
        }
        var flow = baseCtx.getFlowConfig();
        if (flow == null) {
            return true;
        }
        var perJob = flow.getLimits().getPerJob();
        for (String dimensionId : dimensionMap.keySet()) {
            if (getGlobalLimitForDimension(dimensionId) > 0) {
                return true;
            }
            if (getPerJobLimitForDimension(dimensionId, perJob) > 0) {
                return true;
            }
        }
        return false;
    }

    private int getPerJobLimitForDimension(String dimensionId,
            com.lrenyi.template.core.TemplateConfigProperties.Flow.PerJob perJob) {
        if (perJob == null) {
            return 0;
        }
        return switch (dimensionId) {
            case InFlightProductionDimension.ID -> perJob.getInFlightProduction();
            case StorageDimension.ID -> perJob.getStorageCapacity();
            case ProducerConcurrencyDimension.ID -> perJob.getProducerThreads();
            case InFlightConsumerDimension.ID -> perJob.getInFlightConsumer() > 0
                    ? perJob.getInFlightConsumer()
                    : perJob.getConsumerThreads();
            case ConsumerConcurrencyDimension.ID -> perJob.getConsumerThreads();
            default -> 0;
        };
    }

    /**
     * 申请指定维度的资源，返回幂等 AutoCloseable 租约。等价于 {@link #acquire(String, BooleanSupplier, int) acquire(dimensionId, stopCheck, 1)}。
     */
    public DimensionLease acquire(String dimensionId,
            BooleanSupplier stopCheck) throws InterruptedException, TimeoutException {
        return acquire(dimensionId, stopCheck, 1);
    }

    /**
     * 申请指定维度的资源，返回幂等 AutoCloseable 租约。
     *
     * <p>路由规则：按 dimensionId 选取唯一 SPI 实现（同 ID 多实现时取最小 order）。
     * 如未找到注册实现，返回 noop 租约（不计入 activeLeases）。
     *
     * @param dimensionId 维度 ID
     * @param stopCheck   停止检查（null 表示永不停止）
     * @param permits    申请数量（通常为 1，消费配对等场景可为 2 或更多）
     * @return 资源租约；业务处理结束后调用 close() 释放
     * @throws InterruptedException 等待期间被中断
     * @throws TimeoutException     超过配置超时时间
     */
    public DimensionLease acquire(String dimensionId,
            BooleanSupplier stopCheck,
            int permits) throws InterruptedException, TimeoutException {
        if (permits <= 0) {
            return DimensionLease.noop(dimensionId);
        }
        ResourceBackpressureDimension dim = dimensionMap.get(dimensionId);
        if (dim == null) {
            log.debug("No dimension registered for id={}, returning noop lease, jobId={}", dimensionId, jobId);
            return DimensionLease.noop(dimensionId);
        }

        DimensionContext ctx = buildContext(dimensionId, stopCheck != null ? stopCheck : NEVER_STOP, permits);
        FlowResourceRegistry registry = baseCtx.getResourceRegistry();
        if (registry != null) {
            int globalLimit = getGlobalLimitForDimension(dimensionId);
            if (globalLimit > 0) {
                long timeoutMs = getFairShareTimeoutMs(dimensionId);
                registry.awaitFairShare(dimensionId, globalLimit, () -> getHoldingCount(dimensionId),
                        timeoutMs, stopCheck != null ? stopCheck : NEVER_STOP);
            }
        }
        try {
            dim.acquire(ctx, permits);
        } catch (BackpressureTimeoutException e) {
            if (metricsEnabled) {
                if (e.getResult() == PermitPair.AcquireResult.FAILED_ON_GLOBAL) {
                    acquireFailedGlobal.increment();
                } else if (e.getResult() == PermitPair.AcquireResult.FAILED_ON_PER_JOB) {
                    acquireFailedPerJob.increment();
                } else {
                    acquireFailedOther.increment();
                }
            }
            throw e;
        } catch (InterruptedException | TimeoutException e) {
            if (metricsEnabled) {
                acquireFailedOther.increment();
            }
            throw e;
        } catch (Exception e) {
            if (metricsEnabled) {
                acquireFailedOther.increment();
            }
            throw new RuntimeException("Backpressure acquire failed: dimensionId=" + dimensionId + ", jobId=" + jobId,
                                       e
            );
        }
        String leaseId = jobId + ":" + dimensionId + ":" + leaseIdSeq.incrementAndGet();
        DefaultDimensionLease lease = new DefaultDimensionLease(leaseId, dimensionId, permits, dim, ctx, this);
        activeLeases.put(leaseId, lease);
        PermitPair pair = getPermitPairForDimension(dimensionId);
        if (metricsEnabled && pair != null) {
            if (pair.hasGlobal()) {
                activeLeasesGaugeGlobal.incrementAndGet();
                acquireSuccessGlobal.increment();
            }
            if (pair.hasPerJob()) {
                activeLeasesGaugePerJob.incrementAndGet();
                acquireSuccessPerJob.increment();
            }
        }
        return lease;
    }

    /** 当前活跃租约数（健康检查/诊断用）。 */
    public int getActiveLeasesCount() {
        return activeLeases.size();
    }

    /** 本 Job 在指定维度的当前持有 permit 总数（用于动态 fair share 检查）。 */
    int getHoldingCount(String dimensionId) {
        return activeLeases.values().stream()
                .filter(l -> dimensionId.equals(l.dimensionId()))
                .mapToInt(DimensionLease::permits)
                .sum();
    }

    /** Called by {@link DefaultDimensionLease#close()} */
    void onLeaseClose(String leaseId, String dimensionId, boolean idempotent) {
        if (idempotent && metricsEnabled) {
            PermitPair pair = getPermitPairForDimension(dimensionId);
            if (pair != null) {
                if (pair.hasGlobal()) {
                    idempotentHitGlobal.increment();
                }
                if (pair.hasPerJob()) {
                    idempotentHitPerJob.increment();
                }
            }
        }
        if (!idempotent) {
            if (activeLeases.remove(leaseId) != null) {
                PermitPair pair = getPermitPairForDimension(dimensionId);
                if (metricsEnabled && pair != null) {
                    if (pair.hasGlobal()) {
                        activeLeasesGaugeGlobal.decrementAndGet();
                    }
                    if (pair.hasPerJob()) {
                        activeLeasesGaugePerJob.decrementAndGet();
                    }
                }
                FlowResourceRegistry registry = baseCtx.getResourceRegistry();
                if (registry != null) {
                    registry.signalFairShare(dimensionId);
                }
            }
        }
    }

    /** Called by {@link DefaultDimensionLease.LeakGuard} when lease is GC'd without close() */
    void onLeakDetected(String leaseId, String dimensionId) {
        if (metricsEnabled) {
            PermitPair pair = getPermitPairForDimension(dimensionId);
            if (pair != null) {
                if (pair.hasGlobal()) {
                    leakDetectedGlobal.increment();
                }
                if (pair.hasPerJob()) {
                    leakDetectedPerJob.increment();
                }
            }
        }
        log.warn("BackpressureManager: lease leak detected, leaseId={}, dimensionId={}, {}",
                 leaseId,
                 dimensionId,
                 FlowLogHelper.formatJobContext(jobId, metricJobId)
        );
        if (activeLeases.remove(leaseId) != null) {
            PermitPair pair = getPermitPairForDimension(dimensionId);
            if (metricsEnabled && pair != null) {
                if (pair.hasGlobal()) {
                    activeLeasesGaugeGlobal.decrementAndGet();
                }
                if (pair.hasPerJob()) {
                    activeLeasesGaugePerJob.decrementAndGet();
                }
            }
        }
    }

    private PermitPair getPermitPairForDimension(String dimensionId) {
        return switch (dimensionId) {
            case InFlightProductionDimension.ID -> baseCtx.getInFlightPermitPair();
            case StorageDimension.ID -> baseCtx.getStoragePermitPair();
            case ProducerConcurrencyDimension.ID -> baseCtx.getProducerPermitPair();
            case InFlightConsumerDimension.ID -> baseCtx.getInFlightConsumerPermitPair();
            case ConsumerConcurrencyDimension.ID -> baseCtx.getConsumerPermitPair();
            default -> null;
        };
    }

    private int getGlobalLimitForDimension(String dimensionId) {
        var global = baseCtx.getFlowConfig() != null ? baseCtx.getFlowConfig().getLimits().getGlobal() : null;
        if (global == null) {
            return 0;
        }
        return switch (dimensionId) {
            case InFlightProductionDimension.ID -> global.getInFlightProduction();
            case StorageDimension.ID -> global.getStorageCapacity();
            case ProducerConcurrencyDimension.ID -> global.getProducerThreads();
            case InFlightConsumerDimension.ID -> global.getInFlightConsumer();
            case ConsumerConcurrencyDimension.ID -> global.getConsumerThreads();
            default -> 0;
        };
    }

    private long getFairShareTimeoutMs(String dimensionId) {
        var flow = baseCtx.getFlowConfig();
        if (flow == null) {
            return 30_000L;
        }
        return switch (dimensionId) {
            case ConsumerConcurrencyDimension.ID, InFlightConsumerDimension.ID ->
                    flow.getConsumerAcquireTimeoutMill();
            default -> flow.getProducerBackpressureTimeoutMill();
        };
    }

    private DimensionContext buildContext(String dimensionId, BooleanSupplier stopCheck, int permits) {
        return DimensionContext.builder()
                               .jobId(baseCtx.getJobId())
                               .metricJobId(baseCtx.getMetricJobId())
                               .dimensionId(dimensionId)
                               .permits(permits)
                               .stopCheck(stopCheck)
                               .meterRegistry(baseCtx.getMeterRegistry())
                               .flowConfig(baseCtx.getFlowConfig())
                               .resourceRegistry(baseCtx.getResourceRegistry())
                               .inFlightPermitPair(baseCtx.getInFlightPermitPair())
                               .producerPermitPair(baseCtx.getProducerPermitPair())
                               .consumerPermitPair(baseCtx.getConsumerPermitPair())
                               .inFlightConsumerPermitPair(baseCtx.getInFlightConsumerPermitPair())
                               .storagePermitPair(baseCtx.getStoragePermitPair())
                               .globalConsumerLimit(baseCtx.getGlobalConsumerLimit())
                               .build();
    }

    private static Map<String, ResourceBackpressureDimension> loadDimensions() {
        Iterable<ResourceBackpressureDimension> loaded = ServiceLoader.load(ResourceBackpressureDimension.class);
        Map<String, List<ResourceBackpressureDimension>> byId = StreamSupport.stream(loaded.spliterator(), false)
                                                                             .collect(Collectors.groupingBy(
                                                                                     ResourceBackpressureDimension::id));
        Map<String, ResourceBackpressureDimension> result = new HashMap<>();
        byId.forEach((id, impls) -> {
            ResourceBackpressureDimension best =
                    impls.stream().min(Comparator.comparingInt(ResourceBackpressureDimension::order)).orElseThrow();
            result.put(id, best);
            if (impls.size() > 1) {
                log.info("BackpressureManager: dimension id={} has {} implementations, selected order={} ({})",
                         id,
                         impls.size(),
                         best.order(),
                         best.getClass().getName()
                );
            }
        });
        if (result.isEmpty()) {
            log.debug("BackpressureManager: no dimensions loaded via SPI");
        } else {
            log.debug("BackpressureManager: loaded dimensions={}", result.keySet());
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * 注销所有背压指标。
     * <p>
     * 在 Job 停止时调用,从 MeterRegistry 中移除所有背压相关的指标。
     *
     * @param meterRegistry 指标注册表
     */
    public void unregisterMetrics(MeterRegistry meterRegistry) {
        if (metricsEnabled) {
            meterRegistry.remove(acquireSuccessGlobal);
            meterRegistry.remove(acquireSuccessPerJob);
            meterRegistry.remove(acquireFailedGlobal);
            meterRegistry.remove(acquireFailedPerJob);
            meterRegistry.remove(acquireFailedOther);
            meterRegistry.remove(idempotentHitGlobal);
            meterRegistry.remove(idempotentHitPerJob);
            meterRegistry.remove(leakDetectedGlobal);
            meterRegistry.remove(leakDetectedPerJob);
            meterRegistry.find(BackpressureMetricNames.MANAGER_LEASE_ACTIVE_GLOBAL)
                         .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
                         .gauges()
                         .forEach(meterRegistry::remove);
            meterRegistry.find(BackpressureMetricNames.MANAGER_LEASE_ACTIVE_PER_JOB)
                         .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
                         .gauges()
                         .forEach(meterRegistry::remove);
        }
        log.debug("BackpressureManager: 已注销 Job [{}] 的所有背压指标", jobId);
    }
}
