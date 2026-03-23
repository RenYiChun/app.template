package com.lrenyi.template.flow.it;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowPipeline;
import com.lrenyi.template.flow.api.FlowSourceAdapters;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.model.EgressReason;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
            .nextStage(Integer.class, new IntegerPassThroughJoiner(), (Integer i) -> i % 2 == 0 ? List.of(i) : List.of()) // 过滤奇数
            .fork(
                // 分支 A：直接乘 10 并计数
                (FlowPipeline.Builder<Integer> b) -> b.nextStage(Integer.class, new IntegerPassThroughJoiner(), (Integer i) -> List.of(i * 10))
                      .sink(Integer.class, (Integer i, String jobId) -> sinkCountA.incrementAndGet()),
                // 分支 B：攒批 10 个后计数
                (FlowPipeline.Builder<Integer> b) -> b.aggregate(10, 5, TimeUnit.SECONDS)
                      .sink((BiConsumer<List<Integer>, String>) (list, jobId) -> sinkCountB.addAndGet(list.size()))
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
