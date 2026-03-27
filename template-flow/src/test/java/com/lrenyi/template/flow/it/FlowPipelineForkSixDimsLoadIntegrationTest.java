package com.lrenyi.template.flow.it;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowInlet;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowPipeline;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.api.NamedBranchSpec;
import com.lrenyi.template.flow.api.NextMapSpec;
import com.lrenyi.template.flow.api.NextStageSpec;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.context.FlowProgressSnapshot;
import com.lrenyi.template.flow.health.FlowHealth;
import com.lrenyi.template.flow.internal.FlowLauncher;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.backpressure.BackpressureMetricNames;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flow 管道高压集成：2×nextMap + nextStage + forkNamed(6×aggregate+sink)，推送模式 + 多生产者。
 * <p>
 * 配置约定：{@code limits.global} 数值限流均为 0；压测参数仅 {@code limits.per-job}。
 * <p>
 * 系统属性：{@code -Dflow.load.test.runSmoke=true} 启用 {@link #loadPushSixDims_smoke}，
 * {@code -Dflow.load.test.n=} 覆盖默认条数（冒烟）；{@code -Dflow.load.test.runSlow=true}
 * 启用 {@link #loadPushSixDims_highVolume}；{@code -Dflow.load.test.slow.n=} 覆盖高压条数（默认 100000）。
 * 各维攒批条数（见本类常量数组）须能整除所选 N，以保证 6×N 业务断言稳定。
 * <p><b>监控指标</b>（本测例使用 {@link SimpleMeterRegistry}，成功结束时 {@link #logMicrometerPipelineSummary()} 会打 INFO 汇总）：
 * <ul>
 *   <li><b>业务吞吐/生命周期</b>（{@link FlowMetricNames}，按 {@code jobId} 分阶段，含 fork 分支名）：
 *       {@code production_acquired}、{@code production_released}、{@code terminated}；
 *       计时 {@code deposit.duration}、{@code match.duration}、{@code finalize.duration}；
 *       资源 Gauge {@code resources.per_job.*}、完成辅助 Gauge {@code completion.*}</li>
 *   <li><b>背压</b>（{@link BackpressureMetricNames}）：
 *       {@code manager.acquire.success.*}、{@code dimension.acquire.*}、{@code manager.lease.active.*} 等（global 限流为 0 时仍以 per-job 为主）</li>
 *   <li><b>运行时水位</b>：{@link ProgressTracker#getSnapshot()} 与 Watchdog；卡死时 {@link #dumpStuckState} 会打出各 Launcher 快照</li>
 * </ul>
 * 压测成功结束后 {@link #assertPipelineMetricsHealthy(int, ProgressTracker, FlowManager)} 会校验：
 * 管道快照仅聚合 {@code inStorage}/{@code activeConsumers}（见 {@link #assertPipelineAggregatedSnapshotMatchesCompletion}）；
 * 各 Launcher 快照与首段 {@code inFlightPush} 与引擎 {@code completionCondition} 对齐；
 * {@code completion.*}/{@code resources.per_job.*} Gauge、各阶段 Counter、背压失败/超时/lease、{@code errors}。
 * 指标语义与全量校验可参考 {@link FlowAndBackpressureMetricsIntegrationTest}。
 */
@Slf4j
class FlowPipelineForkSixDimsLoadIntegrationTest {

    private static final String PIPELINE_ID = "fork-six-dims-load";

    /** 与 {@link ProgressTracker#setMetricJobId(String)} 一致，用于 Micrometer {@code jobId} 标签断言 */
    private static final String METRIC_DISPLAY_PREFIX = "LoadSixDims";

    /** 默认冒烟数据量；可通过 {@code -Dflow.load.test.n=} 覆盖 */
    private static final int DEFAULT_N = 10_000;

    /**
     * 各维攒批条数：须能整除默认 N（10_000）与 slow 用下限（100_000），避免尾批与 6×N 断言竞态。
     */
    private static final int[] AGG_BATCH_SIZES = {100, 200, 250, 40, 50, 500};
    private static final int AGG_TIMEOUT_SEC = 5;

    private SimpleMeterRegistry meterRegistry;
    private FlowManager flowManager;
    private TemplateConfigProperties.Flow managerConfig;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        FlowManager.reset();
        FlowResourceRegistry.reset();
    }

    @AfterEach
    void tearDown() {
        try {
            flowManager.stopAll(true);
        } catch (Exception e) {
            log.debug("tearDown stopAll", e);
        }
        FlowManager.reset();
        FlowResourceRegistry.reset();
        FlowHealth.clearIndicators();
    }

    /**
     * 全局数值限流全部为 0（不启用）；驱逐与扫描在 per-job 显式给出，避免回退依赖 global 默认值。
     */
    static TemplateConfigProperties.Flow createGlobalAllZeroPerJobLoadConfig() {
        TemplateConfigProperties.Flow flow = new TemplateConfigProperties.Flow();
        TemplateConfigProperties.Flow.Global g = flow.getLimits().getGlobal();
        g.setProducerThreads(0);
        g.setStorageCapacity(0);
        g.setConsumerThreads(0);
        g.setSinkConsumerThreads(0);

        TemplateConfigProperties.Flow.PerJob p = flow.getLimits().getPerJob();
        p.setProducerThreads(16);
        p.setConsumerThreads(256);
        p.setStorageCapacity(200_000);
        p.setQueuePollIntervalMill(2000);
        p.setEvictionCoordinatorThreads(1);
        p.setEvictionScanIntervalMill(100L);

        p.getKeyedCache().setCacheTtlMill(10_000L);
        return flow;
    }

    /**
     * 大数据量时提高 per-job 存储上限，避免六路扇出 + 多段驻留时 200k 默认容量导致尾批丢失或完成竞态。
     */
    private static void scalePerJobForVolume(TemplateConfigProperties.Flow flow, int n) {
        if (n <= 100_000) {
            return;
        }
        TemplateConfigProperties.Flow.PerJob p = flow.getLimits().getPerJob();
        int storageCap = (int) Math.min(10_000_000L, Math.max(2_000_000L, (long) n * 6L));
        p.setStorageCapacity(storageCap);
    }

    private static long sumSinkItems(AtomicLong[] sinkItemsPerDim) {
        long sum = 0L;
        for (AtomicLong c : sinkItemsPerDim) {
            sum += c.get();
        }
        return sum;
    }

    private static int resolveN() {
        String prop = System.getProperty("flow.load.test.n");
        if (prop != null && !prop.isBlank()) {
            return Integer.parseInt(prop.trim());
        }
        return DEFAULT_N;
    }

    @Test
    @Tag("slow")
    @EnabledIfSystemProperty(named = "flow.load.test.runSmoke", matches = "true")
    void loadPushSixDims_smoke() throws Exception {
        int n = Math.min(resolveN(), 50_000);
        runLoadTest(n, 8, durationTimeoutMs(n), 30_000L);
    }

    @Test
    @Tag("slow")
    @EnabledIfSystemProperty(named = "flow.load.test.runSlow", matches = "true")
    void loadPushSixDims_highVolume() throws Exception {
        String slowN = System.getProperty("flow.load.test.slow.n");
        int n = slowN != null && !slowN.isBlank() ? Integer.parseInt(slowN.trim()) : 100_000;
        runLoadTest(n, 16, durationTimeoutMs(n), 120_000L);
    }

    /**
     * 完成等待上限：大数据量时线性估算可能超过 30 分钟，故对 {@code n > 500_000} 放宽到最多 3 小时。
     */
    private static long durationTimeoutMs(int n) {
        long linear = 120_000L + (long) n * 30L;
        long cap = n > 500_000 ? 3L * 60 * 60 * 1000L : 30L * 60_000L;
        return Math.min(cap, linear);
    }

    private void runLoadTest(int n, int producerThreads, long completionTimeoutMs, long stagnantTimeoutMs)
            throws Exception {
        managerConfig = createGlobalAllZeroPerJobLoadConfig();
        scalePerJobForVolume(managerConfig, n);
        flowManager = FlowManager.getInstance(managerConfig, meterRegistry);

        TemplateConfigProperties.Flow flowConfig = createGlobalAllZeroPerJobLoadConfig();
        scalePerJobForVolume(flowConfig, n);

        AtomicLong[] sinkItemsPerDim = new AtomicLong[6];
        for (int i = 0; i < 6; i++) {
            sinkItemsPerDim[i] = new AtomicLong();
        }

        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) FlowPipeline
                .builder(PIPELINE_ID, Integer.class, flowManager)
                .nextMap(NextMapSpec.<Integer, Integer>builder(Integer.class, Integer.class, i -> i)
                        .consumeInterval(1L, TimeUnit.MILLISECONDS)
                        .build())
                .nextMap(NextMapSpec.<Integer, Integer>builder(Integer.class, Integer.class, i -> i)
                        .consumeInterval(1L, TimeUnit.MILLISECONDS)
                        .build())
                .nextStage(NextStageSpec.<Integer, Integer>builder(Integer.class, new IntPassThroughJoiner(), List::of)
                        .build())
                .forkNamed(
                        branch(0, sinkItemsPerDim[0]),
                        branch(1, sinkItemsPerDim[1]),
                        branch(2, sinkItemsPerDim[2]),
                        branch(3, sinkItemsPerDim[3]),
                        branch(4, sinkItemsPerDim[4]),
                        branch(5, sinkItemsPerDim[5])
                );

        ProgressTracker tracker = pipeline.getProgressTracker();
        tracker.setMetricJobId(METRIC_DISPLAY_PREFIX);

        FlowInlet<Integer> inlet = pipeline.startPush(flowConfig);

        AtomicBoolean sourceFinished = new AtomicBoolean(false);
        AtomicReference<String> watchdogFailure = new AtomicReference<>();
        ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "flow-load-watchdog");
            t.setDaemon(true);
            return t;
        });

        long[] lastSig = new long[] {-1L, -1L, -1L, -1L};
        AtomicInteger stagnantRounds = new AtomicInteger();

        watchdog.scheduleAtFixedRate(() -> {
            try {
                if (watchdogFailure.get() != null) {
                    return;
                }
                FlowProgressSnapshot snap = tracker.getSnapshot();
                long inProd = snap.getInProductionCount();
                long pend = snap.getPendingConsumerCount();
                long sig0 = snap.inStorage();
                long sig1 = snap.activeConsumers();
                long sig2 = inProd;
                long sig3 = pend;

                log.debug(
                        "watchdog snap inStorage={} activeConsumers={} inProduction={} pending={} terminated={} completed={}",
                        sig0,
                        sig1,
                        inProd,
                        pend,
                        snap.terminated(),
                        tracker.isCompleted(true)
                );

                sampleMicrometerMismatch();

                if (sourceFinished.get() && !tracker.isCompleted(true)) {
                    boolean same = sig0 == lastSig[0] && sig1 == lastSig[1] && sig2 == lastSig[2]
                            && sig3 == lastSig[3];
                    if (same) {
                        int r = stagnantRounds.incrementAndGet();
                        if (r * 500L >= stagnantTimeoutMs) {
                            watchdogFailure.set(dumpStuckState(flowManager, tracker, snap, PIPELINE_ID + ":0"));
                        }
                    } else {
                        stagnantRounds.set(0);
                        lastSig[0] = sig0;
                        lastSig[1] = sig1;
                        lastSig[2] = sig2;
                        lastSig[3] = sig3;
                    }
                }
            } catch (Exception ex) {
                watchdogFailure.set("watchdog: " + ex.getMessage());
            }
        }, 1, 500, TimeUnit.MILLISECONDS);

        long t0 = System.nanoTime();
        try {
            runProducers(inlet, n, producerThreads);

            sourceFinished.set(true);
            inlet.markSourceFinished();

            try {
                tracker.getCompletionFuture().get(completionTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                if (watchdogFailure.get() != null) {
                    pipeline.stop(true);
                    throw new AssertionError(watchdogFailure.get(), e);
                }
                pipeline.stop(true);
                throw new AssertionError("管道完成超时: " + tracker.getSnapshot(), e);
            } catch (ExecutionException e) {
                throw new AssertionError(e.getCause());
            }

            if (watchdogFailure.get() != null) {
                log.error("Watchdog failure: {}", watchdogFailure.get());
                pipeline.stop(true);
                throw new AssertionError(watchdogFailure.get());
            }

            assertTrue(tracker.isCompleted(true), () -> "管道应在超时前完成: " + tracker.getSnapshot());

            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            double tps = n / Math.max(1.0, elapsedMs / 1000.0);
            log.info("load test done N={} elapsedMs={} tps={}", n, elapsedMs, tps);

            long expectedItems = (long) n * 6L;
            long waitSinkUntil = System.currentTimeMillis() + Math.min(180_000L, Math.max(30_000L, n / 50L));
            while (sumSinkItems(sinkItemsPerDim) < expectedItems && System.currentTimeMillis() < waitSinkUntil) {
                Thread.sleep(20L);
            }

            long sum = sumSinkItems(sinkItemsPerDim);
            if (sum != expectedItems) {
                log.error(
                        "六路 sink 合计与 6×N 不一致: sum={} expected={} perDim=[{}, {}, {}, {}, {}, {}]",
                        sum,
                        expectedItems,
                        sinkItemsPerDim[0].get(),
                        sinkItemsPerDim[1].get(),
                        sinkItemsPerDim[2].get(),
                        sinkItemsPerDim[3].get(),
                        sinkItemsPerDim[4].get(),
                        sinkItemsPerDim[5].get());
            }
            assertEquals(expectedItems, sum, "六路广播累计条数应为 6×N");

            assertPipelineMetricsHealthy(n, tracker, flowManager);

            logMicrometerPipelineSummary();

        } finally {
            watchdog.shutdownNow();
            watchdog.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /**
     * 压测结束后汇总与本管道相关的 Micrometer 样本（Flow 核心 Counter/Timer/Gauge 及背压 Manager 计数）。
     * jobId 标签形如 {@code LoadSixDims:0}、{@code LoadSixDims:3:fork:0-dim-1:1}，与管道阶段/fork 一致。
     */
    private void logMicrometerPipelineSummary() {
        log.info("---------- Flow 压测 Micrometer 汇总 (pipeline={}) ----------", PIPELINE_ID);
        for (String metricName : new String[] {
                FlowMetricNames.PRODUCTION_ACQUIRED,
                FlowMetricNames.PRODUCTION_RELEASED,
                FlowMetricNames.TERMINATED,
        }) {
            appendMetersForPipeline(metricName);
        }
        for (String metricName : new String[] {
                FlowMetricNames.DEPOSIT_DURATION,
                FlowMetricNames.MATCH_DURATION,
                FlowMetricNames.FINALIZE_DURATION,
        }) {
            appendTimersForPipeline(metricName);
        }
        for (String metricName : new String[] {
                FlowMetricNames.RESOURCES_PER_JOB_STORAGE_USED,
                FlowMetricNames.COMPLETION_SOURCE_FINISHED,
                FlowMetricNames.COMPLETION_IN_FLIGHT_PUSH,
                FlowMetricNames.COMPLETION_ACTIVE_CONSUMERS,
        }) {
            appendGaugesForPipeline(metricName);
        }
        log.info("---------- Backpressure 汇总 (含本 Job 相关 tag) ----------");
        for (String metricName : new String[] {
                BackpressureMetricNames.MANAGER_ACQUIRE_SUCCESS_GLOBAL,
                BackpressureMetricNames.MANAGER_ACQUIRE_SUCCESS_PER_JOB,
                BackpressureMetricNames.MANAGER_LEASE_ACTIVE_GLOBAL,
                BackpressureMetricNames.MANAGER_LEASE_ACTIVE_PER_JOB,
                BackpressureMetricNames.DIM_ACQUIRE_ATTEMPTS_GLOBAL,
                BackpressureMetricNames.DIM_ACQUIRE_ATTEMPTS_PER_JOB,
                BackpressureMetricNames.DIM_ACQUIRE_BLOCKED_GLOBAL,
                BackpressureMetricNames.DIM_ACQUIRE_BLOCKED_PER_JOB,
        }) {
            appendBackpressureMeters(metricName);
        }
        Counter errors = meterRegistry.find(FlowMetricNames.ERRORS).counter();
        if (errors != null && errors.count() > 0) {
            log.warn("Flow ERRORS counter total={} (无 jobId，详见 errorType/phase 标签)", errors.count());
        }
        log.info("---------- Micrometer 汇总结束 ----------");
    }

    private boolean isPipelineMetricJobId(String jobIdTag) {
        return jobIdTag != null && (jobIdTag.contains(PIPELINE_ID) || jobIdTag.contains(METRIC_DISPLAY_PREFIX));
    }

    /**
     * 与 {@link com.lrenyi.template.flow.internal.DefaultProgressTracker} 完成条件对齐：
     * {@code sourceFinished}、{@code inStorage}、{@code activeConsumers}、首段 {@code inFlightPush}；
     * 并校验对应 Micrometer Gauge（{@link FlowMetricNames#COMPLETION_SOURCE_FINISHED} 等）
     * 与 per-job 资源 used 类 Gauge 应为终态（源已结束、余量为 0）。
     */
    private void assertPipelineMetricsHealthy(int n, ProgressTracker pipelineTracker, FlowManager fm) {
        assertPipelineAggregatedSnapshotMatchesCompletion(pipelineTracker);
        assertEveryLauncherSnapshotAndInFlightPush(fm);
        assertCompletionAndPerJobResourceGaugesForDisplayIds();

        for (int s = 0; s < 3; s++) {
            assertProductionTripleEqual(metricLinearStageJobId(s), n, "线性段 nextMap/nextStage 阶段" + s);
        }
        for (int b = 0; b < 6; b++) {
            assertProductionTripleEqual(metricForkStageJobId(b, 0), n, "fork 分支 dim-" + (b + 1) + " aggregate");
            long batches = n / AGG_BATCH_SIZES[b];
            assertProductionTripleEqual(metricForkStageJobId(b, 1), batches, "fork 分支 dim-" + (b + 1) + " sink");
        }
        assertAcquiredGteTerminatedAllPipelineStages();
        assertFlowErrorsTotalZero();
        assertBackpressureFailureAndTimeoutZero();
        assertBackpressureLeaseGaugesZeroForPipeline();
    }

    /**
     * 管道级聚合快照：{@link com.lrenyi.template.flow.pipeline.PipelineProgressTracker#getSnapshot()} 对
     * {@link FlowProgressSnapshot#inStorage()}、{@link FlowProgressSnapshot#activeConsumers()} 为各阶段求和，可用于「全链存储/消费许可」排空判断。
     * <p>
     * 勿断言 {@link FlowProgressSnapshot#getInProductionCount()} / {@link FlowProgressSnapshot#getPendingConsumerCount()}：
     * 管道快照里 production 计数仍取首段，与汇总后的 inStorage/activeConsumers/terminated 混用会使衍生字段失去与单 Job 完成条件相同的语义。
     */
    private void assertPipelineAggregatedSnapshotMatchesCompletion(ProgressTracker pipelineTracker) {
        FlowProgressSnapshot s = pipelineTracker.getSnapshot();
        assertEquals(0L, s.inStorage(), "管道聚合 inStorage 应为 0（完成判定：存储排空）");
        assertEquals(0L, s.activeConsumers(), "管道聚合 activeConsumers 应为 0");
    }

    /** 各子 Launcher 与引擎一致的水位；首段校验 inFlightPush（仅推送入口段）。 */
    private void assertEveryLauncherSnapshotAndInFlightPush(FlowManager fm) {
        fm.getActiveLaunchers().forEach((internalJobId, launcher) -> {
            if (internalJobId == null || !internalJobId.startsWith(PIPELINE_ID)) {
                return;
            }
            FlowProgressSnapshot snap = launcher.getTracker().getSnapshot();
            assertEquals(0L, snap.inStorage(), "Launcher " + internalJobId + " inStorage 应为 0");
            assertEquals(0L, snap.activeConsumers(), "Launcher " + internalJobId + " activeConsumers 应为 0");
            assertEquals(0L, snap.getInProductionCount(), "Launcher " + internalJobId + " inProduction 应为 0");
            assertEquals(0L, snap.getPendingConsumerCount(), "Launcher " + internalJobId + " pendingConsumer 应为 0");
            if (PIPELINE_ID.concat(":0").equals(internalJobId)) {
                assertEquals(0, launcher.getInFlightPushCount(), "首段 inFlightPush 应为 0");
            }
        });
    }

    /**
     * Micrometer 展示名 jobId（{@link #METRIC_DISPLAY_PREFIX}）下：完成类 Gauge 与 per-job 资源 used 应为终态。
     */
    private void assertCompletionAndPerJobResourceGaugesForDisplayIds() {
        int sourceFinishedStages = 0;
        for (Meter m : meterRegistry.find(FlowMetricNames.COMPLETION_SOURCE_FINISHED).meters()) {
            String jid = m.getId().getTag(FlowMetricNames.TAG_JOB_ID);
            if (jid == null || !jid.startsWith(METRIC_DISPLAY_PREFIX)) {
                continue;
            }
            sourceFinishedStages++;
            assertGaugeEquals(m, 1.0, FlowMetricNames.COMPLETION_SOURCE_FINISHED + " jobId=" + jid);
        }
        assertEquals(15, sourceFinishedStages,
                "completion.source_finished 应覆盖 3 段线性 + 6 路×(aggregate+sink) 共 15 个 Launcher");
        String[] zeroWhenDone = {
                FlowMetricNames.COMPLETION_IN_FLIGHT_PUSH,
                FlowMetricNames.COMPLETION_ACTIVE_CONSUMERS,
                FlowMetricNames.RESOURCES_PER_JOB_STORAGE_USED,
        };
        for (String metricName : zeroWhenDone) {
            for (Meter m : meterRegistry.find(metricName).meters()) {
                String jid = m.getId().getTag(FlowMetricNames.TAG_JOB_ID);
                if (jid == null || !jid.startsWith(METRIC_DISPLAY_PREFIX)) {
                    continue;
                }
                assertGaugeEquals(m, 0.0, metricName + " jobId=" + jid);
            }
        }
    }

    private static void assertGaugeEquals(Meter m, double expected, String message) {
        assertTrue(m instanceof Gauge, () -> "非 Gauge: " + message);
        assertEquals(expected, ((Gauge) m).value(), 0.001, message);
    }

    /** 背压 lease 活跃数在完成时应为 0（与 release 一致）。 */
    private void assertBackpressureLeaseGaugesZeroForPipeline() {
        for (String name : new String[] {
                BackpressureMetricNames.MANAGER_LEASE_ACTIVE_GLOBAL,
                BackpressureMetricNames.MANAGER_LEASE_ACTIVE_PER_JOB,
        }) {
            for (Meter m : meterRegistry.find(name).meters()) {
                String jid = m.getId().getTag(BackpressureMetricNames.TAG_JOB_ID);
                if (jid != null && isPipelineMetricJobId(jid) && m instanceof Gauge g) {
                    assertEquals(0.0, g.value(), 0.001, name + " jobId=" + jid);
                }
            }
        }
    }

    private static String metricLinearStageJobId(int stageIndex) {
        return METRIC_DISPLAY_PREFIX + ":" + stageIndex;
    }

    /** @param stageInBranch 0=aggregate 1=sink */
    private static String metricForkStageJobId(int branchIndex, int stageInBranch) {
        return METRIC_DISPLAY_PREFIX + ":3:fork:" + branchIndex + "-dim-" + (branchIndex + 1) + ":" + stageInBranch;
    }

    private void assertProductionTripleEqual(String jobId, long expected, String desc) {
        double acq = counterRequired(FlowMetricNames.PRODUCTION_ACQUIRED, jobId);
        double rel = counterRequired(FlowMetricNames.PRODUCTION_RELEASED, jobId);
        double term = counterRequired(FlowMetricNames.TERMINATED, jobId);
        assertEquals(expected, acq, 0.001, desc + " — production_acquired");
        assertEquals(expected, rel, 0.001, desc + " — production_released");
        assertEquals(expected, term, 0.001, desc + " — terminated");
    }

    private double counterRequired(String metricName, String jobId) {
        Counter c = meterRegistry.find(metricName).tag(FlowMetricNames.TAG_JOB_ID, jobId).counter();
        assertNotNull(c, () -> "缺少指标 " + metricName + " jobId=" + jobId);
        return c.count();
    }

    private void assertAcquiredGteTerminatedAllPipelineStages() {
        for (Meter m : meterRegistry.find(FlowMetricNames.PRODUCTION_ACQUIRED).meters()) {
            String jid = m.getId().getTag(FlowMetricNames.TAG_JOB_ID);
            if (!isPipelineMetricJobId(jid)) {
                continue;
            }
            Counter acq = (Counter) m;
            Counter term = meterRegistry.find(FlowMetricNames.TERMINATED).tag(FlowMetricNames.TAG_JOB_ID, jid).counter();
            assertNotNull(term, () -> "缺少 terminated jobId=" + jid);
            assertTrue(acq.count() + 1.0e-9 >= term.count(),
                    () -> "production_acquired 应 >= terminated, jobId=" + jid
                            + " acquired=" + acq.count() + " terminated=" + term.count());
        }
    }

    private void assertFlowErrorsTotalZero() {
        double sum = 0D;
        for (Meter m : meterRegistry.find(FlowMetricNames.ERRORS).meters()) {
            if (m instanceof Counter c) {
                sum += c.count();
            }
        }
        assertEquals(0.0, sum, 0.001, "app.template.flow.errors 各标签累计应为 0");
    }

    private void assertBackpressureFailureAndTimeoutZero() {
        String[] mustBeZero = {
                BackpressureMetricNames.MANAGER_ACQUIRE_FAILED_GLOBAL,
                BackpressureMetricNames.MANAGER_ACQUIRE_FAILED_PER_JOB,
                BackpressureMetricNames.MANAGER_ACQUIRE_FAILED_OTHER,
                BackpressureMetricNames.DIM_ACQUIRE_TIMEOUT_GLOBAL,
                BackpressureMetricNames.DIM_ACQUIRE_TIMEOUT_PER_JOB,
        };
        for (String name : mustBeZero) {
            assertCounterSumGlobally(name, 0.0, "背压指标累计应为 0");
        }
    }

    private void assertCounterSumGlobally(String metricName, double expected, String message) {
        double sum = 0D;
        for (Meter m : meterRegistry.find(metricName).meters()) {
            if (m instanceof Counter c) {
                sum += c.count();
            }
        }
        assertEquals(expected, sum, 0.001, message + ": " + metricName);
    }

    private void appendMetersForPipeline(String metricName) {
        for (Meter m : meterRegistry.find(metricName).meters()) {
            String jid = m.getId().getTag(FlowMetricNames.TAG_JOB_ID);
            if (!isPipelineMetricJobId(jid)) {
                continue;
            }
            if (m instanceof Counter c) {
                log.info("  {} jobId={} count={}", metricName, jid, c.count());
            }
        }
    }

    private void appendTimersForPipeline(String metricName) {
        for (Meter m : meterRegistry.find(metricName).meters()) {
            String jid = m.getId().getTag(FlowMetricNames.TAG_JOB_ID);
            if (!isPipelineMetricJobId(jid)) {
                continue;
            }
            if (m instanceof Timer t) {
                log.info("  {} jobId={} count={} meanMs={}", metricName, jid, t.count(), t.mean(TimeUnit.MILLISECONDS));
            }
        }
    }

    private void appendGaugesForPipeline(String metricName) {
        for (Meter m : meterRegistry.find(metricName).meters()) {
            String jid = m.getId().getTag(FlowMetricNames.TAG_JOB_ID);
            if (!isPipelineMetricJobId(jid)) {
                continue;
            }
            if (m instanceof Gauge g) {
                log.info("  {} jobId={} value={}", metricName, jid, g.value());
            }
        }
    }

    private void appendBackpressureMeters(String metricName) {
        for (Meter m : meterRegistry.find(metricName).meters()) {
            String jid = m.getId().getTag(BackpressureMetricNames.TAG_JOB_ID);
            if (jid != null && !isPipelineMetricJobId(jid)) {
                continue;
            }
            String dim = m.getId().getTag(BackpressureMetricNames.TAG_DIMENSION_ID);
            if (m instanceof Counter c) {
                log.info("  {} jobId={} dimensionId={} count={}", metricName, jid, dim, c.count());
            } else if (m instanceof Gauge g) {
                log.info("  {} jobId={} dimensionId={} value={}", metricName, jid, dim, g.value());
            } else if (m instanceof Timer t) {
                log.info("  {} jobId={} dimensionId={} count={} meanMs={}",
                        metricName, jid, dim, t.count(), t.mean(TimeUnit.MILLISECONDS));
            }
        }
    }

    private void sampleMicrometerMismatch() {
        for (Meter m : meterRegistry.find(FlowMetricNames.PRODUCTION_ACQUIRED).meters()) {
            String jobId = m.getId().getTag(FlowMetricNames.TAG_JOB_ID);
            if (jobId == null || !jobId.startsWith(PIPELINE_ID)) {
                continue;
            }
            Counter acq = meterRegistry.find(FlowMetricNames.PRODUCTION_ACQUIRED)
                    .tag(FlowMetricNames.TAG_JOB_ID, jobId)
                    .counter();
            Counter term = meterRegistry.find(FlowMetricNames.TERMINATED)
                    .tag(FlowMetricNames.TAG_JOB_ID, jobId)
                    .counter();
            if (acq != null && term != null && acq.count() < term.count()) {
                log.warn("metric anomaly jobId={} acquired={} terminated={}", jobId, acq.count(), term.count());
            }
        }
    }

    private String dumpStuckState(FlowManager fm,
            ProgressTracker pipelineTracker,
            FlowProgressSnapshot snap,
            String firstLauncherId) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("疑似卡死：管道快照=").append(snap).append("; ");
        FlowLauncher<Integer> head = fm.getActiveLauncher(firstLauncherId);
        if (head != null) {
            sb.append("首段inFlightPush=").append(head.getInFlightPushCount()).append("; ");
        }
        fm.getActiveLaunchers().forEach((jid, launcher) -> {
            if (jid != null && jid.startsWith(PIPELINE_ID)) {
                sb.append('[').append(jid).append(" -> ").append(launcher.getTracker().getSnapshot()).append("] ");
            }
        });
        String msg = sb.toString();
        log.error(msg);
        return msg;
    }

    private static void runProducers(FlowInlet<Integer> inlet, int n, int producerThreads) throws Exception {
        ExecutorService exec = Executors.newFixedThreadPool(producerThreads, r -> {
            Thread t = new Thread(r, "flow-load-producer");
            t.setDaemon(false);
            return t;
        });
        int per = (n + producerThreads - 1) / producerThreads;
        CountDownLatch done = new CountDownLatch(producerThreads);
        for (int t = 0; t < producerThreads; t++) {
            final int tid = t;
            exec.submit(() -> {
                try {
                    int start = tid * per + 1;
                    int end = Math.min(n, (tid + 1) * per);
                    for (int v = start; v <= end; v++) {
                        inlet.push(v);
                    }
                } finally {
                    done.countDown();
                }
            });
        }
        boolean finished = done.await(30, TimeUnit.MINUTES);
        exec.shutdown();
        assertTrue(finished, "生产者应在时限内结束");
        assertTrue(exec.awaitTermination(1, TimeUnit.MINUTES));
    }

    private static NamedBranchSpec<Integer> branch(int dimIndex, AtomicLong sinkCounter) {
        int batch = AGG_BATCH_SIZES[dimIndex];
        String name = "dim-" + (dimIndex + 1);
        return NamedBranchSpec.of(name, b -> b.aggregate(batch, AGG_TIMEOUT_SEC, TimeUnit.SECONDS)
                .sink((List<Integer> list, String jobId) -> sinkCounter.addAndGet(list.size())));
    }

    private static class IntPassThroughJoiner implements FlowJoiner<Integer> {
        @Override
        public Class<Integer> getDataType() {
            return Integer.class;
        }

        @Override
        public String joinKey(Integer item) {
            return String.valueOf(item);
        }

        @Override
        public void onSingleConsumed(Integer i, String jobId, EgressReason reason) {
        }

        @Override
        public void onPairConsumed(Integer existing, Integer incoming, String jobId) {
        }

        @Override
        public FlowSourceProvider<Integer> sourceProvider() {
            return null;
        }
    }
}
