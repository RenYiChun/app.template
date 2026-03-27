package com.lrenyi.template.flow.it;

import java.util.List;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.FlowTestSupport;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowPipeline;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.api.NextStageSpec;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证 Pipeline 各阶段可覆盖 {@code limits.per-job.storage-capacity}，且基底 flow 不被修改。
 */
class PipelinePerStageStorageCapacityTest {

    @AfterEach
    void tearDown() {
        FlowTestSupport.cleanup();
        FlowResourceRegistry.reset();
    }

    @Test
    void perStageStorage_overridesPerLauncher_preservesBaseFlow() {
        FlowTestSupport.cleanup();
        FlowResourceRegistry.reset();

        TemplateConfigProperties.Flow base = FlowTestSupport.defaultFlowConfig();
        int baseCap = base.getLimits().getPerJob().getStorageCapacity();

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        FlowManager fm = FlowManager.getInstance(base, registry);

        @SuppressWarnings("unchecked")
        FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) FlowPipeline.builder("pss-cap-job", Integer.class, fm)
                .nextStage(NextStageSpec.<Integer, Integer>builder(
                        Integer.class, new IntPassThroughJoiner(), i -> List.of(i))
                        .storageCapacity(111)
                        .build())
                .nextStage(NextStageSpec.<Integer, Integer>builder(
                        Integer.class, new IntPassThroughJoiner(), i -> List.of(i))
                        .storageCapacity(222)
                        .build())
                .sink(Integer.class, (i, jobId) -> { }, 333);

        pipeline.startPush(base);

        assertEquals(baseCap, base.getLimits().getPerJob().getStorageCapacity());
        assertEquals(111, fm.getActiveLauncher("pss-cap-job:0").getFlow().getLimits().getPerJob().getStorageCapacity());
        assertEquals(222, fm.getActiveLauncher("pss-cap-job:1").getFlow().getLimits().getPerJob().getStorageCapacity());
        assertEquals(333, fm.getActiveLauncher("pss-cap-job:2").getFlow().getLimits().getPerJob().getStorageCapacity());
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
