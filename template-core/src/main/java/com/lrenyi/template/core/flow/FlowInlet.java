package com.lrenyi.template.core.flow;

import java.util.concurrent.CompletableFuture;

/**
 * 推送模式入口：业务通过 push(item) 注入数据，通过 markSourceFinished() 声明输入结束。
 * 内部委托给 FlowLauncher 与 ProgressTracker，与拉取模式共用同一套存储与消费逻辑。
 */
public interface FlowInlet<T> {

    /**
     * 推送一条数据，语义同 Launcher.launch(item)；可能因背压阻塞。
     */
    void push(T item);

    /**
     * 声明输入已截止，不再 push；完成后 getCompletionFuture() 将在缓存排空后完成。
     */
    void markSourceFinished();

    /**
     * 进度追踪（快照、完成率、Stuck 等）。
     */
    ProgressTracker getProgressTracker();

    /**
     * 任务完成：markSourceFinished 已调用且活跃消费归零后完成。
     */
    CompletableFuture<Void> getCompletionFuture();

    /**
     * 停止任务（不再接收 push）；force 为 true 时强制清空缓存。
     */
    void stop(boolean force);
}
