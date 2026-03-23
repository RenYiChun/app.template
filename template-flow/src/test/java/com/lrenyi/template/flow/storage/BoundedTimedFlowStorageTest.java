package com.lrenyi.template.flow.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.FlowTestSupport;
import com.lrenyi.template.flow.api.FlowInlet;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowSourceAdapters;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.context.FlowProgressSnapshot;
import com.lrenyi.template.flow.engine.FlowJoinerEngine;
import com.lrenyi.template.flow.internal.FlowEgressHandler;
import com.lrenyi.template.flow.internal.FlowFinalizer;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BoundedTimedFlowStorage 单元测试：通过虚假配置与收集型 Joiner 验证写入与消费行为。
 */
class BoundedTimedFlowStorageTest {

    private static final String JOB_ID = "test-bounded-storage";

    private SimpleMeterRegistry meterRegistry;
    private TemplateConfigProperties.Flow flowConfig;
    private FlowManager flowManager;
    private FlowResourceRegistry resourceRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        flowConfig = fakeFlowConfig();
        flowManager = FlowManager.getInstance(flowConfig, meterRegistry);
        resourceRegistry = flowManager.getResourceRegistry();
    }

    @AfterEach
    void tearDown() {
        FlowTestSupport.cleanup();
        FlowResourceRegistry.reset();
        if (meterRegistry != null) {
            meterRegistry.clear();
        }
    }

    /** 虚假配置：小容量、短 TTL、单 key 单值（覆盖模式）。 */
    private static TemplateConfigProperties.Flow fakeFlowConfig() {
        TemplateConfigProperties.Flow flow = new TemplateConfigProperties.Flow();
        flow.getLimits().getGlobal().setConsumerThreads(10);
        TemplateConfigProperties.Flow.PerJob perJob = flow.getLimits().getPerJob();
        perJob.setStorageCapacity(100);
        perJob.getKeyedCache().setCacheTtlMill(30_000L);
        perJob.getKeyedCache().setMultiValueEnabled(false);
        perJob.getKeyedCache().setMultiValueMaxPerKey(1);
        return flow;
    }

    /** 用于超时驱逐测试：极短 TTL，便于在测试中等待 EvictionCoordinator 触发。 */
    private static TemplateConfigProperties.Flow fakeFlowConfigWithShortTtl(long ttlMs) {
        TemplateConfigProperties.Flow flow = fakeFlowConfig();
        flow.getLimits().getPerJob().getKeyedCache().setCacheTtlMill(ttlMs);
        return flow;
    }

    /** 配对模式测试用：多值、同 key、短 TTL，仅 A-B 可配对。 */
    private static TemplateConfigProperties.Flow fakeFlowConfigForPairing(long ttlMs) {
        TemplateConfigProperties.Flow flow = new TemplateConfigProperties.Flow();
        flow.getLimits().getGlobal().setConsumerThreads(10);
        TemplateConfigProperties.Flow.PerJob perJob = flow.getLimits().getPerJob();
        perJob.setStorageCapacity(100);
        perJob.getKeyedCache().setCacheTtlMill(ttlMs);
        perJob.getKeyedCache().setMultiValueEnabled(true);
        perJob.getKeyedCache().setMultiValueMaxPerKey(10);
        return flow;
    }

    private static BoundedTimedFlowStorage<String> createStorage(
            TemplateConfigProperties.Flow config,
            FlowJoiner<String> joiner,
            ProgressTracker progressTracker,
            FlowResourceRegistry registry,
            SimpleMeterRegistry registryMeter,
            String jobId) {
        FlowEgressHandler<String> egressHandler = new FlowEgressHandler<>(joiner, progressTracker, registryMeter);
        FlowFinalizer<String> finalizer = new FlowFinalizer<>(registry, registryMeter, egressHandler, joiner);
        long scanMs = config.getLimits().getPerJob().getEffectiveEvictionScanIntervalMill(config.getLimits().getGlobal());
        return new BoundedTimedFlowStorage<>(
                config,
                joiner,
                progressTracker,
                finalizer,
                egressHandler, registryMeter,
                jobId,
                scanMs
        );
    }

    @Test
    void createStorageWithFakeConfig_succeeds() {
        CollectingJoiner joiner = new CollectingJoiner();
        NoopProgressTracker tracker = new NoopProgressTracker();

        BoundedTimedFlowStorage<String> storage =
                createStorage(flowConfig, joiner, tracker, resourceRegistry, meterRegistry, JOB_ID);

        assertNotNull(storage);
        assertEquals(0, storage.size());
        assertEquals(100, storage.maxCacheSize());
        assertTrue(storage.supportsDeferredExpiry());

        storage.shutdown();
    }

    @Test
    void overwriteModeSameKey_oldEntryConsumedWithReplaceReason() {
        // 使用固定 key 的 Joiner，使 "old" 与 "new" 落入同一槽位从而触发覆盖
        CollectingJoiner joiner = new CollectingJoinerWithFixedKey("sameKey");
        NoopProgressTracker tracker = new NoopProgressTracker();
        BoundedTimedFlowStorage<String> storage =
                createStorage(flowConfig, joiner, tracker, resourceRegistry, meterRegistry, JOB_ID);

        FlowEntry<String> oldEntry = new FlowEntry<>("old", JOB_ID);
        assertTrue(storage.doDeposit(oldEntry));
        assertEquals(1, storage.size());

        FlowEntry<String> newEntry = new FlowEntry<>("new", JOB_ID);
        assertTrue(storage.doDeposit(newEntry));
        assertEquals(1, storage.size());

        List<ConsumedRecord> consumed = joiner.getConsumed();
        assertEquals(1, consumed.size());
        assertEquals("old", consumed.getFirst().item);
        assertEquals(EgressReason.REPLACE, consumed.getFirst().reason);

        storage.shutdown();
    }

    @Test
    void timeoutEviction_consumesEntryWithTimeoutReason() throws InterruptedException {
        long ttlMs = 150L;
        TemplateConfigProperties.Flow shortTtlConfig = fakeFlowConfigWithShortTtl(ttlMs);
        flowManager = FlowManager.getInstance(shortTtlConfig, meterRegistry);
        resourceRegistry = flowManager.getResourceRegistry();

        CollectingJoiner joiner = new CollectingJoiner();
        NoopProgressTracker tracker = new NoopProgressTracker();
        BoundedTimedFlowStorage<String> storage =
                createStorage(shortTtlConfig, joiner, tracker, resourceRegistry, meterRegistry, JOB_ID);

        storage.doDeposit(new FlowEntry<>("expireMe", JOB_ID));
        assertEquals(1, storage.size());

        // 等待时长随 TTL 变化：TTL 到期 + EvictionCoordinator 调度余量；TTL=10s 时需至少等 10s 才会过期
        long waitMs = ttlMs + 5_000L;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMs);
        while (joiner.getConsumed().isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }

        List<ConsumedRecord> consumed = joiner.getConsumed();
        assertEquals(1, consumed.size(), "应收到 1 条超时驱逐消费（TTL=" + ttlMs + "ms，等待最多 " + waitMs + "ms）");
        assertEquals("expireMe", consumed.getFirst().item);
        assertEquals(EgressReason.SINGLE_CONSUMED, consumed.getFirst().reason,
                "collect 模式（needMatched=false）下 TTL 驱逐使用 SINGLE_CONSUMED");

        storage.shutdown();
    }

    @Test
    void shutdown_drainsRemainingWithShutdownReason() {
        CollectingJoiner joiner = new CollectingJoiner();
        NoopProgressTracker tracker = new NoopProgressTracker();
        BoundedTimedFlowStorage<String> storage =
                createStorage(flowConfig, joiner, tracker, resourceRegistry, meterRegistry, JOB_ID);

        storage.doDeposit(new FlowEntry<>("x", JOB_ID));
        storage.doDeposit(new FlowEntry<>("y", JOB_ID));
        assertEquals(2, storage.size());

        storage.shutdown();

        List<ConsumedRecord> consumed = joiner.getConsumed();
        assertEquals(2, consumed.size());
        assertTrue(consumed.stream().allMatch(r -> r.reason == EgressReason.SHUTDOWN));
        assertTrue(consumed.stream().anyMatch(r -> "x".equals(r.item)));
        assertTrue(consumed.stream().anyMatch(r -> "y".equals(r.item)));
    }

    // ---------- 配对场景：3 条数据仅 2 条可配对，校验 job 结束条件各计数 ----------

    /**
     * 场景1：第 2 条与第 1 条入缓存前就配对成功，第 3 条通过驱逐出缓存。
     * 预期：productionAcquired=3, productionReleased=3, terminated=3, inStorage=0；被动出口含 1 条 TIMEOUT。
     */
    @Test
    void pairingScenario1_secondPairsWithFirst_thirdEvicted_jobCountsCorrect() throws InterruptedException {
        long ttlMs = 150L;
        TemplateConfigProperties.Flow config = fakeFlowConfigForPairing(ttlMs);
        flowManager = FlowManager.getInstance(config, meterRegistry);
        resourceRegistry = flowManager.getResourceRegistry();
        FlowJoinerEngine engine = new FlowJoinerEngine(flowManager);

        PairingCollectingJoiner joiner = new PairingCollectingJoiner();
        FlowInlet<String> inlet = engine.startPush(JOB_ID, joiner, config);

        inlet.push("A");
        // 等待 A 完成存储后再推入 B，确保 B 看到 A 在槽中并配对成功
        awaitCondition(() -> inlet.getProgressTracker().getSnapshot().productionReleased() >= 1, 5_000);
        inlet.push("B"); // B 与 A 配对
        awaitCondition(() -> inlet.getProgressTracker().getSnapshot().productionReleased() >= 2, 5_000);
        inlet.push("C"); // C 留在缓存，等待驱逐
        inlet.markSourceFinished();

        awaitCondition(() -> inlet.getProgressTracker().getSnapshot().terminated() >= 3
                && inlet.getProgressTracker().getSnapshot().inStorage() == 0, 15_000);
        awaitCondition(inlet::isCompleted, 5_000);

        FlowProgressSnapshot snapshot = inlet.getProgressTracker().getSnapshot();
        assertEquals(3, snapshot.productionAcquired(), "productionAcquired");
        assertEquals(3, snapshot.productionReleased(), "productionReleased");
        assertEquals(3, snapshot.terminated(), "terminated");
        assertEquals(0, snapshot.inStorage(), "inStorage");
        assertEquals(1, joiner.getPairConsumedCount(), "配对消费次数");
    }

    /**
     * 场景2：第 3 条才与缓存中某一条配对成功，未配对的这条在生产线程内被 CLEARED_AFTER_PAIR_SUCCESS 移除。
     * 预期：productionAcquired=3, productionReleased=3, terminated=3, inStorage=0；被动出口含 1 条 CLEARED_AFTER_PAIR_SUCCESS。
     */
    @Test
    void pairingScenario2_thirdPairsWithCached_oneClearedAfterPair_jobCountsCorrect() throws InterruptedException {
        TemplateConfigProperties.Flow config = fakeFlowConfigForPairing(30_000L);
        flowManager = FlowManager.getInstance(config, meterRegistry);
        resourceRegistry = flowManager.getResourceRegistry();
        FlowJoinerEngine engine = new FlowJoinerEngine(flowManager);

        PairingCollectingJoiner joiner = new PairingCollectingJoiner();
        FlowInlet<String> inlet = engine.startPush(JOB_ID + "-sc2", joiner, config);

        inlet.push("A");
        awaitCondition(() -> inlet.getProgressTracker().getSnapshot().productionReleased() >= 1, 5_000);
        inlet.push("C");
        // 等待 A 与 C 都在槽中后，再推入 B，确保 B 匹配 A 并将 C 以 CLEARED_AFTER_PAIR_SUCCESS 清除
        awaitCondition(() -> inlet.getProgressTracker().getSnapshot().productionReleased() >= 2, 5_000);
        inlet.push("B"); // B 与 A 配对，C 被 CLEARED_AFTER_PAIR_SUCCESS
        inlet.markSourceFinished();

        awaitCondition(() -> inlet.getProgressTracker().getSnapshot().terminated() >= 3
                && inlet.getProgressTracker().getSnapshot().inStorage() == 0, 10_000);
        awaitCondition(inlet::isCompleted, 5_000);

        FlowProgressSnapshot snapshot = inlet.getProgressTracker().getSnapshot();
        assertEquals(3, snapshot.productionAcquired(), "productionAcquired");
        assertEquals(3, snapshot.productionReleased(), "productionReleased");
        assertEquals(3, snapshot.terminated(), "terminated");
        assertEquals(0, snapshot.inStorage(), "inStorage");
        assertEquals(1, joiner.getPairConsumedCount(), "配对消费次数");
    }

    @Test
    void pairingScenario2_clearedEntryShouldNotBeEvictedAgain_storageGaugeStaysNonNegative() throws InterruptedException {
        long ttlMs = 150L;
        String jobId = JOB_ID + "-sc2-storage";
        TemplateConfigProperties.Flow config = fakeFlowConfigForPairing(ttlMs);
        flowManager = FlowManager.getInstance(config, meterRegistry);
        resourceRegistry = flowManager.getResourceRegistry();
        FlowJoinerEngine engine = new FlowJoinerEngine(flowManager);

        PairingCollectingJoiner joiner = new PairingCollectingJoiner();
        FlowInlet<String> inlet = engine.startPush(jobId, joiner, config);

        inlet.push("A");
        awaitCondition(() -> inlet.getProgressTracker().getSnapshot().productionReleased() >= 1, 5_000);
        inlet.push("C");
        awaitCondition(() -> inlet.getProgressTracker().getSnapshot().productionReleased() >= 2, 5_000);
        inlet.push("B");

        FlowStorage<?> storage = flowManager.getActiveLauncher(jobId).getStorage();
        awaitCondition(() -> storage.size() == 0, 5_000);

        Thread.sleep(ttlMs + 1_000L);

        assertEquals(0, storage.size(), "已 CLEARED_AFTER_PAIR_SUCCESS 的条目不应在后续 TTL 再次扣减 storage.size()");
    }

    /**
     * 场景3：3 条数据不入缓存时配对，而是全部进缓存后由驱逐时再配对（A-B 在 processEvictedSlot 中配对，C 以 TIMEOUT 离库）。
     * 校验 job 结束条件各计数：productionAcquired=3, productionReleased=3, terminated=3, inStorage=0；
     * 被动出口含 1 条 TIMEOUT，且有一次配对消费。
     */
    @Test
    void pairingScenario3_allInCache_thenPairOnEviction_jobCountsCorrect() throws InterruptedException {
        long ttlMs = 150L;
        TemplateConfigProperties.Flow config = fakeFlowConfigForPairing(ttlMs);
        flowManager = FlowManager.getInstance(config, meterRegistry);
        resourceRegistry = flowManager.getResourceRegistry();
        FlowJoinerEngine engine = new FlowJoinerEngine(flowManager);

        PairOnlyOnEvictionCollectingJoiner joiner = new PairOnlyOnEvictionCollectingJoiner();
        FlowInlet<String> inlet = engine.startPush(JOB_ID + "-sc3", joiner, config);

        joiner.setAllowPairing(false);
        inlet.push("A");
        inlet.push("B");
        inlet.push("C");
        // 等待 3 条数据全部完成 deposit（进入 storage）后，再允许配对，确保驱逐时才发生 A-B 配对
        awaitCondition(() -> inlet.getProgressTracker().getSnapshot().productionReleased() >= 3, 5_000);
        joiner.setAllowPairing(true);
        inlet.markSourceFinished();

        awaitCondition(() -> inlet.getProgressTracker().getSnapshot().terminated() >= 3
                && inlet.getProgressTracker().getSnapshot().inStorage() == 0, 15_000);
        awaitCondition(inlet::isCompleted, 5_000);

        FlowProgressSnapshot snapshot = inlet.getProgressTracker().getSnapshot();
        assertEquals(3, snapshot.productionAcquired(), "productionAcquired");
        assertEquals(3, snapshot.productionReleased(), "productionReleased");
        assertEquals(3, snapshot.terminated(), "terminated");
        assertEquals(0, snapshot.inStorage(), "inStorage");
        assertEquals(1, joiner.getPairConsumedCount(), "驱逐时应有 1 次配对消费");
    }

    private static void awaitCondition(java.util.function.BooleanSupplier condition, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(condition.getAsBoolean(), "条件在 " + timeoutMs + "ms 内未满足");
    }

    // ---------- 内部辅助 ----------

    /** 仅 "A" 与 "B" 可配对，同 key 多值。 */
    private static final class PairingCollectingJoiner extends CollectingJoiner {
        private final AtomicLong pairConsumedCount = new AtomicLong(0);

        @Override
        public String joinKey(String item) {
            return "sameKey";
        }

        @Override
        public boolean needMatched() {
            return true;
        }

        @Override
        public boolean isMatched(String existing, String incoming) {
            return ("A".equals(existing) && "B".equals(incoming)) || ("B".equals(existing) && "A".equals(incoming));
        }

        @Override
        public void onPairConsumed(String existing, String incoming, String jobId) {
            pairConsumedCount.incrementAndGet();
        }

        long getPairConsumedCount() {
            return pairConsumedCount.get();
        }
    }

    /**
     * 入缓存时不配对（isMatched 受 allowPairing 控制为 false），仅驱逐时允许 A-B 配对，用于场景3。
     */
    private static final class PairOnlyOnEvictionCollectingJoiner extends CollectingJoiner {
        private final AtomicLong pairConsumedCount = new AtomicLong(0);
        private volatile boolean allowPairing = false;

        void setAllowPairing(boolean allowPairing) {
            this.allowPairing = allowPairing;
        }

        @Override
        public String joinKey(String item) {
            return "sameKey";
        }

        @Override
        public boolean needMatched() {
            return true;
        }

        @Override
        public boolean isMatched(String existing, String incoming) {
            if (!allowPairing) {
                return false;
            }
            return ("A".equals(existing) && "B".equals(incoming)) || ("B".equals(existing) && "A".equals(incoming));
        }

        @Override
        public void onPairConsumed(String existing, String incoming, String jobId) {
            pairConsumedCount.incrementAndGet();
        }

        long getPairConsumedCount() {
            return pairConsumedCount.get();
        }
    }

    /** 统计各回调次数，用于校验 job 结束条件中的计数。 */
    private static final class CountingProgressTracker implements ProgressTracker {
        private final String jobId;
        private final AtomicLong productionAcquired = new AtomicLong(0);
        private final AtomicLong productionReleased = new AtomicLong(0);
        private final AtomicLong activeConsumers = new AtomicLong(0);
        private final AtomicLong terminated = new AtomicLong(0);
        private volatile Supplier<Long> inStorageSupplier = () -> 0L;

        CountingProgressTracker(String jobId) {
            this.jobId = jobId;
        }

        void setInStorageSupplier(Supplier<Long> inStorageSupplier) {
            this.inStorageSupplier = inStorageSupplier != null ? inStorageSupplier : () -> 0L;
        }

        @Override
        public void onProductionAcquired() {
            productionAcquired.incrementAndGet();
        }

        @Override
        public void onProductionReleased() {
            productionReleased.incrementAndGet();
        }

        @Override
        public void onConsumerAcquired() {
            activeConsumers.incrementAndGet();
        }

        @Override
        public void onConsumerReleased(String jobId) {
            activeConsumers.decrementAndGet();
            terminated.incrementAndGet();
        }

        @Override
        public void onTerminated(int count) {
            for (int i = 0; i < count; i++) {
                terminated.incrementAndGet();
            }
        }

        @Override
        public FlowProgressSnapshot getSnapshot() {
            long inStorage = inStorageSupplier.get();
            return new FlowProgressSnapshot(jobId,
                                            -1L,
                                            productionAcquired.get(),
                                            productionReleased.get(),
                                            activeConsumers.get(),
                                            inStorage,
                                            terminated.get(),
                                            System.currentTimeMillis(),
                                            0L
            );
        }

        @Override
        public void setTotalExpected(String jobId, long total) {
        }

        @Override
        public void markSourceFinished(String jobId, boolean status) {
        }

        @Override
        public boolean isCompleted(boolean status) {
            return false;
        }

        @Override
        public boolean isCompletionConditionMet() {
            return false;
        }
    }

    private static final class ConsumedRecord {
        final String item;
        final String jobId;
        final EgressReason reason;

        ConsumedRecord(String item, String jobId, EgressReason reason) {
            this.item = item;
            this.jobId = jobId;
            this.reason = reason;
        }
    }

    private static class CollectingJoiner implements FlowJoiner<String> {
        private final List<ConsumedRecord> consumed = new CopyOnWriteArrayList<>();

        @Override
        public Class<String> getDataType() {
            return String.class;
        }

        @Override
        public FlowSourceProvider<String> sourceProvider() {
            return FlowSourceAdapters.emptyProvider();
        }

        @Override
        public String joinKey(String item) {
            return item;
        }

        @Override
        public void onPairConsumed(String existing, String incoming, String jobId) {
            // 本测试仅用单条消费
        }

        @Override
        public void onSingleConsumed(String item, String jobId, EgressReason reason) {
            consumed.add(new ConsumedRecord(item, jobId, reason));
        }

        @Override
        public boolean needMatched() {
            return false;
        }

        List<ConsumedRecord> getConsumed() {
            return new ArrayList<>(consumed);
        }
    }

    /** 固定 joinKey，用于测试同 key 覆盖（REPLACE）。 */
    private static class CollectingJoinerWithFixedKey extends CollectingJoiner {
        private final String fixedKey;

        CollectingJoinerWithFixedKey(String fixedKey) {
            this.fixedKey = fixedKey;
        }

        @Override
        public String joinKey(String item) {
            return fixedKey;
        }
    }

    private static final class NoopProgressTracker implements ProgressTracker {
        @Override
        public void onProductionAcquired() {
        }

        @Override
        public void onProductionReleased() {
        }

        @Override
        public void onConsumerAcquired() {
        }

        @Override
        public void onConsumerReleased(String jobId) {
        }

        @Override
        public void onTerminated(int count) {
        }

        @Override
        public FlowProgressSnapshot getSnapshot() {
            return null;
        }

        @Override
        public void setTotalExpected(String jobId, long total) {
        }

        @Override
        public void markSourceFinished(String jobId, boolean status) {
        }

        @Override
        public boolean isCompleted(boolean status) {
            return false;
        }

        @Override
        public boolean isCompletionConditionMet() {
            return false;
        }
    }
}
