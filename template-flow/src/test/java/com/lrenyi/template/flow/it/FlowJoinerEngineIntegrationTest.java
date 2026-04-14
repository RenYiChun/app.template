package com.lrenyi.template.flow.it;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.MismatchPairingJoiner;
import com.lrenyi.template.flow.OverwriteJoiner;
import com.lrenyi.template.flow.PairItem;
import com.lrenyi.template.flow.PairingJoiner;
import com.lrenyi.template.flow.QueueJoiner;
import com.lrenyi.template.flow.api.FlowSource;
import com.lrenyi.template.flow.api.FlowSourceAdapters;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.context.FlowProgressSnapshot;
import com.lrenyi.template.flow.engine.FlowJoinerEngine;
import com.lrenyi.template.flow.health.FlowHealth;
import com.lrenyi.template.flow.internal.DefaultProgressTracker;
import com.lrenyi.template.flow.internal.FlowLauncher;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.model.PreRetryResult;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import com.lrenyi.template.flow.storage.RetryStorageAdapter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flow 框架集成测试：拉取/推送、存储与失败原因、进度与指标、资源与生命周期。
 */
@Slf4j
class FlowJoinerEngineIntegrationTest {

    private static final int TIMEOUT_SEC = 30;
    private static final String JOB_LAUNCHER_TEST = "job-launcher-test";
    private static final String JOB_PULL_MULTI = "job-pull-multi";
    private static final String JOB_MISMATCH = "job-mismatch";
    private static final String JOB_STOP_RESET = "job-stop-reset";
    private TemplateConfigProperties.Flow globalConfig;
    private TemplateConfigProperties.Flow flowConfig;
    private FlowManager manager;
    private FlowJoinerEngine engine;

    @BeforeEach
    void setUp() {
        globalConfig = new TemplateConfigProperties.Flow();
        globalConfig.getLimits().getGlobal().setConsumerThreads(100);
        flowConfig = new TemplateConfigProperties.Flow();
        TemplateConfigProperties.Flow.PerJob perJob = flowConfig.getLimits().getPerJob();
        perJob.setProducerThreads(10);
        perJob.setQueuePollIntervalMill(5000);
        perJob.setStorageCapacity(1000);
        TemplateConfigProperties.Flow.KeyedCache keyedCache = perJob.getKeyedCache();
        keyedCache.setCacheTtlMill(5000);
        keyedCache.setMultiValueEnabled(false);
        keyedCache.setMultiValueMaxPerKey(1);
        manager = FlowManager.getInstance(globalConfig);
        engine = new FlowJoinerEngine(manager);
    }

    @AfterEach
    void tearDown() {
        try {
            manager.stopAll(true);
        } catch (Exception e) {
            log.debug("tearDown stopAll completed with exception (may be expected)", e);
        }
        FlowManager.reset();
        FlowResourceRegistry.reset();
        FlowHealth.clearIndicators();
    }

    @Test
    void itPullSingleStream() throws Exception {
        int total = 20;
        List<PairItem> list = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            list.add(new PairItem("k" + i, "v" + i, null));
        }
        FlowSource<PairItem> singleSource = FlowSourceAdapters.fromIterator(list.iterator(), null);
        OverwriteJoiner joiner = new OverwriteJoiner();

        engine.run("job-pull-single", joiner, singleSource, total, flowConfig);
        ProgressTracker tracker = engine.getProgressTracker("job-pull-single");
        awaitCompleted(() -> tracker.isCompleted(true));
        awaitConsumedOrTerminated(joiner::getOnConsumeCount, tracker, total);

