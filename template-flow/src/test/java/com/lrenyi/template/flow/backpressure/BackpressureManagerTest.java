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
 *   <li>DIM_ACQUIRE_BLOCKED</li>
 *   <li>DIM_ACQUIRE_TIMEOUT</li>
 *   <li>MANAGER_ACQUIRE_FAILED</li>
 *   <li>MANAGER_RELEASE_IDEMPOTENT_HIT</li>
 *   <li>MANAGER_RELEASE_LEAK_DETECTED</li>
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
        DimensionContext ctx = DimensionContext.builder()
                                              .jobId(jobId)
                                              .stopCheck(() -> false)
                                              .meterRegistry(meterRegistry)
                                              .flowConfig(new TemplateConfigProperties.Flow())
                                              .inFlightConsumerPermitPair(PermitPair.of(null, neverAcquire))
                                              .build();
        BackpressureManager manager = new BackpressureManager(ctx, meterRegistry);

        assertThrows(Exception.class, () -> manager.acquire(InFlightConsumerDimension.ID, () -> false));

        assertEquals(1D, getCounter(BackpressureMetricNames.DIM_ACQUIRE_TIMEOUT, jobId,
                InFlightConsumerDimension.ID), "DIM_ACQUIRE_TIMEOUT 应在超时时增加");
        assertEquals(1D, getCounter(BackpressureMetricNames.MANAGER_ACQUIRE_FAILED, jobId),
                "MANAGER_ACQUIRE_FAILED 应在 acquire 抛异常时增加");
    }

    @Test
    void dimAcquireBlockedWhenInFlightConsumerAcquiresSuccessfully() throws Exception {
        String jobId = "job-blocked";
        Semaphore available = new Semaphore(1);
        DimensionContext ctx = DimensionContext.builder()
                                              .jobId(jobId)
                                              .stopCheck(() -> false)
                                              .meterRegistry(meterRegistry)
                                              .flowConfig(new TemplateConfigProperties.Flow())
                                              .inFlightConsumerPermitPair(PermitPair.of(null, available))
                                              .build();
        BackpressureManager manager = new BackpressureManager(ctx, meterRegistry);

        try (DimensionLease lease = manager.acquire(InFlightConsumerDimension.ID, () -> false)) {
            assertEquals(1D, getCounter(BackpressureMetricNames.DIM_ACQUIRE_BLOCKED, jobId,
                    InFlightConsumerDimension.ID), "InFlightConsumer 成功 acquire 时 DIM_ACQUIRE_BLOCKED +1");
        }
    }

    @Test
    void dimAcquireBlockedAndTimeoutWhenInFlightProductionBlocksThenTimesOut() throws Exception {
        String jobId = "job-inflight-block-timeout";
        TemplateConfigProperties.Flow flow = new TemplateConfigProperties.Flow();
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

        assertTrue(getCounter(BackpressureMetricNames.DIM_ACQUIRE_BLOCKED, jobId,
                        InFlightProductionDimension.ID) >= 1,
                "DIM_ACQUIRE_BLOCKED 应在等待后超时时增加");
        assertTrue(getCounter(BackpressureMetricNames.DIM_ACQUIRE_TIMEOUT, jobId,
                        InFlightProductionDimension.ID) >= 1,
                "DIM_ACQUIRE_TIMEOUT 应在超时时增加");
    }

    @Test
    void managerReleaseIdempotentHitWhenCloseCalledTwice() throws Exception {
        String jobId = "job-idempotent";
        Semaphore available = new Semaphore(1);
        DimensionContext ctx = DimensionContext.builder()
                                              .jobId(jobId)
                                              .stopCheck(() -> false)
                                              .meterRegistry(meterRegistry)
                                              .flowConfig(new TemplateConfigProperties.Flow())
                                              .inFlightConsumerPermitPair(PermitPair.of(null, available))
                                              .build();
        BackpressureManager manager = new BackpressureManager(ctx, meterRegistry);

        DimensionLease lease = manager.acquire(InFlightConsumerDimension.ID, () -> false);
        lease.close();
        lease.close();

        assertEquals(1D, getCounter(BackpressureMetricNames.MANAGER_RELEASE_IDEMPOTENT_HIT, jobId),
                "MANAGER_RELEASE_IDEMPOTENT_HIT 应在重复 close 时增加");
    }

    @Test
    void managerReleaseLeakDetectedWhenLeaseGcdWithoutClose() throws Exception {
        String jobId = "job-leak";
        Semaphore available = new Semaphore(2);
        DimensionContext ctx = DimensionContext.builder()
                                              .jobId(jobId)
                                              .stopCheck(() -> false)
                                              .meterRegistry(meterRegistry)
                                              .flowConfig(new TemplateConfigProperties.Flow())
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
            double leak = getCounter(BackpressureMetricNames.MANAGER_RELEASE_LEAK_DETECTED, jobId);
            if (leak >= 1D) {
                return;
            }
        }
        assertTrue(getCounter(BackpressureMetricNames.MANAGER_RELEASE_LEAK_DETECTED, jobId) >= 1D,
                "MANAGER_RELEASE_LEAK_DETECTED 应在 lease 未 close 被 GC 时增加（Cleaner 异步执行）");
    }

    private static void removeLeaseFromActiveLeases(BackpressureManager manager, String leaseId) throws Exception {
        Field field = BackpressureManager.class.getDeclaredField("activeLeases");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, ?> map = (java.util.Map<String, ?>) field.get(manager);
        map.remove(leaseId);
    }
}
