package com.lrenyi.template.flow.storage;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.internal.FlowEgressHandler;
import com.lrenyi.template.flow.internal.FlowFinalizer;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.model.FlowConsumeExecutionMode;
import com.lrenyi.template.flow.pipeline.MapOperatorJoiner;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class QueueFlowStorageTest {

    @Test
    void inlineModeRegistersWorkerLimitMetricFromConfiguredEgressWorkers() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ProgressTracker progressTracker = Mockito.mock(ProgressTracker.class);
        Mockito.when(progressTracker.getMetricJobId()).thenReturn("inline-metric-mode:0");
        Mockito.when(progressTracker.getStageDisplayName()).thenReturn("stage-0");

        FlowResourceRegistry resourceRegistry = Mockito.mock(FlowResourceRegistry.class);
        MapOperatorJoiner<Integer> joiner = new MapOperatorJoiner<>(Integer.class, TimeUnit.MILLISECONDS.toMillis(10));
        FlowEgressHandler<Integer> egressHandler = new FlowEgressHandler<>(joiner, progressTracker, meterRegistry);
        FlowFinalizer<Integer> finalizer = new FlowFinalizer<>(resourceRegistry, meterRegistry, egressHandler, joiner);

        QueueFlowStorage<Integer> storage = new QueueFlowStorage<>(100,
                joiner,
                progressTracker,
                finalizer,
                egressHandler,
                "inline-metric-mode:0",
                10L,
                FlowConsumeExecutionMode.INLINE,
                3,
                meterRegistry);
        try {
            Gauge workerLimitGauge = meterRegistry.getMeters().stream()
                    .filter(meter -> meter.getId().getName().equals(FlowMetricNames.EGRESS_WORKER_LIMIT))
                    .filter(meter -> "inline".equals(meter.getId().getTag(FlowMetricNames.TAG_CONSUME_EXECUTION_MODE)))
                    .filter(meter -> "inline-metric-mode:0".equals(meter.getId().getTag(FlowMetricNames.TAG_JOB_ID)))
                    .filter(Gauge.class::isInstance)
                    .map(Gauge.class::cast)
                    .findFirst()
                    .orElse(null);

            assertNotNull(workerLimitGauge, "inline 模式应注册 egress worker limit 指标");
            assertEquals(3.0, workerLimitGauge.value());
        } finally {
            storage.shutdown();
        }
    }

    @Test
    void inlineModeSetsActiveConsumersOnStartAndResetsToZeroWhenWorkersStop() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ProgressTracker progressTracker = Mockito.mock(ProgressTracker.class);
        Mockito.when(progressTracker.getMetricJobId()).thenReturn("inline-active-consumers:0");
        Mockito.when(progressTracker.getStageDisplayName()).thenReturn("stage-0");

        FlowResourceRegistry resourceRegistry = Mockito.mock(FlowResourceRegistry.class);
        MapOperatorJoiner<Integer> joiner = new MapOperatorJoiner<>(Integer.class, TimeUnit.MILLISECONDS.toMillis(10));
        FlowEgressHandler<Integer> egressHandler = new FlowEgressHandler<>(joiner, progressTracker, meterRegistry);
        FlowFinalizer<Integer> finalizer = new FlowFinalizer<>(resourceRegistry, meterRegistry, egressHandler, joiner);

        QueueFlowStorage<Integer> storage = new QueueFlowStorage<>(100,
                joiner,
                progressTracker,
                finalizer,
                egressHandler,
                "inline-active-consumers:0",
                10L,
                FlowConsumeExecutionMode.INLINE,
                3,
                meterRegistry);
        try {
            verify(progressTracker).setActiveConsumers(3);
            verify(progressTracker, timeout(1_000)).setActiveConsumers(0);
            verify(progressTracker, atLeastOnce()).setActiveConsumers(anyLong());
        } finally {
            storage.shutdown();
        }
    }
}
