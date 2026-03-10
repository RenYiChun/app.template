package com.lrenyi.template.flow.context;

import com.lrenyi.template.flow.api.ProgressTracker;

/**
 * 任务编排器，持有 jobId、tracker 与 resourceContext。
 * 消费并发由 FlowFinalizer 通过 BackpressureManager 控制。
 */
public record Orchestrator(String jobId, ProgressTracker tracker, FlowResourceContext resourceContext) {
}
