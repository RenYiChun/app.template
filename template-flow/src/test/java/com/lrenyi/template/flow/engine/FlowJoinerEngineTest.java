package com.lrenyi.template.flow.engine;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowSource;
import com.lrenyi.template.flow.api.FlowSourceAdapters;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.internal.DefaultProgressTracker;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowJoinerEngineTest {

    @AfterEach
    void tearDown() {
        FlowManager.reset();
        FlowResourceRegistry.reset();
    }

    @Test
    void run_fetchesSubSourcesOnCallerThreadEvenWhenProducerThreadsGreaterThanOne() throws Exception {
        TemplateConfigProperties.Flow flow = baseFlow();
        flow.getLimits().getPerJob().setProducerThreads(4);
        FlowManager manager = FlowManager.getInstance(flow, new SimpleMeterRegistry());
        FlowJoinerEngine engine = new FlowJoinerEngine(manager);
        RecordingProvider provider = new RecordingProvider();
        RecordingJoiner joiner = new RecordingJoiner(provider);
        String jobId = "provider-caller-thread";
        DefaultProgressTracker tracker = new DefaultProgressTracker(jobId, manager);
        tracker.setTotalExpected(jobId, 2L);
        long callerThreadId = Thread.currentThread().threadId();

        engine.run(jobId, joiner, tracker, flow);

        awaitCondition(() -> tracker.isCompleted(true), 10_000L);
        assertEquals(2, joiner.consumedCount.get());
        assertEquals(List.of(callerThreadId, callerThreadId), provider.nextThreadIds);
    }

    @Test
    void run_providerFailureShouldForceStopAndUnregisterJob() {
        TemplateConfigProperties.Flow flow = baseFlow();
        FlowManager manager = FlowManager.getInstance(flow, new SimpleMeterRegistry());
        FlowJoinerEngine engine = new FlowJoinerEngine(manager);
        FailingProvider provider = new FailingProvider();
        FailingJoiner joiner = new FailingJoiner(provider);
        String jobId = "provider-failure-cleanup";
        DefaultProgressTracker tracker = new DefaultProgressTracker(jobId, manager);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> engine.run(jobId, joiner, tracker, flow));

        assertEquals("boom", ex.getMessage());
        assertTrue(provider.closed.get(), "provider 失败后仍应执行 close");
        assertTrue(manager.isStopped(jobId), "失败任务应被强制停止");
        assertTrue(manager.getActiveLaunchers().isEmpty(), "失败任务不应残留活跃 launcher");
        assertNotNull(manager.getProgressTracker(jobId), "失败任务注销后仍应保留 tracker 快照");
    }

    private static void awaitCondition(java.util.function.BooleanSupplier condition, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50L);
        }
        assertTrue(condition.getAsBoolean(), "条件在 " + timeoutMs + "ms 内未满足");
    }

    private static TemplateConfigProperties.Flow baseFlow() {
        TemplateConfigProperties.Flow flow = new TemplateConfigProperties.Flow();
        flow.getLimits().getGlobal().setConsumerThreads(8);
        flow.getLimits().getGlobal().setProducerThreads(8);
        flow.getLimits().getGlobal().setStorageCapacity(64);
        flow.getLimits().getPerJob().setProducerThreads(4);
        flow.getLimits().getPerJob().setConsumerThreads(4);
        flow.getLimits().getPerJob().setStorageCapacity(16);
        return flow;
    }

    private static final class RecordingProvider implements FlowSourceProvider<Integer> {
        private final List<Long> nextThreadIds = new CopyOnWriteArrayList<>();
        private int index;

        @Override
        public boolean hasNextSubSource() {
            return index < 2;
        }

        @Override
        public FlowSource<Integer> nextSubSource() {
            nextThreadIds.add(Thread.currentThread().threadId());
            int value = index++;
            return FlowSourceAdapters.fromIterator(List.of(value).iterator(), null);
        }

        @Override
        public void close() {
        }
    }

    private static final class RecordingJoiner implements FlowJoiner<Integer> {
        private final RecordingProvider provider;
        private final AtomicInteger consumedCount = new AtomicInteger();

        private RecordingJoiner(RecordingProvider provider) {
            this.provider = provider;
        }

        @Override
        public Class<Integer> getDataType() {
            return Integer.class;
        }

        @Override
        public FlowSourceProvider<Integer> sourceProvider() {
            return provider;
        }

        @Override
        public String joinKey(Integer item) {
            return String.valueOf(item);
        }

        @Override
        public void onPairConsumed(Integer existing, Integer incoming, String jobId) {
        }

        @Override
        public void onSingleConsumed(Integer item, String jobId, EgressReason reason) {
            consumedCount.incrementAndGet();
        }
    }

    private static final class FailingProvider implements FlowSourceProvider<Integer> {
        private final AtomicBoolean closed = new AtomicBoolean(false);

        @Override
        public boolean hasNextSubSource() {
            return true;
        }

        @Override
        public FlowSource<Integer> nextSubSource() {
            throw new IllegalStateException("boom");
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    private static final class FailingJoiner implements FlowJoiner<Integer> {
        private final FailingProvider provider;

        private FailingJoiner(FailingProvider provider) {
            this.provider = provider;
        }

        @Override
        public Class<Integer> getDataType() {
            return Integer.class;
        }

        @Override
        public FlowSourceProvider<Integer> sourceProvider() {
            return provider;
        }

        @Override
        public String joinKey(Integer item) {
            return String.valueOf(item);
        }

        @Override
        public void onPairConsumed(Integer existing, Integer incoming, String jobId) {
        }

        @Override
        public void onSingleConsumed(Integer item, String jobId, EgressReason reason) {
        }
    }
}
