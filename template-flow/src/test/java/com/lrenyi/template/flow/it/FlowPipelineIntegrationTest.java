package com.lrenyi.template.flow.it;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowInlet;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowPipeline;
import com.lrenyi.template.flow.api.FlowSource;
import com.lrenyi.template.flow.api.EmbeddedBatchSpec;
import com.lrenyi.template.flow.api.NextMapSpec;
import com.lrenyi.template.flow.api.NextStageSpec;
import com.lrenyi.template.flow.api.FlowSourceAdapters;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.internal.AsyncEgressConsumeStrategy;
import com.lrenyi.template.flow.internal.InlineEgressConsumeStrategy;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.model.FlowConsumeExecutionMode;
import com.lrenyi.template.flow.model.EgressReason;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管道功能集成测试
 */
@Slf4j
public class FlowPipelineIntegrationTest {

    @BeforeEach
    void resetFlowManagerBeforeEach() {
        FlowManager.reset();
    }

    @AfterEach
    void resetFlowManagerAfterEach() {
        FlowManager.reset();
    }

    private void awaitUntil(String message, long timeoutMs, java.util.function.BooleanSupplier condition)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertTrue(condition.getAsBoolean(), message);
    }

    @Test
    public void testComplexPipeline() throws Exception {
        TemplateConfigProperties.Flow config = new TemplateConfigProperties.Flow();
        // 限制并发加速测试且易于观察
        config.getLimits().getGlobal().setConsumerThreads(8);
        FlowManager flowManager = FlowManager.getInstance(config, new SimpleMeterRegistry());

        AtomicLong sinkCountA = new AtomicLong();
        AtomicLong sinkCountB = new AtomicLong();

        FlowPipeline.Builder<Integer> builder = FlowPipeline.builder("test-pipeline", Integer.class, flowManager);
        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) builder
            .nextStage(NextStageSpec.<Integer, Integer>builder(Integer.class, new IntegerPassThroughJoiner(),
                    (Integer i) -> i % 2 == 0 ? List.of(i) : List.of()).build()) // 过滤奇数
            .fork(
                // 分支 A：直接乘 10 并计数
                (FlowPipeline.Builder<Integer> b) -> b.nextStage(NextStageSpec.<Integer, Integer>builder(
                        Integer.class, new IntegerPassThroughJoiner(), (Integer i) -> List.of(i * 10)).build())
                      .sink(Integer.class, (Integer i, String jobId) -> sinkCountA.incrementAndGet()),
                // 分支 B：攒批 10 个后计数
                (FlowPipeline.Builder<Integer> b) -> b.aggregate(10, 5, TimeUnit.SECONDS)
                      .sink((list, jobId) -> sinkCountB.addAndGet(list.size()))
            );

        List<Integer> data = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            data.add(i);
        }

        log.info("Starting pipeline execution...");
        pipeline.run(FlowSourceAdapters.fromIterator(data.iterator(), null), config);

        awaitUntil("分支 A/B 应在时限内完成消费", 15_000L,
                () -> sinkCountA.get() == 50 && sinkCountB.get() == 50 && pipeline.getProgressTracker().isCompleted(true));

        boolean completed = pipeline.getProgressTracker().isCompleted(true);
        assertEquals(50, sinkCountA.get(), "分支 A 应收到 50 个偶数");
        assertEquals(50, sinkCountB.get(), "分支 B 应收到 50 个偶数（通过聚合阶段）");
        assertTrue(completed, "管道应当在 10 秒内完成");
    }

    /**
     * 与 {@link #testComplexPipeline} 分支 B 语义等价：使用 {@code nextMap(..., batchSize, timeout, unit)} 内嵌攒批，
     * 不单独增加 {@code aggregate} 段。
     */
    @Test
    public void testEmbeddedBatchNextMap() throws Exception {
        TemplateConfigProperties.Flow config = new TemplateConfigProperties.Flow();
        config.getLimits().getGlobal().setConsumerThreads(8);
        FlowManager flowManager = FlowManager.getInstance(config, new SimpleMeterRegistry());

        AtomicLong sinkCountB = new AtomicLong();

        FlowPipeline.Builder<Integer> builder = FlowPipeline.builder("embedded-batch-pipeline", Integer.class, flowManager);
        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) builder
                .nextStage(NextStageSpec.<Integer, Integer>builder(Integer.class, new IntegerPassThroughJoiner(),
                        (Integer i) -> i % 2 == 0 ? List.of(i) : List.of()).build())
                .nextMap(NextMapSpec.<Integer, Integer>builder(
                                Integer.class,
                                Integer.class,
                                i -> i)
                        .consumeInterval(100L, TimeUnit.MILLISECONDS)
                        .build(),
                        EmbeddedBatchSpec.of(10, 5, TimeUnit.SECONDS))
                .sink((List<Integer> list, String jobId) -> sinkCountB.addAndGet(list.size()));

        List<Integer> data = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            data.add(i);
        }

        pipeline.run(FlowSourceAdapters.fromIterator(data.iterator(), null), config);

        awaitUntil("内嵌攒批 nextMap 应在时限内完成消费", 15_000L,
                () -> sinkCountB.get() == 50 && pipeline.getProgressTracker().isCompleted(true));
        assertEquals(50, sinkCountB.get(), "内嵌攒批应收到 50 条偶数（与独立 aggregate 段等价）");
        assertTrue(pipeline.getProgressTracker().isCompleted(true), "管道应当在 10 秒内完成");
    }

    /**
     * 与 {@link #testEmbeddedBatchNextMap} 等价，改用 {@code nextStage(NextStageSpec, EmbeddedBatchSpec)}。
     */
    @Test
    public void testEmbeddedBatchNextStage() throws Exception {
        TemplateConfigProperties.Flow config = new TemplateConfigProperties.Flow();
        config.getLimits().getGlobal().setConsumerThreads(8);
        FlowManager flowManager = FlowManager.getInstance(config, new SimpleMeterRegistry());

        AtomicLong sinkCountB = new AtomicLong();

        FlowPipeline.Builder<Integer> builder = FlowPipeline.builder("embedded-batch-nextstage", Integer.class, flowManager);
        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) builder
                .nextStage(NextStageSpec.<Integer, Integer>builder(Integer.class, new IntegerPassThroughJoiner(),
                        (Integer i) -> i % 2 == 0 ? List.of(i) : List.of()).build())
                .nextStage(NextStageSpec.<Integer, Integer>builder(
                        Integer.class, new IntegerPassThroughJoiner(), i -> List.of(i)).build(),
                        EmbeddedBatchSpec.of(10, 5, TimeUnit.SECONDS))
                .sink((List<Integer> list, String jobId) -> sinkCountB.addAndGet(list.size()));

        List<Integer> data = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            data.add(i);
        }

        pipeline.run(FlowSourceAdapters.fromIterator(data.iterator(), null), config);

        awaitUntil("内嵌攒批 nextStage 应在时限内完成消费", 15_000L,
                () -> sinkCountB.get() == 50 && pipeline.getProgressTracker().isCompleted(true));
        assertEquals(50, sinkCountB.get(), "nextStage 内嵌攒批应收到 50 条偶数");
        assertTrue(pipeline.getProgressTracker().isCompleted(true), "管道应当在 10 秒内完成");
    }

    @Test
    public void testEmbeddedBatchPushModeFlushesTailBatchOnSourceFinished() throws Exception {
        TemplateConfigProperties.Flow config = new TemplateConfigProperties.Flow();
        config.getLimits().getGlobal().setConsumerThreads(8);
        FlowManager flowManager = FlowManager.getInstance(config, new SimpleMeterRegistry());

        AtomicLong sinkCount = new AtomicLong();

        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) FlowPipeline.builder(
                        "embedded-batch-push-tail",
                        Integer.class,
                        flowManager)
                .nextMap(NextMapSpec.<Integer, Integer>builder(Integer.class, Integer.class, i -> i)
                        .consumeInterval(10L, TimeUnit.MILLISECONDS)
                        .build(), EmbeddedBatchSpec.of(100, 1, TimeUnit.MINUTES))
                .sink((List<Integer> list, String jobId) -> sinkCount.addAndGet(list.size()));

        FlowInlet<Integer> inlet = pipeline.startPush(config);
        for (int i = 1; i <= 20; i++) {
            inlet.push(i);
        }
        inlet.markSourceFinished();

        awaitUntil("push 模式尾批数据应在时限内到达 sink", 15_000L, () -> sinkCount.get() == 20);
        assertEquals(20, sinkCount.get(), "push 模式完成时应刷出全部尾批数据");
        awaitUntil("push 模式尾批刷出后管道应完成", 15_000L, () -> pipeline.getProgressTracker().isCompleted(true));
    }

    @Test
    public void testGracefulStopWaitsForEmbeddedBatchTailFlush() throws Exception {
        TemplateConfigProperties.Flow config = new TemplateConfigProperties.Flow();
        config.getLimits().getGlobal().setConsumerThreads(8);
        FlowManager flowManager = FlowManager.getInstance(config, new SimpleMeterRegistry());

        AtomicLong sinkCount = new AtomicLong();

        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) FlowPipeline.builder(
                        "embedded-batch-graceful-stop",
                        Integer.class,
                        flowManager)
                .nextMap(NextMapSpec.<Integer, Integer>builder(Integer.class, Integer.class, i -> i)
                        .consumeInterval(10L, TimeUnit.MILLISECONDS)
                        .build(), EmbeddedBatchSpec.of(100, 1, TimeUnit.MINUTES))
                .sink((List<Integer> list, String jobId) -> sinkCount.addAndGet(list.size()));

        FlowInlet<Integer> inlet = pipeline.startPush(config);
        for (int i = 1; i <= 20; i++) {
            inlet.push(i);
        }
        inlet.markSourceFinished();

        pipeline.stop(false);

        assertEquals(20, sinkCount.get(), "优雅 stop 返回时应已刷出全部尾批数据");
        awaitUntil("优雅 stop 返回后管道最终应完成", 15_000L, () -> pipeline.getProgressTracker().isCompleted(true));
    }

    @Test
    public void testEmbeddedBatchTailFlushSurvivesInterruptedCompletionThread() throws Exception {
        TemplateConfigProperties.Flow config = new TemplateConfigProperties.Flow();
        config.getLimits().getGlobal().setConsumerThreads(8);
        FlowManager flowManager = FlowManager.getInstance(config, new SimpleMeterRegistry());

        AtomicLong sinkCount = new AtomicLong();

        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) FlowPipeline.builder(
                        "embedded-batch-interrupted-completion",
                        Integer.class,
                        flowManager)
                .nextStage(NextStageSpec.<Integer, Integer>builder(
                        Integer.class,
                        new InterruptAfterConsumeJoiner(),
                        i -> List.of(i)).build(), EmbeddedBatchSpec.of(100, 1, TimeUnit.MINUTES))
                .sink((List<Integer> list, String jobId) -> sinkCount.addAndGet(list.size()));

        FlowInlet<Integer> inlet = pipeline.startPush(config);
        for (int i = 1; i <= 20; i++) {
            inlet.push(i);
        }
        inlet.markSourceFinished();

        awaitUntil("完成线程被中断时尾批仍应被刷到 sink", 15_000L, () -> sinkCount.get() == 20);
        awaitUntil("中断完成线程场景下管道仍应完成", 15_000L, () -> pipeline.getProgressTracker().isCompleted(true));
    }

    @Test
    public void testStartPushReturnsSameInletInstance() {
        TemplateConfigProperties.Flow config = new TemplateConfigProperties.Flow();
        config.getLimits().getGlobal().setConsumerThreads(4);
        FlowManager flowManager = FlowManager.getInstance(config, new SimpleMeterRegistry());

        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) FlowPipeline.builder("same-inlet", Integer.class, flowManager)
                .nextStage(new IntegerPassThroughJoiner())
                .sink((Integer i, String jobId) -> { });

        FlowInlet<Integer> inlet1 = pipeline.startPush(config);
        FlowInlet<Integer> inlet2 = pipeline.startPush(config);

        assertSame(inlet1, inlet2, "重复 startPush 应返回同一个入口实例");
    }

    @Test
    public void testBuiltPipelineIsImmutableSnapshotOfBuilder() throws Exception {
        TemplateConfigProperties.Flow config = new TemplateConfigProperties.Flow();
        config.getLimits().getGlobal().setConsumerThreads(4);
        FlowManager flowManager = FlowManager.getInstance(config, new SimpleMeterRegistry());

        AtomicLong sinkCount = new AtomicLong();
        FlowPipeline.Builder<Integer> builder = FlowPipeline.builder("builder-snapshot", Integer.class, flowManager)
                .nextStage(new IntegerPassThroughJoiner());

        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> firstPipeline = (FlowPipeline<Integer>) builder
                .sink((Integer i, String jobId) -> sinkCount.incrementAndGet());

        // 继续基于旧 builder 衍生新定义，不应污染已经 build 的 firstPipeline
        builder.nextStage(NextStageSpec.<Integer, Integer>builder(
                Integer.class, new IntegerPassThroughJoiner(), i -> List.of(i + 100)).build())
                .sink((Integer i, String jobId) -> { });

        firstPipeline.run(FlowSourceAdapters.fromIterator(List.of(1, 2, 3).iterator(), null), config);

        awaitUntil("已构建 pipeline 应在时限内完成消费", 10_000L,
                () -> sinkCount.get() == 3 && firstPipeline.getProgressTracker().isCompleted(true));
        assertEquals(3, sinkCount.get(), "已构建 pipeline 不应被 builder 后续追加的阶段污染");
    }

    @Test
    void testNextMapInlineConsumeModeWiresInlineStrategy() {
        TemplateConfigProperties.Flow config = new TemplateConfigProperties.Flow();
        config.getLimits().getGlobal().setConsumerThreads(4);
        FlowManager flowManager = FlowManager.getInstance(config, new SimpleMeterRegistry());

        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) FlowPipeline.builder("inline-mode", Integer.class, flowManager)
                .nextMap(NextMapSpec.<Integer, Integer>builder(Integer.class, Integer.class, i -> i)
                        .consumeInterval(10L, TimeUnit.MILLISECONDS)
                        .consumeExecutionMode(FlowConsumeExecutionMode.INLINE)
                        .build())
                .sink((Integer i, String jobId) -> { });

        pipeline.startPush(config);

        assertTrue(flowManager.getActiveLaunchers().values().stream()
                .map(launcher -> launcher.getResourceContext().getEgressConsumeStrategy())
                .anyMatch(InlineEgressConsumeStrategy.class::isInstance));

        pipeline.stop(true);
    }

    @Test
    void testNextMapDefaultConsumeModeUsesInlineStrategy() {
        TemplateConfigProperties.Flow config = new TemplateConfigProperties.Flow();
        config.getLimits().getGlobal().setConsumerThreads(4);
        FlowManager flowManager = FlowManager.getInstance(config, new SimpleMeterRegistry());

        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) FlowPipeline.builder("inline-default-mode", Integer.class, flowManager)
                .nextMap(NextMapSpec.<Integer, Integer>builder(Integer.class, Integer.class, i -> i)
                        .consumeInterval(10L, TimeUnit.MILLISECONDS)
                        .build())
                .sink((Integer i, String jobId) -> { });

        pipeline.startPush(config);

        assertTrue(flowManager.getActiveLaunchers().values().stream()
                .map(launcher -> launcher.getResourceContext().getEgressConsumeStrategy())
                .anyMatch(InlineEgressConsumeStrategy.class::isInstance));

        pipeline.stop(true);
    }

    @Test
    void testNextMapExplicitAsyncConsumeModeStillUsesAsyncStrategy() {
        TemplateConfigProperties.Flow config = new TemplateConfigProperties.Flow();
        config.getLimits().getGlobal().setConsumerThreads(4);
        FlowManager flowManager = FlowManager.getInstance(config, new SimpleMeterRegistry());

        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) FlowPipeline.builder("async-override-mode", Integer.class, flowManager)
                .nextMap(NextMapSpec.<Integer, Integer>builder(Integer.class, Integer.class, i -> i)
                        .consumeInterval(10L, TimeUnit.MILLISECONDS)
                        .consumeExecutionMode(FlowConsumeExecutionMode.ASYNC)
                        .build())
                .sink((Integer i, String jobId) -> { });

        pipeline.startPush(config);

        assertTrue(flowManager.getActiveLaunchers().values().stream()
                .map(launcher -> launcher.getResourceContext().getEgressConsumeStrategy())
                .allMatch(AsyncEgressConsumeStrategy.class::isInstance));
        assertInstanceOf(AsyncEgressConsumeStrategy.class,
                flowManager.getActiveLaunchers().values().iterator().next().getResourceContext().getEgressConsumeStrategy());

        pipeline.stop(true);
    }

    @Test
    void testRunFailureStopsPipelineAndReleasesAllLaunchers() throws Exception {
        TemplateConfigProperties.Flow config = new TemplateConfigProperties.Flow();
        config.getLimits().getGlobal().setConsumerThreads(4);
        FlowManager flowManager = FlowManager.getInstance(config, new SimpleMeterRegistry());

        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) FlowPipeline.builder("pipeline-run-failure",
                        Integer.class,
                        flowManager)
                .nextStage(new IntegerPassThroughJoiner())
                .sink((Integer i, String jobId) -> { });

        FlowSource<Integer> failingSource = new FlowSource<>() {
            private boolean first = true;

            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public Integer next() {
                if (first) {
                    first = false;
                    return 1;
                }
                throw new IllegalStateException("source failed");
            }

            @Override
            public void close() {
            }
        };

        RuntimeException ex = assertThrows(RuntimeException.class, () -> pipeline.run(failingSource, config));

        assertInstanceOf(IllegalStateException.class, ex.getCause());
        awaitUntil("异常后不应残留 pipeline launcher", 10_000L, () -> flowManager.getActiveLaunchers().isEmpty());
    }

    private static class IntegerPassThroughJoiner implements FlowJoiner<Integer> {
        @Override
        public Class<Integer> getDataType() {
            return Integer.class;
        }

        @Override
        public String joinKey(Integer item) {
            return String.valueOf(item);
        }

        @Override
        public void onSingleConsumed(Integer item, String jobId, EgressReason reason) {
        }

        @Override
        public void onPairConsumed(Integer existing, Integer incoming, String jobId) {
        }

        @Override
        public FlowSourceProvider<Integer> sourceProvider() {
            return null;
        }
    }

    private static class InterruptAfterConsumeJoiner extends IntegerPassThroughJoiner {
        @Override
        public void onSingleConsumed(Integer item, String jobId, EgressReason reason) {
            if (reason == EgressReason.SINGLE_CONSUMED) {
                Thread.currentThread().interrupt();
            }
        }
    }

}
