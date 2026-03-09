package com.lrenyi.template.flow.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.FlowTestSupport;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowSourceAdapters;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.context.FlowProgressSnapshot;
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

    private static BoundedTimedFlowStorage<String> createStorage(
            TemplateConfigProperties.Flow config,
            FlowJoiner<String> joiner,
            ProgressTracker progressTracker,
            FlowResourceRegistry registry,
            SimpleMeterRegistry registryMeter,
            String jobId) {
        FlowEgressHandler<String> egressHandler = new FlowEgressHandler<>(joiner, progressTracker, registryMeter);
        FlowFinalizer<String> finalizer = new FlowFinalizer<>(registry, registryMeter, egressHandler);
        return new BoundedTimedFlowStorage<>(
                config,
                joiner,
                progressTracker,
                finalizer,
                egressHandler,
                registry,
                registryMeter,
                jobId
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
    void depositThenDrainRemaining_consumesAllViaSingleConsumed() {
        CollectingJoiner joiner = new CollectingJoiner();
        NoopProgressTracker tracker = new NoopProgressTracker();
        BoundedTimedFlowStorage<String> storage =
                createStorage(flowConfig, joiner, tracker, resourceRegistry, meterRegistry, JOB_ID);

        List<String> data = List.of("a", "b", "c");
        for (String value : data) {
            FlowEntry<String> entry = new FlowEntry<>(value, JOB_ID);
            assertTrue(storage.doDeposit(entry), "deposit: " + value);
        }
        assertEquals(3, storage.size());

        int drained = storage.drainRemainingToFinalizer();
        assertEquals(3, drained);
        assertEquals(3, joiner.getConsumed().size());

        List<ConsumedRecord> consumed = joiner.getConsumed();
        assertEquals("a", consumed.get(0).item);
        assertEquals("b", consumed.get(1).item);
        assertEquals("c", consumed.get(2).item);
        consumed.forEach(r -> assertEquals(EgressReason.SINGLE_CONSUMED, r.reason));

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
        assertEquals(EgressReason.TIMEOUT, consumed.getFirst().reason);

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

    // ---------- 内部辅助 ----------

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
        public void onActiveEgress() {
        }

        @Override
        public void onPassiveEgress(EgressReason reason) {
        }

        @Override
        public FlowProgressSnapshot getSnapshot() {
            return null;
        }

        @Override
        public void setTotalExpected(String jobId, long total) {
        }

        @Override
        public void markSourceFinished(String jobId) {
        }

        @Override
        public boolean isCompleted() {
            return false;
        }

        @Override
        public boolean isCompletionConditionMet() {
            return false;
        }
    }
}
