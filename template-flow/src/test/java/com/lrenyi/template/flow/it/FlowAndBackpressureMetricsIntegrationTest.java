package com.lrenyi.template.flow.it;

import java.util.ArrayList;
import java.util.List;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.PairItem;
import com.lrenyi.template.flow.PairingJoiner;
import com.lrenyi.template.flow.api.FlowSource;
import com.lrenyi.template.flow.api.FlowSourceAdapters;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.backpressure.BackpressureMetricNames;
import com.lrenyi.template.flow.internal.DefaultProgressTracker;
import com.lrenyi.template.flow.backpressure.dimension.ConsumerConcurrencyDimension;
import com.lrenyi.template.flow.backpressure.dimension.InFlightConsumerDimension;
import com.lrenyi.template.flow.backpressure.dimension.InFlightProductionDimension;
import com.lrenyi.template.flow.backpressure.dimension.ProducerConcurrencyDimension;
import com.lrenyi.template.flow.backpressure.dimension.StorageDimension;
import com.lrenyi.template.flow.engine.FlowJoinerEngine;
import com.lrenyi.template.flow.health.FlowHealth;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 集成测试：验证 FlowMetricNames 与 BackpressureMetricNames 中所有指标的计数正确性。
 *
 * <h3>FlowMetricNames 覆盖</h3>
 * <ul>
 *   <li>TERMINATED: 与 snapshot.terminated() 一致</li>
 *   <li>DEPOSIT_DURATION: 每次 launch 入 Storage 记录</li>
 *   <li>MATCH_DURATION: 配对消费时记录</li>
 *   <li>FINALIZE_DURATION: 消费完成时记录</li>
 *   <li>ERRORS (job_stopped): stop 后 push 时 launch 检测到 stopped</li>
 *   <li>ERRORS (onPairConsumed_failed): onPairConsumed 抛异常时</li>
 * </ul>
 *
 * <h3>BackpressureMetricNames 覆盖</h3>
 * <ul>
 *   <li>MANAGER_ACQUIRE_SUCCESS / MANAGER_LEASE_ACTIVE: 正常流程</li>
 *   <li>DIM_ACQUIRE_ATTEMPTS / DIM_ACQUIRE_DURATION / DIM_RELEASE_COUNT: 各维度 (storage, producer-concurrency, in-flight-production, in-flight-consumer, consumer-concurrency)</li>
 *   <li>DIM_ACQUIRE_BLOCKED / DIM_ACQUIRE_TIMEOUT / MANAGER_ACQUIRE_FAILED: 需背压/超时场景，由单元测试覆盖</li>
 *   <li>MANAGER_RELEASE_IDEMPOTENT_HIT / MANAGER_RELEASE_LEAK_DETECTED: 需重复 close / GC 场景，由单元测试覆盖</li>
 * </ul>
 */
@Slf4j
class FlowAndBackpressureMetricsIntegrationTest {

    private SimpleMeterRegistry meterRegistry;
    private FlowManager manager;
    private FlowJoinerEngine engine;
    private TemplateConfigProperties.Flow flowConfig;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        FlowManager.reset();
        FlowResourceRegistry.reset();
        TemplateConfigProperties.Flow globalConfig = new TemplateConfigProperties.Flow();
        globalConfig.getLimits().getGlobal().setConsumerThreads(333);
        globalConfig.getLimits().getGlobal().setProducerThreads(5);
        globalConfig.getLimits().getGlobal().setInFlightConsumer(20);
        globalConfig.getLimits().getGlobal().setStorageCapacity(1000);
        manager = FlowManager.getInstance(globalConfig, meterRegistry);
        engine = new FlowJoinerEngine(manager);

