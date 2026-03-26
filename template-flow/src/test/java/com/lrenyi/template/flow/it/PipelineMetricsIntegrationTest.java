package com.lrenyi.template.flow.it;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.EmbeddedBatchSpec;
import com.lrenyi.template.flow.api.FlowInlet;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowPipeline;
import com.lrenyi.template.flow.api.FlowSourceAdapters;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.api.NamedBranchSpec;
import com.lrenyi.template.flow.api.NextMapSpec;
import com.lrenyi.template.flow.api.NextStageSpec;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.health.FlowHealth;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class PipelineMetricsIntegrationTest {

    private SimpleMeterRegistry meterRegistry;
    private FlowManager flowManager;
    private TemplateConfigProperties.Flow config;

    @BeforeEach
    public void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        FlowManager.reset();
        FlowResourceRegistry.reset();

        config = new TemplateConfigProperties.Flow();
        config.getLimits().getGlobal().setConsumerThreads(16);
        config.getLimits().getGlobal().setProducerThreads(8);
        config.getLimits().getGlobal().setStorageCapacity(500);

        flowManager = FlowManager.getInstance(config, meterRegistry);
    }

    @AfterEach
    public void tearDown() {
        try {
            flowManager.stopAll(true);
        } catch (Exception e) {
            log.debug("tearDown stopAll", e);
        }
        FlowManager.reset();
        FlowResourceRegistry.reset();
        FlowHealth.clearIndicators();
    }

    private void awaitCompleted(ProgressTracker tracker, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!tracker.isCompleted(true) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
    }

    private double counterValue(String metricName, String tagJobId) {
        Counter c = meterRegistry.find(metricName)
                                 .tag(FlowMetricNames.TAG_JOB_ID, tagJobId)
                                 .counter();
        return c == null ? 0D : c.count();
    }

    private long timerCount(String metricName, String tagJobId) {
        Timer t = meterRegistry.find(metricName)
                               .tag(FlowMetricNames.TAG_JOB_ID, tagJobId)
                               .timer();
        return t == null ? 0L : t.count();
    }

    private List<Meter> findMetersByJobIdPrefix(String metricName, String prefix) {
        return meterRegistry.find(metricName)
                            .meters()
                            .stream()
                            .filter(m -> {
                                String tag = m.getId().getTag(FlowMetricNames.TAG_JOB_ID);
                                return tag != null && tag.startsWith(prefix);
                            })
                            .toList();
    }

    private double gaugeValue(String metricName, String tagJobId) {
        Gauge g = meterRegistry.find(metricName)
                               .tag(FlowMetricNames.TAG_JOB_ID, tagJobId)
                               .gauge();
        return g == null ? Double.NaN : g.value();
    }

    private static class IntPassThroughJoiner implements FlowJoiner<Integer> {
        @Override
        public Class<Integer> getDataType() { return Integer.class; }
        @Override
        public String joinKey(Integer item) { return String.valueOf(item); }
        @Override
        public void onSingleConsumed(Integer i, String jobId, EgressReason reason) { }
        @Override
        public void onPairConsumed(Integer existing, Integer incoming, String jobId) { }
        @Override
        public FlowSourceProvider<Integer> sourceProvider() { return null; }
    }

    private List<Integer> generateData(int count) {
        List<Integer> data = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            data.add(i);
        }
        return data;
    }

    @Test
    public void testTwoStageLinearPipeline_allStagesHaveCounterMetrics() throws Exception {
        int total = 20;
        AtomicLong sinkCount = new AtomicLong();

        String pipelineId = "linear-2stage-metrics";
        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) FlowPipeline
                .builder(pipelineId, Integer.class, flowManager)
                .nextStage(new IntPassThroughJoiner())
                .sink(Integer.class, (i, jobId) -> sinkCount.incrementAndGet());

        pipeline.run(FlowSourceAdapters.fromIterator(generateData(total).iterator(), null), config);
        awaitCompleted(pipeline.getProgressTracker(), 15_000);

        assertTrue(pipeline.getProgressTracker().isCompleted(true), "管道应在 15 秒内完成");
        assertEquals(total, sinkCount.get(), "Sink 收到的数据量应与输入一致");

        String stage0Tag = pipelineId + ":0";
        String stage1Tag = pipelineId + ":1";

        double stage0Acquired = counterValue(FlowMetricNames.PRODUCTION_ACQUIRED, stage0Tag);
        double stage1Acquired = counterValue(FlowMetricNames.PRODUCTION_ACQUIRED, stage1Tag);
        assertTrue(stage0Acquired >= total, "阶段0 PRODUCTION_ACQUIRED 应 >= " + total);
        assertTrue(stage1Acquired >= 1, "阶段1（Sink段）PRODUCTION_ACQUIRED 应 >= 1");

        double stage0Terminated = counterValue(FlowMetricNames.TERMINATED, stage0Tag);
        double stage1Terminated = counterValue(FlowMetricNames.TERMINATED, stage1Tag);
        assertTrue(stage0Terminated >= total, "阶段0 TERMINATED 应 >= " + total);
        assertTrue(stage1Terminated >= 1, "阶段1 TERMINATED 应 >= 1");

        long stage0DepositCount = timerCount(FlowMetricNames.DEPOSIT_DURATION, stage0Tag);
        long stage1DepositCount = timerCount(FlowMetricNames.DEPOSIT_DURATION, stage1Tag);
        assertTrue(stage0DepositCount >= total);
        assertTrue(stage1DepositCount >= 1);
    }

    @Test
    public void testThreeStageLinearPipeline_middleStageHasMetrics() throws Exception {
        int total = 15;
        AtomicLong sinkCount = new AtomicLong();

        String pipelineId = "linear-3stage-metrics";
        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) FlowPipeline
                .builder(pipelineId, Integer.class, flowManager)
                .nextStage(new IntPassThroughJoiner())
                .nextStage(NextStageSpec.of(Integer.class, new IntPassThroughJoiner(), i -> List.of(i)))
                .sink(Integer.class, (i, jobId) -> sinkCount.incrementAndGet());

        pipeline.run(FlowSourceAdapters.fromIterator(generateData(total).iterator(), null), config);
        awaitCompleted(pipeline.getProgressTracker(), 15_000);

        assertTrue(pipeline.getProgressTracker().isCompleted(true), "3 阶段管道应在 15 秒内完成");
        assertEquals(total, sinkCount.get(), "Sink 应收到全部数据");

        String middleTag = pipelineId + ":1";
        double middleAcquired = counterValue(FlowMetricNames.PRODUCTION_ACQUIRED, middleTag);
        double middleTerminated = counterValue(FlowMetricNames.TERMINATED, middleTag);
        assertTrue(middleAcquired >= 1);
        assertTrue(middleTerminated >= 1);
    }

    @Test
    public void testLinearPipeline_productionReleasedAndFinalizeDurationTracked() throws Exception {
        int total = 10;
        AtomicLong sinkCount = new AtomicLong();

        String pipelineId = "linear-prod-released";
        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) FlowPipeline
                .builder(pipelineId, Integer.class, flowManager)
                .nextStage(new IntPassThroughJoiner())
                .sink(Integer.class, (i, jobId) -> sinkCount.incrementAndGet());

        pipeline.run(FlowSourceAdapters.fromIterator(generateData(total).iterator(), null), config);
        awaitCompleted(pipeline.getProgressTracker(), 15_000);

        assertTrue(pipeline.getProgressTracker().isCompleted(true));

        String stage0Tag = pipelineId + ":0";
        double released = counterValue(FlowMetricNames.PRODUCTION_RELEASED, stage0Tag);
        assertTrue(released >= total);

        long finalizeCount = timerCount(FlowMetricNames.FINALIZE_DURATION, stage0Tag);
        assertTrue(finalizeCount >= 1);
    }

    @Test
    public void testForkTwoBranches_eachBranchHasIndependentMetrics() throws Exception {
        int total = 20;
        AtomicLong sinkA = new AtomicLong();
        AtomicLong sinkB = new AtomicLong();

        String pipelineId = "fork-2branch-metrics";
        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) FlowPipeline
                .builder(pipelineId, Integer.class, flowManager)
                .nextStage(new IntPassThroughJoiner())
                .fork(
                        (FlowPipeline.Builder<Integer> b) -> b
                                .nextStage(NextStageSpec.of(Integer.class,
                                        new IntPassThroughJoiner(), i -> List.of(i * 10)))
                                .sink(Integer.class, (i, jobId) -> sinkA.incrementAndGet()),
                        (FlowPipeline.Builder<Integer> b) -> b
                                .sink(Integer.class, (i, jobId) -> sinkB.incrementAndGet())
                );

        pipeline.run(FlowSourceAdapters.fromIterator(generateData(total).iterator(), null), config);
        awaitCompleted(pipeline.getProgressTracker(), 15_000);

        assertTrue(pipeline.getProgressTracker().isCompleted(true), "Fork 管道应在 15 秒内完成");
        assertEquals(total, sinkA.get(), "分支 A 应收到全部数据");
        assertEquals(total, sinkB.get(), "分支 B 应收到全部数据");

        String commonTag = pipelineId + ":0";
        double commonAcquired = counterValue(FlowMetricNames.PRODUCTION_ACQUIRED, commonTag);
        assertTrue(commonAcquired >= total);

        List<Meter> branchAMeters = findMetersByJobIdPrefix(
                FlowMetricNames.PRODUCTION_ACQUIRED, pipelineId + ":1:fork:0");
        assertTrue(!branchAMeters.isEmpty(), "分支 A 应有带 fork:0 前缀的 PRODUCTION_ACQUIRED 指标");

        List<Meter> branchBMeters = findMetersByJobIdPrefix(
                FlowMetricNames.PRODUCTION_ACQUIRED, pipelineId + ":1:fork:1");
        assertTrue(!branchBMeters.isEmpty(), "分支 B 应有带 fork:1 前缀的 PRODUCTION_ACQUIRED 指标");
    }

    @Test
    public void testForkNamed_branchNameAppearsInMetricJobId() throws Exception {
        int total = 10;
        AtomicLong sinkAlpha = new AtomicLong();
        AtomicLong sinkBeta = new AtomicLong();

        String pipelineId = "fork-named-metrics";
        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) FlowPipeline
                .builder(pipelineId, Integer.class, flowManager)
                .nextStage(new IntPassThroughJoiner())
                .forkNamed(
                        NamedBranchSpec.of("alpha",
                                (FlowPipeline.Builder<Integer> b) -> b
                                        .sink(Integer.class, (i, jobId) -> sinkAlpha.incrementAndGet())),
                        NamedBranchSpec.of("beta",
                                (FlowPipeline.Builder<Integer> b) -> b
                                        .sink(Integer.class, (i, jobId) -> sinkBeta.incrementAndGet()))
                );

        pipeline.run(FlowSourceAdapters.fromIterator(generateData(total).iterator(), null), config);
        awaitCompleted(pipeline.getProgressTracker(), 15_000);

        assertTrue(pipeline.getProgressTracker().isCompleted(true), "命名分支管道应在 15 秒内完成");

        List<Meter> alphaMeters = findMetersByJobIdPrefix(
                FlowMetricNames.PRODUCTION_ACQUIRED, pipelineId);
        boolean foundAlpha = alphaMeters.stream().anyMatch(m -> {
            String tag = m.getId().getTag(FlowMetricNames.TAG_JOB_ID);
            return tag != null && tag.contains("alpha");
        });
        assertTrue(foundAlpha, "命名分支 alpha 的 PRODUCTION_ACQUIRED 指标 tag 应包含 'alpha'");

        boolean foundBeta = alphaMeters.stream().anyMatch(m -> {
            String tag = m.getId().getTag(FlowMetricNames.TAG_JOB_ID);
            return tag != null && tag.contains("beta");
        });
        assertTrue(foundBeta, "命名分支 beta 的 PRODUCTION_ACQUIRED 指标 tag 应包含 'beta'");
    }

    @Test
    public void testAggregateStage_hasItsOwnMetrics() throws Exception {
        int total = 30;
        AtomicLong sinkCount = new AtomicLong();
        AtomicLong sinkBatchItems = new AtomicLong();

        String pipelineId = "aggregate-metrics";
        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) FlowPipeline
                .builder(pipelineId, Integer.class, flowManager)
                .nextStage(new IntPassThroughJoiner())
                .aggregate(10, 5, TimeUnit.SECONDS)
                .sink((List<Integer> list, String jobId) -> {
                    sinkCount.incrementAndGet();
                    sinkBatchItems.addAndGet(list.size());
                });

        pipeline.run(FlowSourceAdapters.fromIterator(generateData(total).iterator(), null), config);
        awaitCompleted(pipeline.getProgressTracker(), 15_000);

        assertTrue(pipeline.getProgressTracker().isCompleted(true), "聚合管道应在 15 秒内完成");
        assertEquals(total, sinkBatchItems.get(), "聚合后 Sink 累计数据量应等于输入总量");

        String stage0Tag = pipelineId + ":0";
        double stage0Acquired = counterValue(FlowMetricNames.PRODUCTION_ACQUIRED, stage0Tag);
        assertTrue(stage0Acquired >= total, "阶段0 PRODUCTION_ACQUIRED 应 >= " + total);

        String aggregateTag = pipelineId + ":1";
        double aggAcquired = counterValue(FlowMetricNames.PRODUCTION_ACQUIRED, aggregateTag);
        assertTrue(aggAcquired >= 1, "聚合阶段 PRODUCTION_ACQUIRED 应 >= 1");

        String sinkTag = pipelineId + ":2";
        double sinkAcquired = counterValue(FlowMetricNames.PRODUCTION_ACQUIRED, sinkTag);
        assertTrue(sinkAcquired >= 1, "Sink 阶段 PRODUCTION_ACQUIRED 应 >= 1");
    }

    @Test
    public void testNextStageWithEmbeddedBatch_hasMetrics() throws Exception {
        int total = 30;
        AtomicLong sinkItems = new AtomicLong();

        String pipelineId = "embedded-batch-nextstage-metrics";
        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) FlowPipeline
                .builder(pipelineId, Integer.class, flowManager)
                .nextStage(NextStageSpec.of(Integer.class,
                                new IntPassThroughJoiner(), i -> List.of(i)),
                        EmbeddedBatchSpec.of(10, 5, TimeUnit.SECONDS))
                .sink((List<Integer> list, String jobId) -> sinkItems.addAndGet(list.size()));

        pipeline.run(FlowSourceAdapters.fromIterator(generateData(total).iterator(), null), config);
        awaitCompleted(pipeline.getProgressTracker(), 15_000);

        assertTrue(pipeline.getProgressTracker().isCompleted(true));
        assertEquals(total, sinkItems.get(), "内嵌攒批 Sink 累计数据量应等于输入总量");

        String stage0Tag = pipelineId + ":0";
        double acquired = counterValue(FlowMetricNames.PRODUCTION_ACQUIRED, stage0Tag);
        assertTrue(acquired >= total);

        double terminated = counterValue(FlowMetricNames.TERMINATED, stage0Tag);
        assertTrue(terminated >= total);
    }

    @Test
    public void testNextMapWithEmbeddedBatch_hasMetrics() throws Exception {
        int total = 20;
        AtomicLong sinkItems = new AtomicLong();

        String pipelineId = "embedded-batch-nextmap-metrics";
        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) FlowPipeline
                .builder(pipelineId, Integer.class, flowManager)
                .nextMap(NextMapSpec.of(
                                Integer.class,
                                Integer.class,
                                i -> i,
                                100L,
                                TimeUnit.MILLISECONDS),
                        EmbeddedBatchSpec.of(5, 3, TimeUnit.SECONDS))
                .sink((List<Integer> list, String jobId) -> sinkItems.addAndGet(list.size()));

        pipeline.run(FlowSourceAdapters.fromIterator(generateData(total).iterator(), null), config);
        awaitCompleted(pipeline.getProgressTracker(), 15_000);

        assertTrue(pipeline.getProgressTracker().isCompleted(true));
        assertEquals(total, sinkItems.get());

        String stage0Tag = pipelineId + ":0";
        double acquired = counterValue(FlowMetricNames.PRODUCTION_ACQUIRED, stage0Tag);
        assertTrue(acquired >= total);
    }

    @Test
    public void testPushMode_metricsIncrementedCorrectly() throws Exception {
        int total = 15;
        AtomicLong sinkCount = new AtomicLong();

        String pipelineId = "push-mode-metrics";
        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) FlowPipeline
                .builder(pipelineId, Integer.class, flowManager)
                .nextStage(new IntPassThroughJoiner())
                .sink(Integer.class, (i, jobId) -> sinkCount.incrementAndGet());

        FlowInlet<Integer> inlet = pipeline.startPush(config);
        for (int i = 1; i <= total; i++) {
            inlet.push(i);
        }
        inlet.markSourceFinished();

        awaitCompleted(pipeline.getProgressTracker(), 15_000);
        assertTrue(pipeline.getProgressTracker().isCompleted(true), "推送模式管道应在 15 秒内完成");
        assertEquals(total, sinkCount.get());

        String stage0Tag = pipelineId + ":0";
        double stage0Acquired = counterValue(FlowMetricNames.PRODUCTION_ACQUIRED, stage0Tag);
        assertTrue(stage0Acquired >= total);
    }

    @Test
    public void testPushMode_completionGaugesReachZeroOnCompletion() throws Exception {
        AtomicLong sinkCount = new AtomicLong();

        String pipelineId = "push-gauges-metrics";
        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) FlowPipeline
                .builder(pipelineId, Integer.class, flowManager)
                .nextStage(new IntPassThroughJoiner())
                .sink(Integer.class, (i, jobId) -> sinkCount.incrementAndGet());

        FlowInlet<Integer> inlet = pipeline.startPush(config);
        for (int i = 1; i <= 5; i++) {
            inlet.push(i);
        }
        inlet.markSourceFinished();

        awaitCompleted(pipeline.getProgressTracker(), 15_000);
        assertTrue(pipeline.getProgressTracker().isCompleted(true));

        String stage0Tag = pipelineId + ":0";
        double sourceFinished = gaugeValue(FlowMetricNames.COMPLETION_SOURCE_FINISHED, stage0Tag);
        if (!Double.isNaN(sourceFinished)) {
            assertEquals(1.0, sourceFinished, 0.01);
        }
        double activeConsumers = gaugeValue(FlowMetricNames.COMPLETION_ACTIVE_CONSUMERS, stage0Tag);
        if (!Double.isNaN(activeConsumers)) {
            assertEquals(0.0, activeConsumers, 0.01);
        }
        double storageUsed = gaugeValue(FlowMetricNames.RESOURCES_PER_JOB_STORAGE_USED, stage0Tag);
        if (!Double.isNaN(storageUsed)) {
            assertEquals(0.0, storageUsed, 0.01);
        }
        double inFlightPush = gaugeValue(FlowMetricNames.COMPLETION_IN_FLIGHT_PUSH, stage0Tag);
        if (!Double.isNaN(inFlightPush)) {
            assertEquals(0.0, inFlightPush, 0.01);
        }
    }

    @Test
    public void testSetMetricJobId_afterStartPush_reregistersPerJobGaugesWithNewTag() throws Exception {
        int total = 5;
        AtomicLong sinkCount = new AtomicLong();
        String pipelineId = "late-rename-pipeline";
        String newName = "RenamedLate";
        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) FlowPipeline
                .builder(pipelineId, Integer.class, flowManager)
                .nextStage(new IntPassThroughJoiner())
                .sink(Integer.class, (i, jobId) -> sinkCount.incrementAndGet());

        FlowInlet<Integer> inlet = pipeline.startPush(config);
        // 在首次 push 之前改名：若已有数据再改名，旧 Counter 序列会被 strip，已产生计数会丢失
        pipeline.getProgressTracker().setMetricJobId(newName);

        String stage0New = newName + ":0";
        String stage1New = newName + ":1";
        double g0 = gaugeValue(FlowMetricNames.RESOURCES_PER_JOB_STORAGE_LIMIT, stage0New);
        double g1 = gaugeValue(FlowMetricNames.RESOURCES_PER_JOB_STORAGE_LIMIT, stage1New);
        assertTrue(!Double.isNaN(g0), "展示名变更后阶段0应有 per-job limit Gauge");
        assertTrue(!Double.isNaN(g1), "展示名变更后 Sink 阶段应有 per-job limit Gauge");

        for (int i = 1; i <= total; i++) {
            inlet.push(i);
        }
        inlet.markSourceFinished();
        awaitCompleted(pipeline.getProgressTracker(), 15_000);
        assertTrue(pipeline.getProgressTracker().isCompleted(true));
        assertEquals(total, sinkCount.get());
        assertTrue(counterValue(FlowMetricNames.PRODUCTION_ACQUIRED, stage0New) >= total);
    }

    @Test
    public void testSetMetricJobId_propagatesToAllStages() throws Exception {
        int total = 10;
        AtomicLong sinkCount = new AtomicLong();

        String pipelineId = "display-name-pipeline";
        String displayName = "MyReport";
        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) FlowPipeline
                .builder(pipelineId, Integer.class, flowManager)
                .nextStage(new IntPassThroughJoiner())
                .nextStage(NextStageSpec.of(Integer.class, new IntPassThroughJoiner(), i -> List.of(i)))
                .sink(Integer.class, (i, jobId) -> sinkCount.incrementAndGet());

        pipeline.getProgressTracker().setMetricJobId(displayName);
        pipeline.run(FlowSourceAdapters.fromIterator(generateData(total).iterator(), null), config);
        awaitCompleted(pipeline.getProgressTracker(), 15_000);

        assertTrue(pipeline.getProgressTracker().isCompleted(true));

        String stage0Tag = displayName + ":0";
        String stage1Tag = displayName + ":1";
        String stage2Tag = displayName + ":2";

        double stage0Acquired = counterValue(FlowMetricNames.PRODUCTION_ACQUIRED, stage0Tag);
        assertTrue(stage0Acquired >= total);

        double stage1Acquired = counterValue(FlowMetricNames.PRODUCTION_ACQUIRED, stage1Tag);
        assertTrue(stage1Acquired >= 1);

        double stage2Acquired = counterValue(FlowMetricNames.PRODUCTION_ACQUIRED, stage2Tag);
        assertTrue(stage2Acquired >= 1);

        double origAcquired = counterValue(FlowMetricNames.PRODUCTION_ACQUIRED, pipelineId + ":0");
        assertEquals(0D, origAcquired, 0.001);
    }

    @Test
    public void testEachStageHasPerJobResourceGauges() throws Exception {
        int total = 5;
        AtomicLong sinkCount = new AtomicLong();

        String pipelineId = "resource-gauges";
        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) FlowPipeline
                .builder(pipelineId, Integer.class, flowManager)
                .nextStage(new IntPassThroughJoiner())
                .sink(Integer.class, (i, jobId) -> sinkCount.incrementAndGet());

        FlowInlet<Integer> inlet = pipeline.startPush(config);

        List<Meter> stage0LimitMeters = meterRegistry.find(
                        FlowMetricNames.RESOURCES_PER_JOB_STORAGE_LIMIT)
                .meters().stream().toList();

        assertTrue(stage0LimitMeters.size() >= 2);

        for (int i = 1; i <= total; i++) {
            inlet.push(i);
        }
        inlet.markSourceFinished();
        awaitCompleted(pipeline.getProgressTracker(), 15_000);
        assertTrue(pipeline.getProgressTracker().isCompleted(true));
    }

    @Test
    public void testGlobalResourceGauges_registeredWithoutJobIdTag() {
        Gauge globalStorageLimit = meterRegistry.find(
                FlowMetricNames.RESOURCES_STORAGE_LIMIT).gauge();
        assertNotNull(globalStorageLimit);
        assertEquals(500.0, globalStorageLimit.value(), 0.01);

        Gauge globalSinkConcurrencyLimit = meterRegistry.find(
                FlowMetricNames.RESOURCES_SINK_CONCURRENCY_LIMIT).gauge();
        assertNotNull(globalSinkConcurrencyLimit);
        assertEquals(64.0, globalSinkConcurrencyLimit.value(), 0.01);
    }

    @Test
    public void testGlobalResourceUsed_returnsZeroWhenNoJobsRunning() {
        Gauge globalStorageUsed = meterRegistry.find(
                FlowMetricNames.RESOURCES_STORAGE_USED).gauge();
        assertNotNull(globalStorageUsed);
        assertEquals(0.0, globalStorageUsed.value(), 0.01);
    }

    @Test
    public void testPipelineSnapshot_consistentWithPerStageCounters() throws Exception {
        int total = 25;
        AtomicLong sinkCount = new AtomicLong();

        String pipelineId = "consistency-check";
        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) FlowPipeline
                .builder(pipelineId, Integer.class, flowManager)
                .nextStage(new IntPassThroughJoiner())
                .sink(Integer.class, (i, jobId) -> sinkCount.incrementAndGet());

        pipeline.run(FlowSourceAdapters.fromIterator(generateData(total).iterator(), null), config);
        awaitCompleted(pipeline.getProgressTracker(), 15_000);

        assertTrue(pipeline.getProgressTracker().isCompleted(true));

        var snapshot = pipeline.getProgressTracker().getSnapshot();
        assertEquals(total, snapshot.productionAcquired());
        assertTrue(snapshot.terminated() >= 1);
        assertTrue(snapshot.endTimeMillis() > 0);
    }

    @Test
    public void testAcquiredMinusTerminated_equalsZeroOnCompletion() throws Exception {
        int total = 20;
        AtomicLong sinkCount = new AtomicLong();

        String pipelineId = "zero-balance";
        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) FlowPipeline
                .builder(pipelineId, Integer.class, flowManager)
                .nextStage(new IntPassThroughJoiner())
                .sink(Integer.class, (i, jobId) -> sinkCount.incrementAndGet());

        pipeline.run(FlowSourceAdapters.fromIterator(generateData(total).iterator(), null), config);
        awaitCompleted(pipeline.getProgressTracker(), 15_000);

        assertTrue(pipeline.getProgressTracker().isCompleted(true));

        String stage0Tag = pipelineId + ":0";
        double stage0Acquired = counterValue(FlowMetricNames.PRODUCTION_ACQUIRED, stage0Tag);
        double stage0Terminated = counterValue(FlowMetricNames.TERMINATED, stage0Tag);
        assertEquals(stage0Acquired, stage0Terminated, 0.001);

        String stage1Tag = pipelineId + ":1";
        double stage1Acquired = counterValue(FlowMetricNames.PRODUCTION_ACQUIRED, stage1Tag);
        double stage1Terminated = counterValue(FlowMetricNames.TERMINATED, stage1Tag);
        assertEquals(stage1Acquired, stage1Terminated, 0.001);
    }

    @Test
    public void testHighThroughput_metricsDoNotDropOrDoubleCount() throws Exception {
        int total = 200;
        AtomicLong sinkCount = new AtomicLong();

        String pipelineId = "high-throughput-metrics";
        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) FlowPipeline
                .builder(pipelineId, Integer.class, flowManager)
                .nextStage(new IntPassThroughJoiner())
                .nextStage(NextStageSpec.of(Integer.class, new IntPassThroughJoiner(), i -> List.of(i)))
                .sink(Integer.class, (i, jobId) -> sinkCount.incrementAndGet());

        pipeline.run(FlowSourceAdapters.fromIterator(generateData(total).iterator(), null), config);
        awaitCompleted(pipeline.getProgressTracker(), 30_000);

        assertTrue(pipeline.getProgressTracker().isCompleted(true));
        assertEquals(total, sinkCount.get());

        String stage0Tag = pipelineId + ":0";
        double stage0Acquired = counterValue(FlowMetricNames.PRODUCTION_ACQUIRED, stage0Tag);
        assertEquals(total, stage0Acquired, 0.001);
        double stage0Terminated = counterValue(FlowMetricNames.TERMINATED, stage0Tag);
        assertEquals(total, stage0Terminated, 0.001);
    }

    @Test
    public void testStoppedPipeline_errorsCounterIncrements() throws Exception {
        AtomicLong sinkCount = new AtomicLong();

        String pipelineId = "stopped-pipeline-metrics";
        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) FlowPipeline
                .builder(pipelineId, Integer.class, flowManager)
                .nextStage(new IntPassThroughJoiner())
                .sink(Integer.class, (i, jobId) -> sinkCount.incrementAndGet());

        FlowInlet<Integer> inlet = pipeline.startPush(config);
        inlet.push(1);
        inlet.push(2);

        pipeline.stop(true);

        try {
            inlet.push(3);
        } catch (IllegalStateException ignored) {
        }

        double jobStoppedErrors = 0D;
        Counter c = meterRegistry.find(FlowMetricNames.ERRORS)
                                 .tag(FlowMetricNames.TAG_ERROR_TYPE, "job_stopped")
                                 .tag(FlowMetricNames.TAG_PHASE, "PRODUCTION")
                                 .counter();
        if (c != null) {
            jobStoppedErrors = c.count();
        }
        assertTrue(jobStoppedErrors >= 1);
    }
}
