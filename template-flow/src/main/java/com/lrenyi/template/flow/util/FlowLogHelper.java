package com.lrenyi.template.flow.util;

/**
 * Flow 框架日志格式化工具。在含 jobId 的日志中统一输出 displayName（当存在且不同于 jobId 时）。
 */
public final class FlowLogHelper {

    private FlowLogHelper() {
    }

    /**
     * 格式化 jobId 用于日志输出。当 displayName 存在且不同于 jobId 时，同时输出 displayName。
     *
     * @param jobId       业务 jobId
     * @param displayName 监控展示名，可为 null
     * @return 如 "jobId=xxx" 或 "jobId=xxx, displayName=yyy"
     */
    public static String formatJobContext(String jobId, String displayName) {
        if (displayName == null || displayName.isEmpty() || displayName.equals(jobId)) {
            return "jobId=" + jobId;
        }
        return "jobId=" + jobId + ", displayName=" + displayName;
    }
}