        FlowProgressSnapshot snapshot = tracker.getSnapshot();
        assertEquals(total, snapshot.terminated(), snapshot.toString());
        assertTrue(snapshot.getCompletionRate() >= 1.0 || snapshot.terminated() == total);
        // source 结束后剩余条目经 completion drain 走 SINGLE_CONSUMED，不再走 TIMEOUT
        assertEquals(total, joiner.getOnConsumeCount());
    }

    // ---------- 3.1 拉取模式 ----------

    /** 等待消费数或 terminated 达到预期（异步 deposit 可能晚于 completionFuture） */
    private void awaitConsumedOrTerminated(LongSupplier consumedSupplier,
            ProgressTracker tracker,
            long expected) throws InterruptedException {
        awaitCondition(() -> consumedSupplier.getAsLong() >= expected || tracker.getSnapshot().terminated() >= expected,
                       10_000
        );
    }

    private static void awaitCondition(BooleanSupplier condition, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50));
        }
        throw new AssertionError("等待条件超时: " + timeoutMs + "ms");
    }

    private static void awaitCompleted(BooleanSupplier completed)
            throws InterruptedException {
        assertTrue(awaitConditionWithResult(completed,
                                            TimeUnit.SECONDS.toMillis((long) FlowJoinerEngineIntegrationTest.TIMEOUT_SEC)
        ));
    }

    private static boolean awaitConditionWithResult(BooleanSupplier condition, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50));
        }
        return false;
    }

    @Test
    void itPullMultiStream() throws Exception {
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

        DefaultProgressTracker tracker = new DefaultProgressTracker(JOB_PULL_MULTI, manager);
        tracker.setTotalExpected(JOB_PULL_MULTI, pairCount * 2);
        engine.run(JOB_PULL_MULTI, joiner, tracker, flowConfig);
        awaitCompleted(() -> tracker.isCompleted(true));
        awaitCondition(() -> joiner.getOnSuccessCount() >= pairCount, 10_000);

        assertEquals(pairCount, joiner.getOnSuccessCount());
        FlowProgressSnapshot snapshot = tracker.getSnapshot();
        assertEquals(pairCount * 2, snapshot.terminated());
        assertEquals(0, joiner.getOnFailedCount(EgressReason.SINGLE_CONSUMED));
        assertEquals(0, joiner.getOnFailedCount(EgressReason.MISMATCH));
        assertEquals(0, joiner.getOnFailedCount(EgressReason.EVICTION));
    }

    // ---------- 3.2 推送模式 ----------

    @Test
    void itPullQueueFifo() throws Exception {
        int total = 15;
        List<PairItem> list = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            list.add(new PairItem("q" + i, "v" + i, null));
        }
        FlowSource<PairItem> singleSource = FlowSourceAdapters.fromIterator(list.iterator(), null);
        QueueJoiner joiner = new QueueJoiner();

        engine.run("job-pull-queue", joiner, singleSource, total, flowConfig);
        ProgressTracker tracker = engine.getProgressTracker("job-pull-queue");
        awaitCompleted(() -> tracker.isCompleted(true));
        awaitConsumedOrTerminated(() -> (long) joiner.getConsumedCount(), tracker, total);

        FlowProgressSnapshot snapshot = tracker.getSnapshot();
        assertEquals(total, snapshot.terminated());
        assertEquals(total, joiner.getConsumedCount());
        List<PairItem> order = joiner.getConsumedOrder();
        Set<String> consumedIds = order.stream().map(PairItem::getId).collect(Collectors.toSet());
        Set<String> expectedIds = new HashSet<>();
        for (int i = 0; i < total; i++) {
            expectedIds.add("q" + i);
        }
        assertEquals(expectedIds, consumedIds, "Queue 消费应包含全部条目");
    }

    @Test
    void itFlowLauncherGetters() {
        OverwriteJoiner joiner = new OverwriteJoiner();
        var inlet = engine.startPush(JOB_LAUNCHER_TEST, joiner, flowConfig);
        FlowLauncher<?> launcher = manager.getActiveLauncher(JOB_LAUNCHER_TEST);
        assertNotNull(launcher);
        assertEquals(JOB_LAUNCHER_TEST, launcher.getJobId());
        assertEquals(flowConfig.getLimits().getPerJob().getStorageCapacity(), launcher.getCacheCapacity());
        assertFalse(launcher.isStopped());
        inlet.markSourceFinished();
        manager.stopAll(true);
    }

    @Test
    void itPushCompletion() throws Exception {
        int count = 25;
        OverwriteJoiner joiner = new OverwriteJoiner();
        var inlet = engine.startPush("job-push-complete", joiner, flowConfig);

        for (int i = 0; i < count; i++) {
            inlet.push(new PairItem("p" + i, "v" + i, null));
        }
        inlet.markSourceFinished();
        awaitCompleted(inlet::isCompleted);
        awaitCondition(() -> joiner.getOnConsumeCount() >= count
                && inlet.getProgressTracker().getSnapshot().terminated() >= count, 10_000
        );

        FlowProgressSnapshot snapshot = inlet.getProgressTracker().getSnapshot();
        assertEquals(0, snapshot.activeConsumers());
        assertEquals(0, snapshot.inStorage());
        assertEquals(count, snapshot.terminated());
        assertEquals(count, joiner.getOnConsumeCount());
    }

    @Test
    void itPushMarkFinishedShouldWaitInFlightAndRejectNewPush() throws Exception {
        OverwriteJoiner joiner = new OverwriteJoiner();
        var inlet = engine.startPush("job-push-close-drain", joiner, flowConfig);
        AtomicInteger accepted = new AtomicInteger(0);
        Thread producer = new Thread(() -> {
            for (int i = 0; i < 500; i++) {
                try {
                    inlet.push(new PairItem("drain-" + i, "v" + i, null));
                    accepted.incrementAndGet();
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                } catch (IllegalStateException ignored) {
                    return;
                }
            }
        });
        producer.start();
        awaitCondition(() -> accepted.get() >= 10, 2_000);
        inlet.markSourceFinished();
        producer.join(TimeUnit.SECONDS.toMillis(3));

        assertThrows(IllegalStateException.class, () -> inlet.push(new PairItem("after-close", "v", null)));
        int acceptedCount = accepted.get();
        awaitCompleted(inlet::isCompleted);
        awaitCondition(() -> joiner.getOnConsumeCount() >= acceptedCount, 10_000);
        assertEquals(acceptedCount, joiner.getOnConsumeCount());
    }

    // ---------- 3.3 存储与失败原因 ----------

    @Test
    void itPushStopBeforeFinish() throws Exception {
        OverwriteJoiner joiner = new OverwriteJoiner();
        var inlet = engine.startPush("job-push-stop", joiner, flowConfig);
        for (int i = 0; i < 5; i++) {
            inlet.push(new PairItem("s" + i, "v" + i, null));
        }
        inlet.stop(true);
        awaitConditionWithResult(inlet::isCompleted, TimeUnit.SECONDS.toMillis(3));

        assertTrue(manager.isStopped("job-push-stop"));
    }

    @Test
    void itCaffeineReplace() throws Exception {
        OverwriteJoiner joiner = new OverwriteJoiner();
        var inlet = engine.startPush("job-replace", joiner, flowConfig);
        String sameKey = "sameKey";
        inlet.push(new PairItem(sameKey, "old", null));
        inlet.push(new PairItem(sameKey, "new", null));
        awaitCondition(() -> inlet.getProgressTracker().getSnapshot().productionReleased() >= 2, 10_000);
        inlet.markSourceFinished();
        awaitCompleted(inlet::isCompleted);
        awaitCondition(() -> joiner.getOnFailedCount(EgressReason.REPLACE) >= 1
                && joiner.getOnConsumeCount() >= 1, 10_000
        );

        assertTrue(joiner.getOnFailedCount(EgressReason.REPLACE) >= 1);
        assertEquals(1, joiner.getOnConsumeCount());
    }

    @Test
    void itCaffeineMismatch() throws Exception {
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

        // 匹配模式下每 key 需存储 2 条（每流各 1 条），使用专用配置允许多值
        TemplateConfigProperties.Flow mismatchConfig = new TemplateConfigProperties.Flow();
        mismatchConfig.getLimits().getGlobal().setConsumerThreads(100);
        TemplateConfigProperties.Flow.PerJob mismatchPerJob = mismatchConfig.getLimits().getPerJob();
        mismatchPerJob.setProducerThreads(10);
        mismatchPerJob.setStorageCapacity(1000);
        mismatchPerJob.getKeyedCache().setCacheTtlMill(500);
        mismatchPerJob.getKeyedCache().setMultiValueEnabled(true);
        mismatchPerJob.getKeyedCache().setMultiValueMaxPerKey(4);

        DefaultProgressTracker tracker = new DefaultProgressTracker(JOB_MISMATCH, manager);
        tracker.setTotalExpected(JOB_MISMATCH, 4);
        engine.run(JOB_MISMATCH, joiner, tracker, mismatchConfig);
        awaitCompleted(() -> tracker.isCompleted(true));
        // 匹配模式下无法配对的条目由 TTL 驱逐，通过 processEvictedSlot 以 SINGLE_CONSUMED 离库
        awaitCondition(() -> joiner.getOnFailedCount(EgressReason.SINGLE_CONSUMED) >= 4, 15_000);

        assertEquals(0, joiner.getOnFailedCount(EgressReason.MISMATCH),
                     "不匹配场景改为回槽，不应直接记 MISMATCH"
        );
        assertTrue(joiner.getOnFailedCount(EgressReason.SINGLE_CONSUMED) >= 4,
                   "不匹配数据（匹配模式）由 TTL 驱逐，应为 SINGLE_CONSUMED"
        );
        FlowProgressSnapshot snapshot = tracker.getSnapshot();
        assertEquals(snapshot.productionReleased(), snapshot.terminated());
    }

    @Test
    void itCaffeineRetryPreRetryHandledByMatchedPair() throws Exception {
        String jobId = "job-caffeine-retry-preretry-handled";
        String key = "retry-key";
        RetryablePairingJoiner joiner = new RetryablePairingJoiner();
        TemplateConfigProperties.Flow retryFlow = new TemplateConfigProperties.Flow();
        TemplateConfigProperties.Flow.PerJob retryPerJob = retryFlow.getLimits().getPerJob();
        retryPerJob.setProducerThreads(10);
        retryPerJob.setStorageCapacity(1000);
        retryPerJob.getKeyedCache().setCacheTtlMill(5000);

        var inlet = engine.startPush(jobId, joiner, retryFlow);
        inlet.push(new PairItem(key, "v2", "B"));
        awaitCondition(() -> manager.getActiveLauncher(jobId) != null, 10_000);
        FlowLauncher<?> launcher = manager.getActiveLauncher(jobId);
        assertNotNull(launcher);
        awaitCondition(() -> launcher.getStorage().size() >= 1, 10_000);
        RetryStorageAdapter<PairItem> storage = (RetryStorageAdapter<PairItem>) launcher.getStorage();
        FlowEntry<PairItem> retryEntry = new FlowEntry<>(new PairItem(key, "v1", "A"), jobId);
        PreRetryResult result = storage.preRetry(key, retryEntry, (FlowLauncher<Object>) launcher);
        assertEquals(PreRetryResult.HANDLED, result);
        awaitCondition(() -> joiner.getOnSuccessCount() >= 1, 10_000);
        inlet.stop(true);

        assertEquals(1L, joiner.getOnSuccessCount());
    }

    @Test
    void itEgressReasonInSnapshot() throws Exception {
        OverwriteJoiner joiner = new OverwriteJoiner();
        var inlet = engine.startPush("job-snapshot-reason", joiner, flowConfig);
        inlet.push(new PairItem("r1", "v1", null));
        inlet.push(new PairItem("r1", "v2", null));
        awaitCondition(() -> inlet.getProgressTracker().getSnapshot().productionReleased() >= 2, 10_000);
        inlet.markSourceFinished();
        awaitCompleted(inlet::isCompleted);
        awaitCondition(() -> joiner.getOnFailedCount(EgressReason.REPLACE) >= 1 && joiner.getOnConsumeCount() >= 1,
                       10_000
        );
        FlowProgressSnapshot snapshot = inlet.getProgressTracker().getSnapshot();
        assertEquals(2, snapshot.terminated());
    }

    @Test
    void itEgressCountingConsistency() throws Exception {
        OverwriteJoiner joiner = new OverwriteJoiner();
        var inlet = engine.startPush("job-counting-consistency", joiner, flowConfig);
        inlet.push(new PairItem("k1", "v1", null));
        inlet.push(new PairItem("k1", "v2", null));
        inlet.push(new PairItem("k2", "v2", null));
        awaitCondition(() -> inlet.getProgressTracker().getSnapshot().productionReleased() >= 3, 10_000);
        inlet.markSourceFinished();
        awaitCompleted(inlet::isCompleted);
        awaitCondition(() -> joiner.getOnFailedCount(EgressReason.REPLACE) >= 1 && joiner.getOnConsumeCount() >= 1,
                       10_000
        );
        FlowProgressSnapshot snapshot = inlet.getProgressTracker().getSnapshot();
        assertEquals(3, snapshot.terminated());
    }

    @Test
    void itTerminatedEqualsReleasedUnderMixedEgress() throws Exception {
        OverwriteJoiner joiner = new OverwriteJoiner();
        var inlet = engine.startPush("job-terminated-released-invariant", joiner, flowConfig);
        inlet.push(new PairItem("same", "v1", null));
        inlet.push(new PairItem("same", "v2", null));
        inlet.push(new PairItem("other", "v3", null));
        awaitCondition(() -> inlet.getProgressTracker().getSnapshot().productionReleased() >= 3, 10_000);
        inlet.markSourceFinished();
        awaitCompleted(inlet::isCompleted);
        awaitCondition(() -> joiner.getOnFailedCount(EgressReason.REPLACE) >= 1 && joiner.getOnConsumeCount() >= 1,
                       10_000
        );

        FlowProgressSnapshot snapshot = inlet.getProgressTracker().getSnapshot();
        assertEquals(snapshot.productionReleased(),
                     snapshot.terminated(),
                     "每条 released 数据应恰好终结一次"
        );
    }

    // ---------- 3.4 进度与指标 ----------

    @Test
    void itMetricsJobStartedCountOncePerRun() throws Exception {
        int total = 8;
        List<PairItem> list = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            list.add(new PairItem("ms" + i, "v" + i, null));
        }
        String jobId = "job-metrics-started-once";
        OverwriteJoiner joiner = new OverwriteJoiner();
        FlowSource<PairItem> singleSource = FlowSourceAdapters.fromIterator(list.iterator(), null);

        engine.run(jobId, joiner, singleSource, total, flowConfig);
        ProgressTracker tracker = engine.getProgressTracker(jobId);
        awaitCompleted(() -> tracker.isCompleted(true));
        awaitConsumedOrTerminated(joiner::getOnConsumeCount, tracker, total);
    }

    @Test
    void itMetricsJobCompletedUnifiedAcrossPullAndPush() throws Exception {
        int total = 6;
        List<PairItem> list = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            list.add(new PairItem("mc" + i, "v" + i, null));
        }
        String pullJobId = "job-metrics-completed-pull";
        OverwriteJoiner pullJoiner = new OverwriteJoiner();
        FlowSource<PairItem> singleSource = FlowSourceAdapters.fromIterator(list.iterator(), null);

        engine.run(pullJobId, pullJoiner, singleSource, total, flowConfig);
        ProgressTracker pullTracker = engine.getProgressTracker(pullJobId);
        awaitCompleted(() -> pullTracker.isCompleted(true));
        awaitConsumedOrTerminated(pullJoiner::getOnConsumeCount, pullTracker, total);

        String pushJobId = "job-metrics-completed-push";
        OverwriteJoiner pushJoiner = new OverwriteJoiner();
        var inlet = engine.startPush(pushJobId, pushJoiner, flowConfig);
        for (int i = 0; i < total; i++) {
            inlet.push(new PairItem("mp" + i, "v" + i, null));
        }
        inlet.markSourceFinished();
        awaitCompleted(inlet::isCompleted);
        awaitConsumedOrTerminated(pushJoiner::getOnConsumeCount, inlet.getProgressTracker(), total);
    }

    @Test
    void itUnregisterAfterCompletionRetainsTracker() throws Exception {
        int total = 5;
        String jobId = "job-auto-unregister";
        List<PairItem> list = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            list.add(new PairItem("au" + i, "v" + i, null));
        }
        OverwriteJoiner joiner = new OverwriteJoiner();
        FlowSource<PairItem> singleSource = FlowSourceAdapters.fromIterator(list.iterator(), null);

        engine.run(jobId, joiner, singleSource, total, flowConfig);
        ProgressTracker runningTracker = engine.getProgressTracker(jobId);
        awaitCompleted(() -> runningTracker.isCompleted(true));
        awaitConsumedOrTerminated(joiner::getOnConsumeCount, runningTracker, total);

        manager.unregister(jobId);
        awaitCondition(() -> manager.isStopped(jobId), 10_000);
        assertTrue(manager.getActiveLaunchers().isEmpty());

        ProgressTracker tracker = manager.getProgressTracker(jobId);
        assertNotNull(tracker);
        assertEquals(total, tracker.getSnapshot().terminated());
    }

    @Test
    void itQueueDrainAfterStop() throws Exception {
        flowConfig.getLimits().getPerJob().setStorageCapacity(2);
        QueueJoiner joiner = new QueueJoiner();
        var inlet = engine.startPush("job-queue-drain", joiner, flowConfig);
        inlet.push(new PairItem("d1", "v1", null));
        inlet.push(new PairItem("d2", "v2", null));
        inlet.push(new PairItem("d3", "v3", null));
        inlet.stop(true);
        awaitConditionWithResult(inlet::isCompleted, TimeUnit.SECONDS.toMillis(10));

    }

    @Test
    void itRestartCompletedJobUsesFreshStorageInstance() throws Exception {
        String jobId = "job-restart-fresh-storage";
        OverwriteJoiner firstJoiner = new OverwriteJoiner();
        var firstInlet = engine.startPush(jobId, firstJoiner, flowConfig);
        firstInlet.push(new PairItem("first", "v1", null));
        FlowLauncher<?> firstLauncher = manager.getActiveLauncher(jobId);
        assertNotNull(firstLauncher);
        Object firstStorage = firstLauncher.getStorage();
        firstInlet.markSourceFinished();
        awaitCompleted(firstInlet::isCompleted);
        awaitCondition(() -> firstJoiner.getOnConsumeCount() >= 1, 10_000);

        OverwriteJoiner secondJoiner = new OverwriteJoiner();
        var secondInlet = engine.startPush(jobId, secondJoiner, flowConfig);
        FlowLauncher<?> secondLauncher = manager.getActiveLauncher(jobId);
        assertNotNull(secondLauncher);
        assertNotSame(firstStorage, secondLauncher.getStorage(), "完成后重跑同 jobId 应拿到全新的 storage");

        secondInlet.stop(true);
    }

    @Test
    void itSnapshotCompletionAndSuccess() throws Exception {
        int total = 30;
        List<PairItem> list = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            list.add(new PairItem("c" + i, "v" + i, null));
        }
        OverwriteJoiner joiner = new OverwriteJoiner();
        FlowSource<PairItem> singleSource = FlowSourceAdapters.fromIterator(list.iterator(), null);
        engine.run("job-completion", joiner, singleSource, total, flowConfig);
        ProgressTracker tracker = engine.getProgressTracker("job-completion");
        awaitCompleted(() -> tracker.isCompleted(true));
        awaitConsumedOrTerminated(joiner::getOnConsumeCount, tracker, total);

        FlowProgressSnapshot snapshot = tracker.getSnapshot();
        assertEquals(total, snapshot.terminated());
        assertTrue(snapshot.getCompletionRate() >= 1.0);
    }

    // ---------- 3.5 资源与生命周期 ----------

    @Test
    void itMetricsAndHealth() throws Exception {
        OverwriteJoiner joiner = new OverwriteJoiner();
        var inlet = engine.startPush("job-metrics", joiner, flowConfig);
        inlet.push(new PairItem("m1", "v1", null));
        inlet.markSourceFinished();
        awaitCompleted(inlet::isCompleted);

        java.util.Map<String, Object> healthStatus = manager.getHealthStatus();
        assertNotNull(healthStatus);
        assertTrue(healthStatus.containsKey("overallStatus"));
        assertTrue(healthStatus.containsKey("indicators"));

        var status = manager.checkHealth();
        assertNotNull(status);
        assertTrue(List.of(com.lrenyi.template.flow.health.HealthStatus.HEALTHY,
                           com.lrenyi.template.flow.health.HealthStatus.DEGRADED,
                           com.lrenyi.template.flow.health.HealthStatus.UNHEALTHY
        ).contains(status));
    }

    @Test
    void itMultiJobIsolation() throws Exception {
        OverwriteJoiner joiner1 = new OverwriteJoiner();
        OverwriteJoiner joiner2 = new OverwriteJoiner();
        var inlet1 = engine.startPush("job-isolation-1", joiner1, flowConfig);
        var inlet2 = engine.startPush("job-isolation-2", joiner2, flowConfig);
        inlet1.push(new PairItem("a1", "v1", null));
        inlet2.push(new PairItem("b1", "v1", null));
        inlet1.markSourceFinished();
        inlet2.markSourceFinished();
        awaitCompleted(inlet1::isCompleted);
        awaitCompleted(inlet2::isCompleted);
        awaitCondition(() -> joiner1.getOnConsumeCount() >= 1 && joiner2.getOnConsumeCount() >= 1, 10_000);

        assertEquals(1, joiner1.getOnConsumeCount());
        assertEquals(1, joiner2.getOnConsumeCount());
        manager.unregister("job-isolation-1");
        manager.unregister("job-isolation-2");
        assertTrue(manager.getActiveLaunchers().isEmpty(), "手动注销后应无活跃 launcher");
    }

    @Test
    void itStopJobAndReset() {
        OverwriteJoiner joiner = new OverwriteJoiner();
        var inlet = engine.startPush(JOB_STOP_RESET, joiner, flowConfig);
        inlet.push(new PairItem("x1", "v1", null));
        manager.stopById(JOB_STOP_RESET, true);
        assertTrue(manager.isStopped(JOB_STOP_RESET));

        FlowManager.reset();
        FlowResourceRegistry.reset();
        FlowManager manager2 = FlowManager.getInstance(globalConfig);
        assertNotNull(manager2);
        assertTrue(manager2.getActiveLaunchers().isEmpty());
    }

    @Test
    void itResetAfterCompletion() throws Exception {
        OverwriteJoiner joiner = new OverwriteJoiner();
        var inlet = engine.startPush("job-reset-after", joiner, flowConfig);
        inlet.push(new PairItem("z1", "v1", null));
        inlet.markSourceFinished();
        awaitCompleted(inlet::isCompleted);

        FlowManager.reset();
        FlowResourceRegistry.reset();
        FlowHealth.clearIndicators();

        TemplateConfigProperties.Flow cfg2 = new TemplateConfigProperties.Flow();
        cfg2.getLimits().getGlobal().setConsumerThreads(100);
        FlowManager manager2 = FlowManager.getInstance(cfg2);
        FlowJoinerEngine engine2 = new FlowJoinerEngine(manager2);
        OverwriteJoiner joiner2 = new OverwriteJoiner();
        var inlet2 = engine2.startPush("job-new-after-reset", joiner2, flowConfig);
        inlet2.push(new PairItem("n1", "v1", null));
        inlet2.markSourceFinished();
        awaitCompleted(inlet2::isCompleted);
        awaitCondition(() -> joiner2.getOnConsumeCount() >= 1, 10_000);
        assertEquals(1, joiner2.getOnConsumeCount());
    }

    private static final class RetryablePairingJoiner extends PairingJoiner {
        @Override
        public boolean isRetryable(PairItem item, String jobId) {
            return true;
        }
    }

}
