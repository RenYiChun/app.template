package com.lrenyi.template.core.flow.display;

import java.util.List;
import java.util.Map;
import com.lrenyi.template.core.flow.health.HealthStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FlowHealthReportRenderer 单元测试
 */
class FlowHealthReportRendererTest {

    @Test
    void render_emptyDetails_containsHeader() {
        String report = FlowHealthReportRenderer.render(HealthStatus.HEALTHY, Map.of());
        assertNotNull(report);
        assertTrue(report.contains("Flow Framework Health Report"));
        assertTrue(report.contains("Overall Status: HEALTHY"));
    }

    @Test
    void render_withIndicators_includesIndicatorDetails() {
        Map<String, Object> details = Map.of(
                "indicators", List.of(
                        Map.of(
                                "name", "TestIndicator",
                                "status", "HEALTHY",
                                "details", Map.of("key", "value")
                        )
                )
        );
        String report = FlowHealthReportRenderer.render(HealthStatus.HEALTHY, details);
        assertTrue(report.contains("[TestIndicator]"));
        assertTrue(report.contains("Status: HEALTHY"));
        assertTrue(report.contains("value"));
    }
}
