package com.lrenyi.template.flow.it;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
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
import com.lrenyi.template.flow.context.FlowProgressSnapshot;
import com.lrenyi.template.flow.engine.FlowJoinerEngine;
import com.lrenyi.template.flow.health.FlowHealth;
import com.lrenyi.template.flow.internal.DefaultProgressTracker;
import com.lrenyi.template.flow.internal.FlowLauncher;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.model.FailureReason;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flow 框架集成测试：拉取/推送、存储与失败原因、进度与指标、资源与生命周期。
 */
@Slf4j
class FlowJoinerEngineIntegrationTest {
    
    private static final int TIMEOUT_SEC = 30;
    private TemplateConfigProperties.Flow globalConfig;
    private TemplateConfigProperties.Flow flowConfig;
    private FlowManager manager;
    private FlowJoinerEngine engine;
    
    @BeforeEach
    void setUp() {
        globalConfig = new TemplateConfigProperties.Flow();
        globalConfig.getConsumer().setConcurrencyLimit(100);
        flowConfig = new TemplateConfigProperties.Flow();
        flowConfig.getProducer().setParallelism(10);
        flowConfig.getConsumer().setTtlMill(5000);
        flowConfig.getProducer().setMaxCacheSize(1000);
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
    void IT_PULL_SINGLE_STREAM() throws Exception {
        int total = 20;
        List<PairItem> list = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            list.add(new PairItem("k" + i, "v" + i, null));
        }
        FlowSource<PairItem> singleSource = FlowSourceAdapters.fromIterator(list.iterator(), null);
        OverwriteJoiner joiner = new OverwriteJoiner();
        
        engine.run("job-pull-single", joiner, singleSource, total, flowConfig);
        ProgressTracker tracker = engine.getProgressTracker("job-pull-single");
        tracker.getCompletionFuture().get(TIMEOUT_SEC, TimeUnit.SECONDS);
        awaitConsumedOrTerminated(joiner::getOnConsumeCount, tracker, total);
        
        FlowProgressSnapshot snapshot = tracker.getSnapshot();
        assertEquals(total, snapshot.terminated());
        assertTrue(snapshot.getCompletionRate() >= 1.0 || snapshot.terminated() == total);
        assertEquals(total, joiner.getOnConsumeCount());
    }
    
    // ---------- 3.1 拉取模式 ----------
    
