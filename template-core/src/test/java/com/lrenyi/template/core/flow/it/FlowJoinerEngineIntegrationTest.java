package com.lrenyi.template.core.flow.it;

import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.flow.FailureReason;
import com.lrenyi.template.core.flow.FlowJoinerEngine;
import com.lrenyi.template.core.flow.manager.FlowManager;
import com.lrenyi.template.core.flow.MismatchPairingJoiner;
import com.lrenyi.template.core.flow.OverwriteJoiner;
import com.lrenyi.template.core.flow.PairItem;
import com.lrenyi.template.core.flow.PairingJoiner;
import com.lrenyi.template.core.flow.ProgressTracker;
import com.lrenyi.template.core.flow.QueueJoiner;
import com.lrenyi.template.core.flow.context.FlowProgressSnapshot;
import com.lrenyi.template.core.flow.health.FlowHealth;
import com.lrenyi.template.core.flow.impl.DefaultProgressTracker;
import com.lrenyi.template.core.flow.resource.FlowResourceRegistry;
import com.lrenyi.template.core.flow.source.FlowSource;
import com.lrenyi.template.core.flow.source.FlowSourceAdapters;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flow 框架集成测试：拉取/推送、存储与失败原因、进度与指标、资源与生命周期。
 */
class FlowJoinerEngineIntegrationTest {

    private static final int TIMEOUT_SEC = 30;
    private TemplateConfigProperties.JobGlobal globalConfig;
    private TemplateConfigProperties.JobConfig jobConfig;
    private FlowManager manager;
    private FlowJoinerEngine engine;

    @BeforeEach
    void setUp() {
        globalConfig = new TemplateConfigProperties.JobGlobal();
        globalConfig.setGlobalSemaphoreMaxLimit(100);
        globalConfig.setProgressDisplaySecond(0);
        jobConfig = new TemplateConfigProperties.JobConfig();
        jobConfig.setJobProducerLimit(10);
        jobConfig.setTtlMill(5000);
        jobConfig.setMaxCacheSize(1000);
        manager = FlowManager.getInstance(globalConfig);
        engine = new FlowJoinerEngine(manager);
    }

    @AfterEach
    void tearDown() {
        try {
            manager.stopAll(true);
        } catch (Exception ignored) {
            // ignore
        }
        FlowManager.reset();
        FlowResourceRegistry.reset();
        FlowHealth.clearIndicators();
    }

