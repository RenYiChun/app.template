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
                             String rootJobDisplayName,
                             String stageKey,
                             String stageName,
                             String stageDisplayName,
                             String displayName) {

    public static FlowMetricTags resolve(String internalJobId, String metricJobId) {
        return resolve(internalJobId, metricJobId, null);
    }

    public static FlowMetricTags resolve(String internalJobId, String metricJobId, String explicitStageDisplayName) {
        String safeInternalJobId = blankToDefault(internalJobId, "unknown");
        String safeMetricJobId = blankToDefault(metricJobId, safeInternalJobId);
        String rootJobId = extractRootJobId(safeInternalJobId);
        String suffix = safeInternalJobId.startsWith(rootJobId) ? safeInternalJobId.substring(rootJobId.length()) : "";
        String displayName = extractDisplayName(safeMetricJobId, suffix);
        String stageKey = extractStageKey(suffix);
        String stageName = extractStageName(suffix);
        String branchName = extractBranchName(suffix);
        return new FlowMetricTags(safeInternalJobId,
                safeMetricJobId,
                rootJobId,
                extractRootJobDisplayName(displayName, rootJobId),
                stageKey,
                stageName,
                extractStageDisplayName(explicitStageDisplayName, stageName, stageKey, branchName),
                displayName);
    }

    public Tags toTags() {
        return Tags.of(
                FlowMetricNames.TAG_JOB_ID, metricJobId,
                FlowMetricNames.TAG_ROOT_JOB_ID, rootJobId,
                FlowMetricNames.TAG_ROOT_JOB_DISPLAY_NAME, rootJobDisplayName,
                FlowMetricNames.TAG_STAGE_KEY, stageKey,
                FlowMetricNames.TAG_STAGE_NAME, stageName,
                FlowMetricNames.TAG_STAGE_DISPLAY_NAME, stageDisplayName,
                FlowMetricNames.TAG_DISPLAY_NAME, displayName
        );
    }

    private static String extractRootJobDisplayName(String displayName, String rootJobId) {
        return blankToDefault(displayName, rootJobId);
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

    private static String extractBranchName(String suffix) {
        if (suffix == null || suffix.isBlank()) {
            return null;
        }
        String[] segments = suffix.substring(1).split(":");
        for (int i = 0; i < segments.length; i++) {
            if ("fork".equals(segments[i]) && i + 1 < segments.length) {
                String branch = segments[i + 1];
                int dash = branch.indexOf('-');
                String branchName = dash >= 0 && dash + 1 < branch.length() ? branch.substring(dash + 1) : branch;
                return branchName.isBlank() ? null : branchName;
            }
        }
        return null;
    }

    private static String extractStageDisplayName(String explicitStageDisplayName,
                                                  String stageName,
                                                  String stageKey,
                                                  String branchName) {
        String candidate = explicitStageDisplayName;
        if (candidate == null || candidate.isBlank()) {
            candidate = stageName;
        } else if (branchName != null && !branchName.isBlank()
                && !candidate.equals(branchName)
                && !candidate.startsWith(branchName + ":")) {
            candidate = branchName + ":" + candidate;
        }
        return blankToDefault(candidate, stageKey);
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
