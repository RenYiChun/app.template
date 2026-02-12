package com.lrenyi.template.core.flow.metrics;

import java.util.Map;
import com.lrenyi.template.core.flow.model.FailureReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class FlowMetricsTest {

    @AfterEach
    void tearDown() {
        FlowMetrics.setCollector(new DefaultFlowMetricsCollector());
    }

    @Test
    void getCollector_returnsDefault() {
        assertNotNull(FlowMetrics.getCollector());
    }

    @Test
    void setCollector_null_doesNotChange() {
        FlowMetricsCollector before = FlowMetrics.getCollector();
        FlowMetrics.setCollector(null);
        assertSame(before, FlowMetrics.getCollector());
    }

    @Test
    void setCollector_custom_useCustom() {
        FlowMetricsCollector custom = new DefaultFlowMetricsCollector();
        FlowMetrics.setCollector(custom);
        assertSame(custom, FlowMetrics.getCollector());
    }

    @Test
    void recordError_recordFailureReason_recordLatency_incrementCounter_recordResourceUsage_getMetrics() {
        FlowMetricsCollector collector = FlowMetrics.getCollector();
        FlowMetrics.recordError("test_error", "job1");
        FlowMetrics.recordFailureReason(FailureReason.TIMEOUT, "job1");
        FlowMetrics.recordLatency("op", 10L);
        FlowMetrics.incrementCounter("count");
        FlowMetrics.incrementCounter("count2", 5L);
        FlowMetrics.recordResourceUsage("cache", 100L);
        Map<String, Object> metrics = FlowMetrics.getMetrics();
        assertNotNull(metrics);
    }
}
