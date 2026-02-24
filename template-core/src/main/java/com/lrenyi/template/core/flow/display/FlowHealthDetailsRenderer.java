package com.lrenyi.template.core.flow.display;

import java.util.List;
import java.util.Map;

/**
 * 渲染 Flow 健康检查详细信息的工具类。
 */
public final class FlowHealthDetailsRenderer {

    private FlowHealthDetailsRenderer() {
    }

    @SuppressWarnings("unchecked")
    public static void render(StringBuilder sb, Map<String, Object> healthDetails) {
        List<Map<String, Object>> indicators =
                (List<Map<String, Object>>) healthDetails.get("indicators");

        if (indicators == null || indicators.isEmpty()) {
            return;
        }

        for (Map<String, Object> indicator : indicators) {
            String name = (String) indicator.get("name");
            String status = (String) indicator.get("status");
            Map<String, Object> details = (Map<String, Object>) indicator.get("details");

            sb.append(String.format("[Health Indicator: %s] Status: %s%n", name, status));

            if (details != null) {
                appendSemaphoreUsage(sb, details);
                appendActiveJobs(sb, details);
                appendResourceLeak(sb, details);
                appendRegistryStatus(sb, details);
            }
        }
    }

    private static void appendSemaphoreUsage(StringBuilder sb, Map<String, Object> details) {
        if (details.containsKey("semaphoreUsage")) {
            Double usage = (Double) details.get("semaphoreUsage");
            Integer used = (Integer) details.get("semaphoreUsed");
            Integer maxLimit = (Integer) details.get("semaphoreMaxLimit");
            sb.append(String.format("  - Semaphore Usage: %.2f%% (%d/%d)%n", usage * 100, used, maxLimit));
        }
    }

    private static void appendActiveJobs(StringBuilder sb, Map<String, Object> details) {
        if (details.containsKey("activeJobs")) {
            Integer activeJobs = (Integer) details.get("activeJobs");
            Integer activeLaunchers = (Integer) details.get("activeLaunchers");
            sb.append(String.format("  - Active Jobs: %d, Active Launchers: %d%n", activeJobs, activeLaunchers));
        }
    }

    private static void appendResourceLeak(StringBuilder sb, Map<String, Object> details) {
        if (details.containsKey("resourceLeakDetected")) {
            Boolean leakDetected = (Boolean) details.get("resourceLeakDetected");
            if (Boolean.TRUE.equals(leakDetected)) {
                sb.append(String.format("  - ⚠️  Resource Leak Detected!%n"));
            }
        }
    }

    private static void appendRegistryStatus(StringBuilder sb, Map<String, Object> details) {
        if (details.containsKey("resourceRegistryInitialized")) {
            Boolean initialized = (Boolean) details.get("resourceRegistryInitialized");
            Boolean shutdown = (Boolean) details.get("resourceRegistryShutdown");
            sb.append(String.format("  - Registry: Initialized=%s, Shutdown=%s%n", initialized, shutdown));
        }
    }
}
