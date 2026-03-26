package com.lrenyi.template.flow.backpressure;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import com.lrenyi.template.flow.backpressure.dimension.ConsumerConcurrencyDimension;
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
 *   <li>Micrometer 的 {@code jobId} 标签始终通过 {@link DimensionContext#getMetricJobIdForTags()} 解析，与 {@link com.lrenyi.template.flow.api.ProgressTracker} 一致</li>
 * </ul>
 */
@Slf4j
public class BackpressureManager {

    private static final BooleanSupplier NEVER_STOP = () -> false;

    private final String jobId;
    private final DimensionContext baseCtx;
    private final Map<String, ResourceBackpressureDimension> dimensionMap;
    private final ConcurrentHashMap<String, DimensionLease> activeLeases = new ConcurrentHashMap<>();
    private final AtomicInteger activeLeasesGaugeGlobal = new AtomicInteger(0);
    private final AtomicInteger activeLeasesGaugePerJob = new AtomicInteger(0);
    private final AtomicLong leaseIdSeq = new AtomicLong(0);
    /** 与当前已注册的租约活跃 Gauge 的 jobId 标签一致；展示名变更后由 {@link #onMetricJobIdChanged()} 置空以触发重绑 */
    private volatile String leaseGaugeRegisteredTag;
    private final boolean metricsEnabled;

    public BackpressureManager(DimensionContext baseCtx, MeterRegistry meterRegistry) {
        Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.jobId = baseCtx.getJobId();
        this.baseCtx = baseCtx;
        this.dimensionMap = loadDimensions();
        this.metricsEnabled = hasAnyDimensionWithLimitsEnabled();
    }

    /**
     * 当 {@link com.lrenyi.template.flow.api.ProgressTracker#setMetricJobId(String)} 变更展示名时调用，使租约 Gauge 在下次 acquire 时按新标签重新注册。
     */
    public void onMetricJobIdChanged() {
        leaseGaugeRegisteredTag = null;
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
                    counter(BackpressureMetricNames.MANAGER_ACQUIRE_FAILED_GLOBAL).increment();
                } else if (e.getResult() == PermitPair.AcquireResult.FAILED_ON_PER_JOB) {
                    counter(BackpressureMetricNames.MANAGER_ACQUIRE_FAILED_PER_JOB).increment();
                } else {
                    counter(BackpressureMetricNames.MANAGER_ACQUIRE_FAILED_OTHER).increment();
                }
            }
            throw e;
        } catch (InterruptedException | TimeoutException e) {
            if (metricsEnabled) {
                counter(BackpressureMetricNames.MANAGER_ACQUIRE_FAILED_OTHER).increment();
            }
            throw e;
        } catch (Exception e) {
            if (metricsEnabled) {
                counter(BackpressureMetricNames.MANAGER_ACQUIRE_FAILED_OTHER).increment();
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
            ensureLeaseGauges();
            if (pair.hasGlobal()) {
                activeLeasesGaugeGlobal.incrementAndGet();
                counter(BackpressureMetricNames.MANAGER_ACQUIRE_SUCCESS_GLOBAL).increment();
            }
            if (pair.hasPerJob()) {
                activeLeasesGaugePerJob.incrementAndGet();
                counter(BackpressureMetricNames.MANAGER_ACQUIRE_SUCCESS_PER_JOB).increment();
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
                    counter(BackpressureMetricNames.MANAGER_RELEASE_IDEMPOTENT_HIT_GLOBAL).increment();
                }
                if (pair.hasPerJob()) {
                    counter(BackpressureMetricNames.MANAGER_RELEASE_IDEMPOTENT_HIT_PER_JOB).increment();
                }
            }
        }
        if (!idempotent) {
            if (activeLeases.remove(leaseId) != null) {
                PermitPair pair = getPermitPairForDimension(dimensionId);
                if (metricsEnabled && pair != null) {
                    ensureLeaseGauges();
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
                    counter(BackpressureMetricNames.MANAGER_RELEASE_LEAK_DETECTED_GLOBAL).increment();
                }
                if (pair.hasPerJob()) {
                    counter(BackpressureMetricNames.MANAGER_RELEASE_LEAK_DETECTED_PER_JOB).increment();
                }
            }
        }
        log.warn("BackpressureManager: lease leak detected, leaseId={}, dimensionId={}, {}",
                 leaseId,
                 dimensionId,
                 FlowLogHelper.formatJobContext(jobId, baseCtx.getMetricJobIdForTags())
        );
        if (activeLeases.remove(leaseId) != null) {
            PermitPair pair = getPermitPairForDimension(dimensionId);
            if (metricsEnabled && pair != null) {
                ensureLeaseGauges();
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
            case ConsumerConcurrencyDimension.ID -> flow.getConsumerAcquireTimeoutMill();
            default -> flow.getProducerBackpressureTimeoutMill();
        };
    }

    private DimensionContext buildContext(String dimensionId, BooleanSupplier stopCheck, int permits) {
        return DimensionContext.builder()
                               .jobId(baseCtx.getJobId())
                               .metricJobId(baseCtx.getMetricJobId())
                               .progressTracker(baseCtx.getProgressTracker())
                               .dimensionId(dimensionId)
                               .permits(permits)
                               .stopCheck(stopCheck)
                               .meterRegistry(baseCtx.getMeterRegistry())
                               .flowConfig(baseCtx.getFlowConfig())
                               .resourceRegistry(baseCtx.getResourceRegistry())
                               .inFlightPermitPair(baseCtx.getInFlightPermitPair())
                               .producerPermitPair(baseCtx.getProducerPermitPair())
                               .consumerPermitPair(baseCtx.getConsumerPermitPair())
                               .storagePermitPair(baseCtx.getStoragePermitPair())
                               .globalConsumerLimit(baseCtx.getGlobalConsumerLimit())
                               .build();
    }

    private Counter counter(String name) {
        return Counter.builder(name)
                      .tag(BackpressureMetricNames.TAG_JOB_ID, baseCtx.getMetricJobIdForTags())
                      .register(baseCtx.getMeterRegistry());
    }

    private void ensureLeaseGauges() {
        if (!metricsEnabled) {
            return;
        }
        MeterRegistry reg = baseCtx.getMeterRegistry();
        String tag = baseCtx.getMetricJobIdForTags();
        if (tag.equals(leaseGaugeRegisteredTag)) {
            return;
        }
        if (leaseGaugeRegisteredTag != null) {
            removeMetersWithTag(reg, BackpressureMetricNames.MANAGER_LEASE_ACTIVE_GLOBAL, leaseGaugeRegisteredTag);
            removeMetersWithTag(reg, BackpressureMetricNames.MANAGER_LEASE_ACTIVE_PER_JOB, leaseGaugeRegisteredTag);
        }
        Gauge.builder(BackpressureMetricNames.MANAGER_LEASE_ACTIVE_GLOBAL,
                      activeLeasesGaugeGlobal,
                      AtomicInteger::get
        ).tag(BackpressureMetricNames.TAG_JOB_ID, tag).register(reg);
        Gauge.builder(BackpressureMetricNames.MANAGER_LEASE_ACTIVE_PER_JOB,
                      activeLeasesGaugePerJob,
                      AtomicInteger::get
        ).tag(BackpressureMetricNames.TAG_JOB_ID, tag).register(reg);
        leaseGaugeRegisteredTag = tag;
    }

    private static void removeMetersWithTag(MeterRegistry reg, String metricName, String tagJobId) {
        reg.find(metricName).tag(BackpressureMetricNames.TAG_JOB_ID, tagJobId).meters().forEach(reg::remove);
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
        if (!metricsEnabled) {
            return;
        }
        String tag = baseCtx.getMetricJobIdForTags();
        removeMetersWithTag(meterRegistry, BackpressureMetricNames.MANAGER_ACQUIRE_SUCCESS_GLOBAL, tag);
        removeMetersWithTag(meterRegistry, BackpressureMetricNames.MANAGER_ACQUIRE_SUCCESS_PER_JOB, tag);
        removeMetersWithTag(meterRegistry, BackpressureMetricNames.MANAGER_ACQUIRE_FAILED_GLOBAL, tag);
        removeMetersWithTag(meterRegistry, BackpressureMetricNames.MANAGER_ACQUIRE_FAILED_PER_JOB, tag);
        removeMetersWithTag(meterRegistry, BackpressureMetricNames.MANAGER_ACQUIRE_FAILED_OTHER, tag);
        removeMetersWithTag(meterRegistry, BackpressureMetricNames.MANAGER_RELEASE_IDEMPOTENT_HIT_GLOBAL, tag);
        removeMetersWithTag(meterRegistry, BackpressureMetricNames.MANAGER_RELEASE_IDEMPOTENT_HIT_PER_JOB, tag);
        removeMetersWithTag(meterRegistry, BackpressureMetricNames.MANAGER_RELEASE_LEAK_DETECTED_GLOBAL, tag);
        removeMetersWithTag(meterRegistry, BackpressureMetricNames.MANAGER_RELEASE_LEAK_DETECTED_PER_JOB, tag);
        removeMetersWithTag(meterRegistry, BackpressureMetricNames.MANAGER_LEASE_ACTIVE_GLOBAL, tag);
        removeMetersWithTag(meterRegistry, BackpressureMetricNames.MANAGER_LEASE_ACTIVE_PER_JOB, tag);
        log.debug("BackpressureManager: 已注销 Job [{}] 的所有背压指标", jobId);
    }
}