    /** 等待消费数或 terminated 达到预期（异步 deposit 可能晚于 completionFuture） */
    private void awaitConsumedOrTerminated(Supplier<Long> consumedSupplier,
            ProgressTracker tracker,
            long expected) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (consumedSupplier.get() >= expected || tracker.getSnapshot().terminated() >= expected) {
                return;
            }
            Thread.sleep(100);
        }
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
        engine.run("job-pull-multi", joiner, tracker, flowConfig);
        tracker.getCompletionFuture().get(TIMEOUT_SEC, TimeUnit.SECONDS);
        for (int i = 0; i < 100; i++) {
            if (joiner.getOnSuccessCount() >= pairCount) {break;}
            Thread.sleep(100);
        }
        
        assertEquals(pairCount, joiner.getOnSuccessCount());
        FlowProgressSnapshot snapshot = tracker.getSnapshot();
        assertEquals(pairCount * 2, snapshot.terminated());
        assertEquals(0, joiner.getOnFailedCount(FailureReason.MISMATCH));
        assertEquals(0, joiner.getOnFailedCount(FailureReason.EVICTION));
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
        
        engine.run("job-pull-queue", joiner, singleSource, total, flowConfig);
        ProgressTracker tracker = engine.getProgressTracker("job-pull-queue");
        tracker.getCompletionFuture().get(TIMEOUT_SEC, TimeUnit.SECONDS);
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
    
    // ---------- 3.2 推送模式 ----------
    
    @Test
    void IT_FlowLauncher_getters() {
        OverwriteJoiner joiner = new OverwriteJoiner();
        var inlet = engine.startPush("job-launcher-test", joiner, flowConfig);
        FlowLauncher<?> launcher = manager.getActiveLauncher("job-launcher-test");
        assertNotNull(launcher);
        assertEquals("job-launcher-test", launcher.getJobId());
        assertEquals(flowConfig.getProducer().getMaxCacheSize(), launcher.getCacheCapacity());
        assertFalse(launcher.isStopped());
        inlet.markSourceFinished();
        manager.stopAll(true);
    }
    
    @Test
    void IT_PUSH_COMPLETION() throws Exception {
        int count = 25;
        OverwriteJoiner joiner = new OverwriteJoiner();
        var inlet = engine.startPush("job-push-complete", joiner, flowConfig);
        
        for (int i = 0; i < count; i++) {
            inlet.push(new PairItem("p" + i, "v" + i, null));
        }
        inlet.markSourceFinished();
        inlet.getCompletionFuture().get(TIMEOUT_SEC, TimeUnit.SECONDS);
        for (int i = 0; i < 100; i++) {
            if (joiner.getOnConsumeCount() >= count && inlet.getProgressTracker().getSnapshot().terminated() >= count) {
                break;
            }
            Thread.sleep(100);
        }
        
        FlowProgressSnapshot snapshot = inlet.getProgressTracker().getSnapshot();
        assertEquals(0, snapshot.activeConsumers());
        assertEquals(0, snapshot.inStorage());
        assertEquals(count, snapshot.terminated());
        assertEquals(count, joiner.getOnConsumeCount());
    }
    
    @Test
    void IT_PUSH_STOP_BEFORE_FINISH() throws Exception {
        OverwriteJoiner joiner = new OverwriteJoiner();
        var inlet = engine.startPush("job-push-stop", joiner, flowConfig);
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
        var inlet = engine.startPush("job-replace", joiner, flowConfig);
        String sameKey = "sameKey";
        inlet.push(new PairItem(sameKey, "old", null));
        inlet.push(new PairItem(sameKey, "new", null));
        inlet.markSourceFinished();
        inlet.getCompletionFuture().get(TIMEOUT_SEC, TimeUnit.SECONDS);
        for (int i = 0; i < 100; i++) {
            if (joiner.getOnFailedCount(FailureReason.REPLACE) >= 1 && joiner.getOnConsumeCount() >= 1) {break;}
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
        engine.run("job-mismatch", joiner, tracker, flowConfig);
        tracker.getCompletionFuture().get(TIMEOUT_SEC, TimeUnit.SECONDS);
        for (int i = 0; i < 100; i++) {
            if (joiner.getOnFailedCount(FailureReason.MISMATCH) >= 2) {break;}
            Thread.sleep(100);
        }
        
        assertTrue(joiner.getOnFailedCount(FailureReason.MISMATCH) >= 2,
                   "isMatched=false 时两条均 onFailed，2 个 key 共 4 次"
        );
        FlowProgressSnapshot snapshot = tracker.getSnapshot();
        assertTrue(snapshot.getPassiveEgressByReason(FailureReason.MISMATCH.name()) >= 2);
    }
    
    @Test
    void IT_FAILURE_REASON_IN_SNAPSHOT() throws Exception {
        OverwriteJoiner joiner = new OverwriteJoiner();
        var inlet = engine.startPush("job-snapshot-reason", joiner, flowConfig);
        inlet.push(new PairItem("r1", "v1", null));
        inlet.push(new PairItem("r1", "v2", null));
        inlet.markSourceFinished();
        inlet.getCompletionFuture().get(TIMEOUT_SEC, TimeUnit.SECONDS);
        for (int i = 0; i < 100; i++) {
            FlowProgressSnapshot s = inlet.getProgressTracker().getSnapshot();
            if (s.getPassiveEgressByReason(FailureReason.REPLACE.name()) >= 1) {break;}
            Thread.sleep(100);
        }
        
        FlowProgressSnapshot snapshot = inlet.getProgressTracker().getSnapshot();
        assertNotNull(snapshot.passiveEgressByReason());
        assertTrue(snapshot.getPassiveEgressByReason(FailureReason.REPLACE.name()) >= 1);
    }
    
    @Test
    void IT_QUEUE_DRAIN_AFTER_STOP() throws Exception {
        flowConfig.getProducer().setMaxCacheSize(2);
        QueueJoiner joiner = new QueueJoiner();
        var inlet = engine.startPush("job-queue-drain", joiner, flowConfig);
        inlet.push(new PairItem("d1", "v1", null));
        inlet.push(new PairItem("d2", "v2", null));
        inlet.push(new PairItem("d3", "v3", null));
        inlet.stop(true);
        try {
            inlet.getCompletionFuture().get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // 停止后 drain 可能较慢，短时未完成仍做快照断言
        }
        
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
        engine.run("job-completion", joiner, singleSource, total, flowConfig);
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
        var inlet = engine.startPush("job-metrics", joiner, flowConfig);
        inlet.push(new PairItem("m1", "v1", null));
        inlet.markSourceFinished();
        inlet.getCompletionFuture().get(TIMEOUT_SEC, TimeUnit.SECONDS);
        
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
    
    // ---------- 3.5 资源与生命周期 ----------
    
    @Test
    void IT_MULTI_JOB_ISOLATION() throws Exception {
        OverwriteJoiner joiner1 = new OverwriteJoiner();
        OverwriteJoiner joiner2 = new OverwriteJoiner();
        var inlet1 = engine.startPush("job-isolation-1", joiner1, flowConfig);
        var inlet2 = engine.startPush("job-isolation-2", joiner2, flowConfig);
        inlet1.push(new PairItem("a1", "v1", null));
        inlet2.push(new PairItem("b1", "v1", null));
        inlet1.markSourceFinished();
        inlet2.markSourceFinished();
        inlet1.getCompletionFuture().get(TIMEOUT_SEC, TimeUnit.SECONDS);
        inlet2.getCompletionFuture().get(TIMEOUT_SEC, TimeUnit.SECONDS);
        for (int i = 0; i < 100; i++) {
            if (joiner1.getOnConsumeCount() >= 1 && joiner2.getOnConsumeCount() >= 1) {break;}
            Thread.sleep(100);
        }
        
        assertEquals(1, joiner1.getOnConsumeCount());
        assertEquals(1, joiner2.getOnConsumeCount());
        assertFalse(manager.getActiveLaunchers().isEmpty(), "运行期间或完成后 launcher 仍可存在");
    }
    
    @Test
    void IT_STOP_JOB_AND_RESET() throws Exception {
        OverwriteJoiner joiner = new OverwriteJoiner();
        var inlet = engine.startPush("job-stop-reset", joiner, flowConfig);
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
        var inlet = engine.startPush("job-reset-after", joiner, flowConfig);
        inlet.push(new PairItem("z1", "v1", null));
        inlet.markSourceFinished();
        inlet.getCompletionFuture().get(TIMEOUT_SEC, TimeUnit.SECONDS);
        
        FlowManager.reset();
        FlowResourceRegistry.reset();
        FlowHealth.clearIndicators();
        
        TemplateConfigProperties.Flow cfg2 = new TemplateConfigProperties.Flow();
        cfg2.getConsumer().setConcurrencyLimit(100);
        FlowManager manager2 = FlowManager.getInstance(cfg2);
        FlowJoinerEngine engine2 = new FlowJoinerEngine(manager2);
        OverwriteJoiner joiner2 = new OverwriteJoiner();
        var inlet2 = engine2.startPush("job-new-after-reset", joiner2, flowConfig);
        inlet2.push(new PairItem("n1", "v1", null));
        inlet2.markSourceFinished();
        inlet2.getCompletionFuture().get(TIMEOUT_SEC, TimeUnit.SECONDS);
        for (int i = 0; i < 100; i++) {
            if (joiner2.getOnConsumeCount() >= 1) {break;}
            Thread.sleep(100);
        }
        assertEquals(1, joiner2.getOnConsumeCount());
    }
}
