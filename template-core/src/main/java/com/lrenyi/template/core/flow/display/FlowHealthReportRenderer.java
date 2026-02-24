package com.lrenyi.template.core.flow.display;

import java.util.List;
import java.util.Map;
import com.lrenyi.template.core.flow.health.HealthStatus;
import lombok.extern.slf4j.Slf4j;

/**
 * 渲染 Flow 健康检查报告到字符串并输出日志。
 */
@Slf4j
public final class FlowHealthReportRenderer {

    private static final int WIDTH = 80;

    private FlowHealthReportRenderer() {
    }

    /**
     * 根据健康状态和详情渲染报告并输出到日志。
     */
    public static void renderAndLog(HealthStatus status, Map<String, Object> healthDetails) {
        String report = render(status, healthDetails);
        if (status == HealthStatus.UNHEALTHY) {
            log.error(report);
        } else if (status == HealthStatus.DEGRADED) {
            log.warn(report);
        } else {
            log.info(report);
        }
    }

    /**
     * 渲染健康检查报告为字符串。
     */
    @SuppressWarnings("unchecked")
    public static String render(HealthStatus status, Map<String, Object> healthDetails) {
        StringBuilder report = new StringBuilder("\n");
        report.append("=".repeat(WIDTH)).append("\n");
        report.append("Flow Framework Health Report\n");
        report.append("=".repeat(WIDTH)).append("\n");
        report.append(String.format("Overall Status: %s%n", status.name()));
        report.append("-".repeat(WIDTH)).append("\n");

        List<Map<String, Object>> indicators = (List<Map<String, Object>>) healthDetails.get("indicators");
        if (indicators != null && !indicators.isEmpty()) {
            for (Map<String, Object> indicator : indicators) {
                appendIndicator(report, indicator);
            }
        }

        report.append("=".repeat(WIDTH)).append("\n");
        return report.toString();
    }

    private static void appendIndicator(StringBuilder report, Map<String, Object> indicator) {
        String name = (String) indicator.get("name");
        String indicatorStatus = (String) indicator.get("status");
        report.append(String.format("\n[%s] Status: %s%n", name, indicatorStatus));

        Map<String, Object> details = (Map<String, Object>) indicator.get("details");
        if (details != null) {
            details.forEach((key, value) -> {
                if (value != null) {
                    report.append(String.format("  - %s: %s%n", key, value));
                }
            });
        }
    }
}
