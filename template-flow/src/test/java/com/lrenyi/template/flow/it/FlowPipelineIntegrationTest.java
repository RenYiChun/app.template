package com.lrenyi.template.flow.it;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowInlet;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowPipeline;
import com.lrenyi.template.flow.api.EmbeddedBatchSpec;
import com.lrenyi.template.flow.api.NextMapSpec;
import com.lrenyi.template.flow.api.NextStageSpec;
import com.lrenyi.template.flow.api.FlowSourceAdapters;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.model.EgressReason;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管道功能集成测试
 */
@Slf4j
public class FlowPipelineIntegrationTest {

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
            .nextStage(NextStageSpec.of(Integer.class, new IntegerPassThroughJoiner(),
                    (Integer i) -> i % 2 == 0 ? List.of(i) : List.of())) // 过滤奇数
            .fork(
                // 分支 A：直接乘 10 并计数
                (FlowPipeline.Builder<Integer> b) -> b.nextStage(NextStageSpec.of(Integer.class, new IntegerPassThroughJoiner(),
                        (Integer i) -> List.of(i * 10)))
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

        // 等待整个管道完成（最后一阶段完成）
        long start = System.currentTimeMillis();
        while (!pipeline.getProgressTracker().isCompleted(true) && System.currentTimeMillis() - start < 10000) {
            Thread.sleep(100);
        }

        boolean completed = pipeline.getProgressTracker().isCompleted(true);
        var snapshot = pipeline.getProgressTracker().getSnapshot();
        log.info("Pipeline completed: {}, progress: {}", completed, snapshot);

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
                .nextStage(NextStageSpec.of(Integer.class, new IntegerPassThroughJoiner(),
                        (Integer i) -> i % 2 == 0 ? List.of(i) : List.of()))
                .nextMap(NextMapSpec.of(
                                Integer.class,
                                Integer.class,
                                i -> i,
                                100L,
                                TimeUnit.MILLISECONDS),
                        EmbeddedBatchSpec.of(10, 5, TimeUnit.SECONDS))
                .sink((List<Integer> list, String jobId) -> sinkCountB.addAndGet(list.size()));

        List<Integer> data = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            data.add(i);
        }

        pipeline.run(FlowSourceAdapters.fromIterator(data.iterator(), null), config);

        long start = System.currentTimeMillis();
        while (!pipeline.getProgressTracker().isCompleted(true) && System.currentTimeMillis() - start < 10000) {
            Thread.sleep(100);
        }

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
                .nextStage(NextStageSpec.of(Integer.class, new IntegerPassThroughJoiner(),
                        (Integer i) -> i % 2 == 0 ? List.of(i) : List.of()))
                .nextStage(NextStageSpec.of(Integer.class, new IntegerPassThroughJoiner(), i -> List.of(i)),
                        EmbeddedBatchSpec.of(10, 5, TimeUnit.SECONDS))
                .sink((List<Integer> list, String jobId) -> sinkCountB.addAndGet(list.size()));

        List<Integer> data = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            data.add(i);
        }

        pipeline.run(FlowSourceAdapters.fromIterator(data.iterator(), null), config);

        long start = System.currentTimeMillis();
        while (!pipeline.getProgressTracker().isCompleted(true) && System.currentTimeMillis() - start < 10000) {
            Thread.sleep(100);
        }

        assertEquals(50, sinkCountB.get(), "nextStage 内嵌攒批应收到 50 条偶数");
        assertTrue(pipeline.getProgressTracker().isCompleted(true), "管道应当在 10 秒内完成");
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
        builder.nextStage(NextStageSpec.of(Integer.class, new IntegerPassThroughJoiner(), i -> List.of(i + 100)))
                .sink((Integer i, String jobId) -> { });

        firstPipeline.run(FlowSourceAdapters.fromIterator(List.of(1, 2, 3).iterator(), null), config);

        long start = System.currentTimeMillis();
        while (!firstPipeline.getProgressTracker().isCompleted(true) && System.currentTimeMillis() - start < 5000) {
            Thread.sleep(50);
        }

        assertEquals(3, sinkCount.get(), "已构建 pipeline 不应被 builder 后续追加的阶段污染");
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
}