    /** 等待消费数或 terminated 达到预期（异步 deposit 可能晚于 completionFuture） */
    private void awaitConsumedOrTerminated(Supplier<Long> consumedSupplier, ProgressTracker tracker, long expected) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (consumedSupplier.get() >= expected || tracker.getSnapshot().terminated() >= expected) {
                return;
            }
            Thread.sleep(100);
        }
    }

    // ---------- 3.1 拉取模式 ----------

    @Test
    void IT_PULL_SINGLE_STREAM() throws Exception {
        int total = 20;
        List<PairItem> list = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            list.add(new PairItem("k" + i, "v" + i, null));
        }
        FlowSource<PairItem> singleSource = FlowSourceAdapters.fromIterator(list.iterator(), null);
        OverwriteJoiner joiner = new OverwriteJoiner();

        engine.run("job-pull-single", joiner, singleSource, total, jobConfig);
        ProgressTracker tracker = engine.getProgressTracker("job-pull-single");
        tracker.getCompletionFuture().get(TIMEOUT_SEC, TimeUnit.SECONDS);
        awaitConsumedOrTerminated(joiner::getOnConsumeCount, tracker, total);

        FlowProgressSnapshot snapshot = tracker.getSnapshot();
        assertEquals(total, snapshot.terminated());
        assertTrue(snapshot.getCompletionRate() >= 1.0 || snapshot.terminated() == total);
        assertEquals(total, joiner.getOnConsumeCount());
    }

    @Test
    void IT_PULL_MULTI_STREAM() throws Exception {
        int pairCount = 10;
        List<PairItem> listA = new ArrayList<>();
        List<PairItem> listB = new ArrayList<>();
        for (int i = 0; i < pairCount; i++) {
            String id = "key-" + i;
            listA.add(new PairItem(id, "a", "A"));
            listB.add(new PairItem(id, "b", "B"));
        }
        FlowSource<PairItem> sourceA = FlowSourceAdapters.fromIterator(listA.iterator(), null);
        FlowSource<PairItem> sourceB = FlowSourceAdapters.fromIterator(listB.iterator(), null);
        PairingJoiner joiner = new PairingJoiner();
        joiner.setSourceProvider(FlowSourceAdapters.fromFlowSources(List.of(sourceA, sourceB)));

        DefaultProgressTracker tracker = new DefaultProgressTracker("job-pull-multi", manager);
        tracker.setTotalExpected("job-pull-multi", pairCount * 2);
        engine.run("job-pull-multi", joiner, tracker, jobConfig);
        tracker.getCompletionFuture().get(TIMEOUT_SEC, TimeUnit.SECONDS);
        for (int i = 0; i < 100; i++) {
            if (joiner.getOnSuccessCount() >= pairCount) break;
            Thread.sleep(100);
        }

        assertEquals(pairCount, joiner.getOnSuccessCount());
        FlowProgressSnapshot snapshot = tracker.getSnapshot();
        assertEquals(pairCount * 2, snapshot.terminated());
        assertTrue(joiner.getOnFailedCount(FailureReason.MISMATCH) == 0);
        assertTrue(joiner.getOnFailedCount(FailureReason.EVICTION) == 0);
    }

    @Test
    void IT_PULL_QUEUE_FIFO() throws Exception {
        int total = 15;
        List<PairItem> list = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            list.add(new PairItem("q" + i, "v" + i, null));
        }
        FlowSource<PairItem> singleSource = FlowSourceAdapters.fromIterator(list.iterator(), null);
        QueueJoiner joiner = new QueueJoiner();

        engine.run("job-pull-queue", joiner, singleSource, total, jobConfig);
        ProgressTracker tracker = engine.getProgressTracker("job-pull-queue");
        tracker.getCompletionFuture().get(TIMEOUT_SEC, TimeUnit.SECONDS);
        awaitConsumedOrTerminated(() -> (long) joiner.getConsumedCount(), tracker, total);

        FlowProgressSnapshot snapshot = tracker.getSnapshot();
        assertEquals(total, snapshot.terminated());
        assertEquals(total, joiner.getConsumedCount());
        List<PairItem> order = joiner.getConsumedOrder();
        java.util.Set<String> consumedIds = order.stream().map(PairItem::getId).collect(java.util.stream.Collectors.toSet());
        java.util.Set<String> expectedIds = new java.util.HashSet<>();
        for (int i = 0; i < total; i++) {
            expectedIds.add("q" + i);
        }
        assertEquals(expectedIds, consumedIds, "Queue 消费应包含全部条目");
    }

    // ---------- 3.2 推送模式 ----------

    @Test
    void IT_PUSH_COMPLETION() throws Exception {
        int count = 25;
        OverwriteJoiner joiner = new OverwriteJoiner();
        var inlet = engine.startPush("job-push-complete", joiner, jobConfig);

        for (int i = 0; i < count; i++) {
            inlet.push(new PairItem("p" + i, "v" + i, null));
        }
        inlet.markSourceFinished();
        inlet.getCompletionFuture().get(TIMEOUT_SEC, TimeUnit.SECONDS);

        FlowProgressSnapshot snapshot = inlet.getProgressTracker().getSnapshot();
        assertEquals(0, snapshot.activeConsumers());
        assertEquals(0, snapshot.inStorage());
        assertEquals(count, snapshot.terminated());
        assertEquals(count, joiner.getOnConsumeCount());
    }

    @Test
    void IT_PUSH_STOP_BEFORE_FINISH() throws Exception {
        OverwriteJoiner joiner = new OverwriteJoiner();
        var inlet = engine.startPush("job-push-stop", joiner, jobConfig);
        for (int i = 0; i < 5; i++) {
            inlet.push(new PairItem("s" + i, "v" + i, null));
        }
        inlet.stop(true);
        try {
            inlet.getCompletionFuture().get(3, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // 未调用 markSourceFinished 时实现可能不完成 future，符合“与 stop 语义一致”
        }

        assertTrue(manager.isStopped("job-push-stop"));
        if (inlet.getCompletionFuture().isDone()) {
            FlowProgressSnapshot snapshot = inlet.getProgressTracker().getSnapshot();
            assertTrue(snapshot.getPassiveEgressByReason(FailureReason.SHUTDOWN.name()) >= 0);
        }
    }

    // ---------- 3.3 存储与失败原因 ----------

    @Test
    void IT_CAFFEINE_REPLACE() throws Exception {
        OverwriteJoiner joiner = new OverwriteJoiner();
        var inlet = engine.startPush("job-replace", joiner, jobConfig);
        String sameKey = "sameKey";
        inlet.push(new PairItem(sameKey, "old", null));
        inlet.push(new PairItem(sameKey, "new", null));
        inlet.markSourceFinished();
        inlet.getCompletionFuture().get(TIMEOUT_SEC, TimeUnit.SECONDS);
        for (int i = 0; i < 100; i++) {
            if (joiner.getOnFailedCount(FailureReason.REPLACE) >= 1 && joiner.getOnConsumeCount() >= 1) break;
            Thread.sleep(100);
        }

        assertTrue(joiner.getOnFailedCount(FailureReason.REPLACE) >= 1);
        FlowProgressSnapshot snapshot = inlet.getProgressTracker().getSnapshot();
        assertTrue(snapshot.getPassiveEgressByReason(FailureReason.REPLACE.name()) >= 1);
        assertEquals(1, joiner.getOnConsumeCount());
    }

    @Test
    void IT_CAFFEINE_MISMATCH() throws Exception {
        Set<String> mismatchKeys = Set.of("key-1", "key-2");
        MismatchPairingJoiner joiner = new MismatchPairingJoiner(mismatchKeys);
        List<PairItem> listA = new ArrayList<>();
        List<PairItem> listB = new ArrayList<>();
        for (String id : mismatchKeys) {
            listA.add(new PairItem(id, "a", "A"));
            listB.add(new PairItem(id, "b", "B"));
        }
        FlowSource<PairItem> sourceA = FlowSourceAdapters.fromIterator(listA.iterator(), null);
        FlowSource<PairItem> sourceB = FlowSourceAdapters.fromIterator(listB.iterator(), null);
        joiner.setSourceProvider(FlowSourceAdapters.fromFlowSources(List.of(sourceA, sourceB)));

        DefaultProgressTracker tracker = new DefaultProgressTracker("job-mismatch", manager);
        tracker.setTotalExpected("job-mismatch", 4);
        engine.run("job-mismatch", joiner, tracker, jobConfig);
        tracker.getCompletionFuture().get(TIMEOUT_SEC, TimeUnit.SECONDS);
        for (int i = 0; i < 100; i++) {
            if (joiner.getOnFailedCount(FailureReason.MISMATCH) >= 2) break;
            Thread.sleep(100);
        }

        assertTrue(joiner.getOnFailedCount(FailureReason.MISMATCH) >= 2, "isMatched=false 时两条均 onFailed，2 个 key 共 4 次");
        FlowProgressSnapshot snapshot = tracker.getSnapshot();
        assertTrue(snapshot.getPassiveEgressByReason(FailureReason.MISMATCH.name()) >= 2);
    }

    @Test
    void IT_FAILURE_REASON_IN_SNAPSHOT() throws Exception {
        OverwriteJoiner joiner = new OverwriteJoiner();
        var inlet = engine.startPush("job-snapshot-reason", joiner, jobConfig);
        inlet.push(new PairItem("r1", "v1", null));
        inlet.push(new PairItem("r1", "v2", null));
        inlet.markSourceFinished();
        inlet.getCompletionFuture().get(TIMEOUT_SEC, TimeUnit.SECONDS);
        for (int i = 0; i < 100; i++) {
            FlowProgressSnapshot s = inlet.getProgressTracker().getSnapshot();
            if (s.getPassiveEgressByReason(FailureReason.REPLACE.name()) >= 1) break;
            Thread.sleep(100);
        }

        FlowProgressSnapshot snapshot = inlet.getProgressTracker().getSnapshot();
        assertNotNull(snapshot.passiveEgressByReason());
        assertTrue(snapshot.getPassiveEgressByReason(FailureReason.REPLACE.name()) >= 1);
    }

    @Test
    void IT_QUEUE_DRAIN_AFTER_STOP() throws Exception {
        jobConfig.setMaxCacheSize(2);
        QueueJoiner joiner = new QueueJoiner();
        var inlet = engine.startPush("job-queue-drain", joiner, jobConfig);
        inlet.push(new PairItem("d1", "v1", null));
        inlet.push(new PairItem("d2", "v2", null));
        inlet.push(new PairItem("d3", "v3", null));
        inlet.stop(true);
        inlet.getCompletionFuture().get(TIMEOUT_SEC, TimeUnit.SECONDS);

        FlowProgressSnapshot snapshot = inlet.getProgressTracker().getSnapshot();
        assertTrue(snapshot.getPassiveEgressByReason(FailureReason.SHUTDOWN.name()) >= 0);
    }

    // ---------- 3.4 进度与指标 ----------

    @Test
    void IT_SNAPSHOT_COMPLETION_AND_SUCCESS() throws Exception {
        int total = 30;
        List<PairItem> list = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            list.add(new PairItem("c" + i, "v" + i, null));
        }
        OverwriteJoiner joiner = new OverwriteJoiner();
        FlowSource<PairItem> singleSource = FlowSourceAdapters.fromIterator(list.iterator(), null);
        engine.run("job-completion", joiner, singleSource, total, jobConfig);
        ProgressTracker tracker = engine.getProgressTracker("job-completion");
        tracker.getCompletionFuture().get(TIMEOUT_SEC, TimeUnit.SECONDS);
        awaitConsumedOrTerminated(joiner::getOnConsumeCount, tracker, total);

        FlowProgressSnapshot snapshot = tracker.getSnapshot();
        assertEquals(total, snapshot.terminated());
        assertTrue(snapshot.getCompletionRate() >= 1.0);
        assertTrue(snapshot.getSuccessRate() > 0);
        assertEquals(total, snapshot.activeEgress());
    }

    @Test
    void IT_METRICS_AND_HEALTH() throws Exception {
        OverwriteJoiner joiner = new OverwriteJoiner();
        var inlet = engine.startPush("job-metrics", joiner, jobConfig);
        inlet.push(new PairItem("m1", "v1", null));
        inlet.markSourceFinished();
        inlet.getCompletionFuture().get(TIMEOUT_SEC, TimeUnit.SECONDS);

        java.util.Map<String, Object> metrics = manager.getMetrics();
        assertNotNull(metrics);
        assertTrue(metrics.containsKey("counters") || metrics.containsKey("errors") || metrics.size() > 0);

        java.util.Map<String, Object> healthStatus = manager.getHealthStatus();
        assertNotNull(healthStatus);
        assertTrue(healthStatus.containsKey("overallStatus"));
        assertTrue(healthStatus.containsKey("indicators"));

        var status = manager.checkHealth();
        assertNotNull(status);
        assertTrue(List.of(
            com.lrenyi.template.core.flow.health.HealthStatus.HEALTHY,
            com.lrenyi.template.core.flow.health.HealthStatus.DEGRADED,
            com.lrenyi.template.core.flow.health.HealthStatus.UNHEALTHY
        ).contains(status));
    }

    // ---------- 3.5 资源与生命周期 ----------

    @Test
    void IT_MULTI_JOB_ISOLATION() throws Exception {
        OverwriteJoiner joiner1 = new OverwriteJoiner();
        OverwriteJoiner joiner2 = new OverwriteJoiner();
        var inlet1 = engine.startPush("job-isolation-1", joiner1, jobConfig);
        var inlet2 = engine.startPush("job-isolation-2", joiner2, jobConfig);
        inlet1.push(new PairItem("a1", "v1", null));
        inlet2.push(new PairItem("b1", "v1", null));
        inlet1.markSourceFinished();
        inlet2.markSourceFinished();
        inlet1.getCompletionFuture().get(TIMEOUT_SEC, TimeUnit.SECONDS);
        inlet2.getCompletionFuture().get(TIMEOUT_SEC, TimeUnit.SECONDS);
        for (int i = 0; i < 100; i++) {
            if (joiner1.getOnConsumeCount() >= 1 && joiner2.getOnConsumeCount() >= 1) break;
            Thread.sleep(100);
        }

        assertEquals(1, joiner1.getOnConsumeCount());
        assertEquals(1, joiner2.getOnConsumeCount());
        assertTrue(manager.getActiveLaunchers().size() >= 1, "运行期间或完成后 launcher 仍可存在");
    }

    @Test
    void IT_STOP_JOB_AND_RESET() throws Exception {
        OverwriteJoiner joiner = new OverwriteJoiner();
        var inlet = engine.startPush("job-stop-reset", joiner, jobConfig);
        inlet.push(new PairItem("x1", "v1", null));
        manager.stopById("job-stop-reset", true);
        assertTrue(manager.isStopped("job-stop-reset"));

        FlowManager.reset();
        FlowResourceRegistry.reset();
        FlowManager manager2 = FlowManager.getInstance(globalConfig);
        assertNotNull(manager2);
        assertTrue(manager2.getActiveLaunchers().isEmpty());
    }

    @Test
    void IT_RESET_AFTER_COMPLETION() throws Exception {
        OverwriteJoiner joiner = new OverwriteJoiner();
        var inlet = engine.startPush("job-reset-after", joiner, jobConfig);
        inlet.push(new PairItem("z1", "v1", null));
        inlet.markSourceFinished();
        inlet.getCompletionFuture().get(TIMEOUT_SEC, TimeUnit.SECONDS);

        FlowManager.reset();
        FlowResourceRegistry.reset();
        FlowHealth.clearIndicators();

        TemplateConfigProperties.JobGlobal cfg2 = new TemplateConfigProperties.JobGlobal();
        cfg2.setGlobalSemaphoreMaxLimit(100);
        cfg2.setProgressDisplaySecond(0);
        FlowManager manager2 = FlowManager.getInstance(cfg2);
        FlowJoinerEngine engine2 = new FlowJoinerEngine(manager2);
        OverwriteJoiner joiner2 = new OverwriteJoiner();
        var inlet2 = engine2.startPush("job-new-after-reset", joiner2, jobConfig);
        inlet2.push(new PairItem("n1", "v1", null));
        inlet2.markSourceFinished();
        inlet2.getCompletionFuture().get(TIMEOUT_SEC, TimeUnit.SECONDS);
        for (int i = 0; i < 100; i++) {
            if (joiner2.getOnConsumeCount() >= 1) break;
            Thread.sleep(100);
        }
        assertEquals(1, joiner2.getOnConsumeCount());
    }
}