        flowConfig = new TemplateConfigProperties.Flow();
        flowConfig.getLimits().getPerJob().setProducerThreads(5);
        flowConfig.getLimits().getPerJob().setStorageCapacity(100);
        flowConfig.getLimits().getPerJob().setConsumerThreads(5);
        flowConfig.getLimits().getPerJob().setInFlightProduction(10);
        flowConfig.getLimits().getPerJob().setInFlightConsumer(10);
    }

    @AfterEach
    void tearDown() {
        try {
            manager.stopAll(true);
        } catch (Exception e) {
            log.debug("tearDown stopAll", e);
        }
        FlowManager.reset();
        FlowResourceRegistry.reset();
        FlowHealth.clearIndicators();
    }

    private void awaitCompleted(java.util.function.BooleanSupplier completed, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!completed.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
    }

    private double getFlowCounter(String metricName, String jobId) {
        var c = meterRegistry.find(metricName).tag(FlowMetricNames.TAG_JOB_ID, jobId).counter();
        return c == null ? 0D : c.count();
    }

    private double getFlowCounter(String metricName, String tagKey, String tagValue) {
        var c = meterRegistry.find(metricName).tag(tagKey, tagValue).counter();
        return c == null ? 0D : c.count();
    }

    private double getFlowErrorsCounter(String metricName, String errorType, String phase) {
        var c = meterRegistry.find(metricName)
                             .tag(FlowMetricNames.TAG_ERROR_TYPE, errorType)
                             .tag(FlowMetricNames.TAG_PHASE, phase)
                             .counter();
        return c == null ? 0D : c.count();
    }

    private long getFlowTimerCount(String metricName, String jobId) {
        var t = meterRegistry.find(metricName).tag(FlowMetricNames.TAG_JOB_ID, jobId).timer();
        return t == null ? 0L : t.count();
    }

    private double getBackpressureCounter(String metricName, String jobId) {
        var c = meterRegistry.find(metricName).tag(BackpressureMetricNames.TAG_JOB_ID, jobId).counter();
        return c == null ? 0D : c.count();
    }

    private double getBackpressureCounter(String metricName, String jobId, String dimensionId) {
        var c = meterRegistry.find(metricName)
                             .tag(BackpressureMetricNames.TAG_JOB_ID, jobId)
                             .tag(BackpressureMetricNames.TAG_DIMENSION_ID, dimensionId)
                             .counter();
        return c == null ? 0D : c.count();
    }

    private double getBackpressureGauge(String metricName, String jobId) {
        var g = meterRegistry.find(metricName).tag(BackpressureMetricNames.TAG_JOB_ID, jobId).gauge();
        return g == null ? 0D : g.value();
    }

    private long getBackpressureTimerCount(String metricName, String jobId, String dimensionId) {
        var t = meterRegistry.find(metricName)
                            .tag(BackpressureMetricNames.TAG_JOB_ID, jobId)
                            .tag(BackpressureMetricNames.TAG_DIMENSION_ID, dimensionId)
                            .timer();
        return t == null ? 0L : t.count();
    }

    // ==================== FlowMetricNames ====================

    @Nested
    class FlowMetricNamesTests {

        @Test
        void terminatedCounterIncrementsPerConsumedItem() throws Exception {
            int total = 10;
            String jobId = "job-terminated-metrics";
            List<PairItem> list = new ArrayList<>();
            for (int i = 0; i < total; i++) {
                list.add(new PairItem("k" + i, "v" + i, null));
            }
            var joiner = new com.lrenyi.template.flow.OverwriteJoiner();
            FlowSource<PairItem> source = FlowSourceAdapters.fromIterator(list.iterator(), null);
            engine.run(jobId, joiner, source, total, flowConfig);
            ProgressTracker tracker = engine.getProgressTracker(jobId);
            awaitCompleted(tracker::isCompleted, 15_000);

            long snapshotTerminated = tracker.getSnapshot().terminated();
            assertEquals(snapshotTerminated, getFlowCounter(FlowMetricNames.TERMINATED, jobId),
                    "TERMINATED 应与 snapshot.terminated() 一致");
            assertTrue(snapshotTerminated >= 1, "应有至少 1 条数据被消费");
        }

        @Test
        void depositDurationTimerRecordsOnLaunch() throws Exception {
            String jobId = "job-deposit-duration";
            var joiner = new com.lrenyi.template.flow.OverwriteJoiner();
            var inlet = engine.startPush(jobId, joiner, flowConfig);
            inlet.push(new PairItem("d1", "v1", null));
            inlet.push(new PairItem("d2", "v2", null));
            inlet.markSourceFinished();
            awaitCompleted(inlet::isCompleted, 10_000);

            assertTrue(getFlowTimerCount(FlowMetricNames.DEPOSIT_DURATION, jobId) >= 2,
                    "DEPOSIT_DURATION 应有至少 2 次记录（2 条数据）");
        }

        @Test
        void matchDurationTimerRecordsOnPairConsumption() throws Exception {
            int pairCount = 3;
            String jobId = "job-match-duration";
            List<PairItem> listA = new ArrayList<>();
            List<PairItem> listB = new ArrayList<>();
            for (int i = 0; i < pairCount; i++) {
                String id = "key-" + i;
                listA.add(new PairItem(id, "a", "A"));
                listB.add(new PairItem(id, "b", "B"));
            }
            PairingJoiner joiner = new PairingJoiner();
            joiner.setSourceProvider(FlowSourceAdapters.fromFlowSources(
                    List.of(FlowSourceAdapters.fromIterator(listA.iterator(), null),
                            FlowSourceAdapters.fromIterator(listB.iterator(), null))));
            DefaultProgressTracker tracker = new DefaultProgressTracker(jobId, manager);
            tracker.setTotalExpected(jobId, pairCount * 2L);
            engine.run(jobId, joiner, tracker, flowConfig);
            awaitCompleted(tracker::isCompleted, 15_000);

            assertTrue(getFlowTimerCount(FlowMetricNames.MATCH_DURATION, jobId) >= 1,
                    "MATCH_DURATION 配对模式应有记录");
        }

        @Test
        void finalizeDurationTimerRecordsOnConsumption() throws Exception {
            String jobId = "job-finalize-duration";
            var joiner = new com.lrenyi.template.flow.OverwriteJoiner();
            var inlet = engine.startPush(jobId, joiner, flowConfig);
            inlet.push(new PairItem("f1", "v1", null));
            inlet.markSourceFinished();
            awaitCompleted(inlet::isCompleted, 10_000);

            assertTrue(getFlowTimerCount(FlowMetricNames.FINALIZE_DURATION, jobId) >= 1,
                    "FINALIZE_DURATION 应有至少 1 次记录");
        }

        @Test
        void errorsCounterIncrementsOnJobStopped() throws Exception {
            String jobId = "job-errors-stopped";
            var inlet = engine.startPush(jobId, new com.lrenyi.template.flow.OverwriteJoiner(), flowConfig);
            inlet.push(new PairItem("e1", "v1", null));
            manager.stopById(jobId, true);
            awaitCompleted(() -> manager.isStopped(jobId), 5_000);
            try {
                inlet.push(new PairItem("e2", "v2", null));
            } catch (IllegalStateException ignored) {
            }
            double jobStopped = getFlowErrorsCounter(FlowMetricNames.ERRORS, "job_stopped", "PRODUCTION");
            assertTrue(jobStopped >= 1,
                    "ERRORS job_stopped 应在 stop 后 push 时增加（launch 检测到 stopped），actual=" + jobStopped);
        }

        @Test
        void errorsCounterIncrementsOnMatchProcessFailed() throws Exception {
            String jobId = "job-errors-match-failed";
            var failingJoiner = new PairingJoiner() {
                @Override
                public void onPairConsumed(PairItem existing, PairItem incoming, String jid) {
                    throw new RuntimeException("match_process_failed test");
                }
            };
            List<PairItem> listA = List.of(new PairItem("k1", "a", "A"));
            List<PairItem> listB = List.of(new PairItem("k1", "b", "B"));
            failingJoiner.setSourceProvider(FlowSourceAdapters.fromFlowSources(
                    List.of(FlowSourceAdapters.fromIterator(listA.iterator(), null),
                            FlowSourceAdapters.fromIterator(listB.iterator(), null))));
            DefaultProgressTracker tracker = new DefaultProgressTracker(jobId, manager);
            tracker.setTotalExpected(jobId, 2L);
            engine.run(jobId, failingJoiner, tracker, flowConfig);
            awaitCompleted(tracker::isCompleted, 20_000);

            double matchFailed = getFlowErrorsCounter(FlowMetricNames.ERRORS, "onPairConsumed_failed", "CONSUMPTION");
            assertTrue(matchFailed >= 1,
                    "ERRORS onPairConsumed_failed 应在 onPairConsumed 抛异常时增加, actual=" + matchFailed);
        }
    }

    // ==================== BackpressureMetricNames ====================

    @Nested
    class BackpressureMetricNamesTests {

        @Test
        void managerAcquireSuccessIncrementsOnFlowRun() throws Exception {
            String jobId = "job-bp-acquire-success";
            var joiner = new com.lrenyi.template.flow.OverwriteJoiner();
            var inlet = engine.startPush(jobId, joiner, flowConfig);
            inlet.push(new PairItem("b1", "v1", null));
            inlet.markSourceFinished();
            awaitCompleted(inlet::isCompleted, 10_000);

            assertTrue(getBackpressureCounter(BackpressureMetricNames.MANAGER_ACQUIRE_SUCCESS, jobId) >= 1,
                    "MANAGER_ACQUIRE_SUCCESS 应在 acquire 成功时增加");
        }

        @Test
        void managerLeaseActiveGaugeReturnsToZeroAfterCompletion() throws Exception {
            String jobId = "job-bp-lease-active";
            var joiner = new com.lrenyi.template.flow.OverwriteJoiner();
            var inlet = engine.startPush(jobId, joiner, flowConfig);
            inlet.push(new PairItem("l1", "v1", null));
            inlet.markSourceFinished();
            awaitCompleted(inlet::isCompleted, 10_000);

            assertEquals(0D, getBackpressureGauge(BackpressureMetricNames.MANAGER_LEASE_ACTIVE, jobId),
                    "MANAGER_LEASE_ACTIVE 完成后应为 0");
        }

        @Test
        void storageDimensionMetricsRecordAcquireAndRelease() throws Exception {
            String jobId = "job-bp-storage";
            var joiner = new com.lrenyi.template.flow.OverwriteJoiner();
            var inlet = engine.startPush(jobId, joiner, flowConfig);
            inlet.push(new PairItem("s1", "v1", null));
            inlet.push(new PairItem("s2", "v2", null));
            inlet.markSourceFinished();
            awaitCompleted(inlet::isCompleted, 10_000);

            assertTrue(getBackpressureCounter(BackpressureMetricNames.DIM_ACQUIRE_ATTEMPTS, jobId,
                    StorageDimension.ID) >= 1, "storage DIM_ACQUIRE_ATTEMPTS 应有记录");
            assertTrue(getBackpressureTimerCount(BackpressureMetricNames.DIM_ACQUIRE_DURATION, jobId,
                    StorageDimension.ID) >= 1, "storage DIM_ACQUIRE_DURATION 应有记录");
            assertTrue(getBackpressureCounter(BackpressureMetricNames.DIM_RELEASE_COUNT, jobId,
                    StorageDimension.ID) >= 1, "storage DIM_RELEASE_COUNT 应有记录");
        }

        @Test
        void producerConcurrencyDimensionMetricsRecordAcquireAndRelease() throws Exception {
            String jobId = "job-bp-producer";
            List<PairItem> list = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                list.add(new PairItem("p" + i, "v" + i, null));
            }
            var joiner = new com.lrenyi.template.flow.OverwriteJoiner();
            FlowSource<PairItem> source = FlowSourceAdapters.fromIterator(list.iterator(), null);
            engine.run(jobId, joiner, source, 5, flowConfig);
            ProgressTracker tracker = engine.getProgressTracker(jobId);
            awaitCompleted(tracker::isCompleted, 15_000);

            assertTrue(getBackpressureCounter(BackpressureMetricNames.DIM_ACQUIRE_ATTEMPTS, jobId,
                    ProducerConcurrencyDimension.ID) >= 1, "producer-concurrency DIM_ACQUIRE_ATTEMPTS 应有记录");
            assertTrue(getBackpressureTimerCount(BackpressureMetricNames.DIM_ACQUIRE_DURATION, jobId,
                    ProducerConcurrencyDimension.ID) >= 1, "producer-concurrency DIM_ACQUIRE_DURATION 应有记录");
            assertTrue(getBackpressureCounter(BackpressureMetricNames.DIM_RELEASE_COUNT, jobId,
                    ProducerConcurrencyDimension.ID) >= 1, "producer-concurrency DIM_RELEASE_COUNT 应有记录");
        }

        @Test
        void inFlightProductionDimensionMetricsRecordAcquireAndRelease() throws Exception {
            String jobId = "job-bp-inflight-prod";
            var joiner = new com.lrenyi.template.flow.OverwriteJoiner();
            var inlet = engine.startPush(jobId, joiner, flowConfig);
            inlet.push(new PairItem("ip1", "v1", null));
            inlet.push(new PairItem("ip2", "v2", null));
            inlet.markSourceFinished();
            awaitCompleted(inlet::isCompleted, 10_000);

            assertTrue(getBackpressureCounter(BackpressureMetricNames.DIM_ACQUIRE_ATTEMPTS, jobId,
                    InFlightProductionDimension.ID) >= 1, "in-flight-production DIM_ACQUIRE_ATTEMPTS 应有记录");
            assertTrue(getBackpressureTimerCount(BackpressureMetricNames.DIM_ACQUIRE_DURATION, jobId,
                    InFlightProductionDimension.ID) >= 1, "in-flight-production DIM_ACQUIRE_DURATION 应有记录");
            assertTrue(getBackpressureCounter(BackpressureMetricNames.DIM_RELEASE_COUNT, jobId,
                    InFlightProductionDimension.ID) >= 1, "in-flight-production DIM_RELEASE_COUNT 应有记录");
        }

        @Test
        void inFlightConsumerDimensionMetricsRecordAcquireAndRelease() throws Exception {
            String jobId = "job-bp-inflight-cons";
            var joiner = new com.lrenyi.template.flow.OverwriteJoiner();
            var inlet = engine.startPush(jobId, joiner, flowConfig);
            inlet.push(new PairItem("ic1", "v1", null));
            inlet.markSourceFinished();
            awaitCompleted(inlet::isCompleted, 10_000);

            assertTrue(getBackpressureCounter(BackpressureMetricNames.DIM_ACQUIRE_ATTEMPTS, jobId,
                    InFlightConsumerDimension.ID) >= 1, "in-flight-consumer DIM_ACQUIRE_ATTEMPTS 应有记录");
            assertTrue(getBackpressureTimerCount(BackpressureMetricNames.DIM_ACQUIRE_DURATION, jobId,
                    InFlightConsumerDimension.ID) >= 1, "in-flight-consumer DIM_ACQUIRE_DURATION 应有记录");
            assertTrue(getBackpressureCounter(BackpressureMetricNames.DIM_RELEASE_COUNT, jobId,
                    InFlightConsumerDimension.ID) >= 1, "in-flight-consumer DIM_RELEASE_COUNT 应有记录");
        }

        @Test
        void consumerConcurrencyDimensionMetricsRecordAcquireAndRelease() throws Exception {
            String jobId = "job-bp-consumer";
            var joiner = new com.lrenyi.template.flow.OverwriteJoiner();
            var inlet = engine.startPush(jobId, joiner, flowConfig);
            inlet.push(new PairItem("c1", "v1", null));
            inlet.markSourceFinished();
            awaitCompleted(inlet::isCompleted, 10_000);

            assertTrue(getBackpressureCounter(BackpressureMetricNames.DIM_ACQUIRE_ATTEMPTS, jobId,
                    ConsumerConcurrencyDimension.ID) >= 1, "consumer-concurrency DIM_ACQUIRE_ATTEMPTS 应有记录");
            assertTrue(getBackpressureTimerCount(BackpressureMetricNames.DIM_ACQUIRE_DURATION, jobId,
                    ConsumerConcurrencyDimension.ID) >= 1, "consumer-concurrency DIM_ACQUIRE_DURATION 应有记录");
            assertTrue(getBackpressureCounter(BackpressureMetricNames.DIM_RELEASE_COUNT, jobId,
                    ConsumerConcurrencyDimension.ID) >= 1, "consumer-concurrency DIM_RELEASE_COUNT 应有记录");
        }
    }
}
