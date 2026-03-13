package com.lrenyi.template.flow.backpressure;

import java.lang.reflect.Field;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.backpressure.dimension.InFlightConsumerDimension;
import com.lrenyi.template.flow.backpressure.dimension.InFlightProductionDimension;
import com.lrenyi.template.flow.resource.PermitPair;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 单元测试：覆盖 BackpressureMetricNames 中集成测试未覆盖的指标。
 * <ul>
 *   <li>DIM_ACQUIRE_BLOCKED_GLOBAL / DIM_ACQUIRE_BLOCKED_PER_JOB</li>
 *   <li>DIM_ACQUIRE_TIMEOUT_GLOBAL / DIM_ACQUIRE_TIMEOUT_PER_JOB</li>
 *   <li>MANAGER_ACQUIRE_FAILED_GLOBAL / MANAGER_ACQUIRE_FAILED_PER_JOB</li>
 *   <li>MANAGER_RELEASE_IDEMPOTENT_HIT_GLOBAL / MANAGER_RELEASE_IDEMPOTENT_HIT_PER_JOB</li>
 *   <li>MANAGER_RELEASE_LEAK_DETECTED_GLOBAL / MANAGER_RELEASE_LEAK_DETECTED_PER_JOB</li>
 * </ul>
 */
class BackpressureManagerTest {

    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
    }

    private double getCounter(String metricName, String jobId) {
        var c = meterRegistry.find(metricName).tag(BackpressureMetricNames.TAG_JOB_ID, jobId).counter();
        return c == null ? 0D : c.count();
    }

    private double getCounter(String metricName, String jobId, String dimensionId) {
        var c = meterRegistry.find(metricName)
                             .tag(BackpressureMetricNames.TAG_JOB_ID, jobId)
                             .tag(BackpressureMetricNames.TAG_DIMENSION_ID, dimensionId)
                             .counter();
        return c == null ? 0D : c.count();
    }

    @Test
    void dimAcquireTimeoutAndManagerAcquireFailedWhenSemaphoreTimesOut() throws Exception {
        String jobId = "job-timeout";
        Semaphore neverAcquire = new Semaphore(0) {
            @Override
            public boolean tryAcquire(long timeout, TimeUnit unit) {
                return false;
            }

            @Override
            public boolean tryAcquire(int permits, long timeout, TimeUnit unit) {
                return false;
            }
        };
        TemplateConfigProperties.Flow flow = new TemplateConfigProperties.Flow();
        flow.getLimits().getGlobal().setInFlightConsumer(10);
        DimensionContext ctx = DimensionContext.builder()
                                              .jobId(jobId)
                                              .stopCheck(() -> false)
                                              .meterRegistry(meterRegistry)
                                              .flowConfig(flow)
                                              .inFlightConsumerPermitPair(PermitPair.of(null, neverAcquire))
                                              .build();
        BackpressureManager manager = new BackpressureManager(ctx, meterRegistry);

        assertThrows(Exception.class, () -> manager.acquire(InFlightConsumerDimension.ID, () -> false));

        assertTrue(getCounter(BackpressureMetricNames.DIM_ACQUIRE_TIMEOUT_PER_JOB, jobId,
                        InFlightConsumerDimension.ID) >= 1,
                "DIM_ACQUIRE_TIMEOUT_PER_JOB 应在 per-job 超时时增加");
        assertEquals(1D, getCounter(BackpressureMetricNames.MANAGER_ACQUIRE_FAILED_PER_JOB, jobId),
                "MANAGER_ACQUIRE_FAILED_PER_JOB 应在 per-job 超时时增加");
    }

    @Test
    void dimAcquireBlockedZeroWhenInFlightConsumerAcquiresSuccessfully() throws Exception {
        String jobId = "job-blocked";
        Semaphore available = new Semaphore(1);
        TemplateConfigProperties.Flow flow = new TemplateConfigProperties.Flow();
        flow.getLimits().getGlobal().setInFlightConsumer(10);
        DimensionContext ctx = DimensionContext.builder()
                                              .jobId(jobId)
                                              .stopCheck(() -> false)
                                              .meterRegistry(meterRegistry)
                                              .flowConfig(flow)
                                              .inFlightConsumerPermitPair(PermitPair.of(null, available))
                                              .build();
        BackpressureManager manager = new BackpressureManager(ctx, meterRegistry);

        try (DimensionLease lease = manager.acquire(InFlightConsumerDimension.ID, () -> false)) {
            assertEquals(0D, getCounter(BackpressureMetricNames.DIM_ACQUIRE_BLOCKED_GLOBAL, jobId,
                    InFlightConsumerDimension.ID), "成功 acquire 时 blocked_global 应为 0");
            assertEquals(0D, getCounter(BackpressureMetricNames.DIM_ACQUIRE_BLOCKED_PER_JOB, jobId,
                    InFlightConsumerDimension.ID), "成功 acquire 时 blocked_per_job 应为 0");
        }
    }

    @Test
    void dimAcquireBlockedAndTimeoutWhenInFlightProductionBlocksThenTimesOut() throws Exception {
        String jobId = "job-inflight-block-timeout";
        TemplateConfigProperties.Flow flow = new TemplateConfigProperties.Flow();
        flow.getLimits().getGlobal().setInFlightProduction(10);
        flow.setProducerBackpressureBlockingMode(
                TemplateConfigProperties.Flow.BackpressureBlockingMode.BLOCK_WITH_TIMEOUT);
        flow.setProducerBackpressureTimeoutMill(50);
        Semaphore neverAcquire = new Semaphore(0) {
            @Override
            public boolean tryAcquire(long timeout, TimeUnit unit) {
                return false;
            }

            @Override
            public boolean tryAcquire(int permits, long timeout, TimeUnit unit) {
                return false;
            }
        };
        DimensionContext ctx = DimensionContext.builder()
                                              .jobId(jobId)
                                              .stopCheck(() -> false)
                                              .meterRegistry(meterRegistry)
                                              .flowConfig(flow)
                                              .inFlightPermitPair(PermitPair.of(null, neverAcquire))
                                              .build();
        BackpressureManager manager = new BackpressureManager(ctx, meterRegistry);

        assertThrows(Exception.class, () -> manager.acquire(InFlightProductionDimension.ID, () -> false));

        assertTrue(getCounter(BackpressureMetricNames.DIM_ACQUIRE_BLOCKED_PER_JOB, jobId,
                        InFlightProductionDimension.ID) >= 1,
                "DIM_ACQUIRE_BLOCKED_PER_JOB 应在 per-job 阻塞后超时时增加");
        assertTrue(getCounter(BackpressureMetricNames.DIM_ACQUIRE_TIMEOUT_PER_JOB, jobId,
                        InFlightProductionDimension.ID) >= 1,
                "DIM_ACQUIRE_TIMEOUT_PER_JOB 应在 per-job 超时时增加");
    }

    @Test
    void managerReleaseIdempotentHitWhenCloseCalledTwice() throws Exception {
        String jobId = "job-idempotent";
        Semaphore available = new Semaphore(1);
        TemplateConfigProperties.Flow flow = new TemplateConfigProperties.Flow();
        flow.getLimits().getGlobal().setInFlightConsumer(10);
        DimensionContext ctx = DimensionContext.builder()
                                              .jobId(jobId)
                                              .stopCheck(() -> false)
                                              .meterRegistry(meterRegistry)
                                              .flowConfig(flow)
                                              .inFlightConsumerPermitPair(PermitPair.of(null, available))
                                              .build();
        BackpressureManager manager = new BackpressureManager(ctx, meterRegistry);

        DimensionLease lease = manager.acquire(InFlightConsumerDimension.ID, () -> false);
        lease.close();
        lease.close();

        assertEquals(1D, getCounter(BackpressureMetricNames.MANAGER_RELEASE_IDEMPOTENT_HIT_PER_JOB, jobId),
                "MANAGER_RELEASE_IDEMPOTENT_HIT_PER_JOB 应在重复 close 时增加（per-job 维度）");
    }

    @Test
    void managerReleaseLeakDetectedWhenLeaseGcdWithoutClose() throws Exception {
        String jobId = "job-leak";
        Semaphore available = new Semaphore(2);
        TemplateConfigProperties.Flow flow = new TemplateConfigProperties.Flow();
        flow.getLimits().getGlobal().setInFlightConsumer(10);
        DimensionContext ctx = DimensionContext.builder()
                                              .jobId(jobId)
                                              .stopCheck(() -> false)
                                              .meterRegistry(meterRegistry)
                                              .flowConfig(flow)
                                              .inFlightConsumerPermitPair(PermitPair.of(null, available))
                                              .build();
        BackpressureManager manager = new BackpressureManager(ctx, meterRegistry);

        DimensionLease lease = manager.acquire(InFlightConsumerDimension.ID, () -> false);
        String leaseId = lease.getLeaseId();
        removeLeaseFromActiveLeases(manager, leaseId);
        lease = null;
        for (int i = 0; i < 5; i++) {
            System.gc();
            Thread.sleep(50);
            double leak = getCounter(BackpressureMetricNames.MANAGER_RELEASE_LEAK_DETECTED_PER_JOB, jobId);
            if (leak >= 1D) {
                return;
            }
        }
        assertTrue(getCounter(BackpressureMetricNames.MANAGER_RELEASE_LEAK_DETECTED_PER_JOB, jobId) >= 1D,
                "MANAGER_RELEASE_LEAK_DETECTED_PER_JOB 应在 lease 未 close 被 GC 时增加（Cleaner 异步执行）");
    }

    private static void removeLeaseFromActiveLeases(BackpressureManager manager, String leaseId) throws Exception {
        Field field = BackpressureManager.class.getDeclaredField("activeLeases");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, ?> map = (java.util.Map<String, ?>) field.get(manager);
        map.remove(leaseId);
    }

    @Test
    void noDimensionMetricsWhenBothGlobalAndPerJobDisabled() throws Exception {
        String jobId = "job-no-limits";
        Semaphore available = new Semaphore(1);
        TemplateConfigProperties.Flow flow = new TemplateConfigProperties.Flow();
        flow.getLimits().getGlobal().setInFlightConsumer(0);
        flow.getLimits().getPerJob().setInFlightConsumer(0);
        flow.getLimits().getPerJob().setConsumerThreads(0);
        DimensionContext ctx = DimensionContext.builder()
                                              .jobId(jobId)
                                              .stopCheck(() -> false)
                                              .meterRegistry(meterRegistry)
                                              .flowConfig(flow)
                                              .inFlightConsumerPermitPair(PermitPair.of(null, available))
                                              .build();
        BackpressureManager manager = new BackpressureManager(ctx, meterRegistry);

        try (DimensionLease lease = manager.acquire(InFlightConsumerDimension.ID, () -> false)) {
            assertEquals(0D, getCounter(BackpressureMetricNames.DIM_ACQUIRE_ATTEMPTS_GLOBAL, jobId,
                    InFlightConsumerDimension.ID), "global 与 per-job 均未启用时不应注册 DIM_ACQUIRE_ATTEMPTS_GLOBAL");
            assertEquals(0D, getCounter(BackpressureMetricNames.DIM_ACQUIRE_ATTEMPTS_PER_JOB, jobId,
                    InFlightConsumerDimension.ID), "global 与 per-job 均未启用时不应注册 DIM_ACQUIRE_ATTEMPTS_PER_JOB");
            assertEquals(0D, getCounter(BackpressureMetricNames.DIM_ACQUIRE_BLOCKED_GLOBAL, jobId,
                    InFlightConsumerDimension.ID), "global 与 per-job 均未启用时不应注册 blocked_global");
            assertEquals(0D, getCounter(BackpressureMetricNames.DIM_ACQUIRE_BLOCKED_PER_JOB, jobId,
                    InFlightConsumerDimension.ID), "global 与 per-job 均未启用时不应注册 blocked_per_job");
        }
    }

    @Test
    void dimAcquireBlockedGlobalWhenGlobalSemaphoreBlocks() throws Exception {
        String jobId = "job-blocked-global";
        Semaphore globalNeverAcquire = new Semaphore(0) {
            @Override
            public boolean tryAcquire(int permits) {
                return false;
            }

            @Override
            public boolean tryAcquire(int permits, long timeout, TimeUnit unit) {
                return false;
            }
        };
        Semaphore perJobAvailable = new Semaphore(1);
        TemplateConfigProperties.Flow flow = new TemplateConfigProperties.Flow();
        flow.getLimits().getGlobal().setInFlightConsumer(10);
        flow.setConsumerAcquireBlockingMode(
                TemplateConfigProperties.Flow.BackpressureBlockingMode.BLOCK_WITH_TIMEOUT);
        flow.setConsumerAcquireTimeoutMill(50);
        DimensionContext ctx = DimensionContext.builder()
                                              .jobId(jobId)
                                              .stopCheck(() -> false)
                                              .meterRegistry(meterRegistry)
                                              .flowConfig(flow)
                                              .inFlightConsumerPermitPair(PermitPair.of(globalNeverAcquire, perJobAvailable))
                                              .build();
        BackpressureManager manager = new BackpressureManager(ctx, meterRegistry);

        assertThrows(Exception.class, () -> manager.acquire(InFlightConsumerDimension.ID, () -> false));

        assertTrue(getCounter(BackpressureMetricNames.DIM_ACQUIRE_BLOCKED_GLOBAL, jobId,
                        InFlightConsumerDimension.ID) >= 1,
                "DIM_ACQUIRE_BLOCKED_GLOBAL 应在 global 信号量阻塞时增加");
    }
}
