package com.lrenyi.template.flow.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class FlowMetricTagsTest {

    @Test
    void shouldResolveDisplayFieldsForRootJob() {
        FlowMetricTags tags = FlowMetricTags.resolve("job-001", "订单对账");

        assertEquals("job-001", tags.rootJobId());
        assertEquals("订单对账", tags.rootJobDisplayName());
        assertEquals("root", tags.stageKey());
        assertEquals("root", tags.stageName());
        assertEquals("root", tags.stageDisplayName());
        assertEquals("订单对账", tags.displayName());
    }

    @Test
    void shouldResolveDisplayFieldsForStageJob() {
        FlowMetricTags tags = FlowMetricTags.resolve("job-001:0:fork:1-回流:2", "订单对账:0:fork:1-回流:2");

        assertEquals("job-001", tags.rootJobId());
        assertEquals("订单对账", tags.rootJobDisplayName());
        assertEquals("0/fork/1/2", tags.stageKey());
        assertEquals("回流:stage-2", tags.stageName());
        assertEquals("回流:stage-2", tags.stageDisplayName());
        assertEquals("订单对账", tags.displayName());
    }

    @Test
    void shouldPreferExplicitStageDisplayNameWhenProvided() {
        FlowMetricTags tags = FlowMetricTags.resolve("job-001:3", "订单对账:3", "明细归并");

        assertEquals("job-001", tags.rootJobId());
        assertEquals("订单对账", tags.rootJobDisplayName());
        assertEquals("3", tags.stageKey());
        assertEquals("stage-3", tags.stageName());
        assertEquals("明细归并", tags.stageDisplayName());
        assertEquals("订单对账", tags.displayName());
    }

    @Test
    void shouldFallbackToOriginalValuesWhenDisplayNameMissing() {
        FlowMetricTags tags = FlowMetricTags.resolve("job-001:3", "job-001:3");

        assertEquals("job-001", tags.rootJobId());
        assertEquals("job-001", tags.rootJobDisplayName());
        assertEquals("3", tags.stageKey());
        assertEquals("stage-3", tags.stageName());
        assertEquals("stage-3", tags.stageDisplayName());
        assertEquals("job-001", tags.displayName());
    }
}
