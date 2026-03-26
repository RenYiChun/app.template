package com.lrenyi.template.flow.metrics;

import io.micrometer.core.instrument.Tags;

/**
 * Flow 指标统一标签解析。
 * <p>
 * 内部 jobId 用于唯一定位阶段；监控展示使用 displayName/rootJobId/stageKey/stageName。
 */
public record FlowMetricTags(String internalJobId,
                             String metricJobId,
                             String rootJobId,
                             String stageKey,
                             String stageName,
                             String displayName) {

    public static FlowMetricTags resolve(String internalJobId, String metricJobId) {
        String safeInternalJobId = blankToDefault(internalJobId, "unknown");
        String safeMetricJobId = blankToDefault(metricJobId, safeInternalJobId);
        String rootJobId = extractRootJobId(safeInternalJobId);
        String suffix = safeInternalJobId.startsWith(rootJobId) ? safeInternalJobId.substring(rootJobId.length()) : "";
        String displayName = extractDisplayName(safeMetricJobId, suffix);
        String stageKey = extractStageKey(suffix);
        String stageName = extractStageName(suffix);
        return new FlowMetricTags(safeInternalJobId, safeMetricJobId, rootJobId, stageKey, stageName, displayName);
    }

    public Tags toTags() {
        return Tags.of(
                FlowMetricNames.TAG_JOB_ID, metricJobId,
                FlowMetricNames.TAG_ROOT_JOB_ID, rootJobId,
                FlowMetricNames.TAG_STAGE_KEY, stageKey,
                FlowMetricNames.TAG_STAGE_NAME, stageName,
                FlowMetricNames.TAG_DISPLAY_NAME, displayName
        );
    }

    private static String extractRootJobId(String internalJobId) {
        int idx = internalJobId.indexOf(':');
        return idx >= 0 ? internalJobId.substring(0, idx) : internalJobId;
    }

    private static String extractDisplayName(String metricJobId, String suffix) {
        if (!suffix.isEmpty() && metricJobId.endsWith(suffix)) {
            String candidate = metricJobId.substring(0, metricJobId.length() - suffix.length());
            if (!candidate.isBlank()) {
                return candidate;
            }
        }
        return metricJobId;
    }

    private static String extractStageKey(String suffix) {
        if (suffix == null || suffix.isBlank()) {
            return "root";
        }
        String[] segments = suffix.substring(1).split(":");
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                key.append('/');
            }
            String segment = segments[i];
            if (i > 0 && "fork".equals(segments[i - 1])) {
                int dash = segment.indexOf('-');
                key.append(dash > 0 ? segment.substring(0, dash) : segment);
            } else {
                key.append(segment);
            }
        }
        return key.toString();
    }

    private static String extractStageName(String suffix) {
        if (suffix == null || suffix.isBlank()) {
            return "root";
        }
        String[] segments = suffix.substring(1).split(":");
        if (segments.length == 1) {
            return "stage-" + segments[0];
        }
        for (int i = 0; i < segments.length; i++) {
            if ("fork".equals(segments[i]) && i + 1 < segments.length) {
                String branch = segments[i + 1];
                int dash = branch.indexOf('-');
                String branchName = dash >= 0 && dash + 1 < branch.length() ? branch.substring(dash + 1) : branch;
                String leaf = segments[segments.length - 1];
                return branchName + ":stage-" + leaf;
            }
        }
        return "stage-" + segments[segments.length - 1];
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
