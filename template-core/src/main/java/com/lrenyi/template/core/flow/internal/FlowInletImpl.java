package com.lrenyi.template.core.flow.internal;

import java.util.concurrent.CompletableFuture;
import com.lrenyi.template.core.flow.api.FlowInlet;
import com.lrenyi.template.core.flow.api.ProgressTracker;
import lombok.RequiredArgsConstructor;

/**
 * 推送模式入口实现：委托给 FlowLauncher 与 ProgressTracker。
 */
@RequiredArgsConstructor
public class FlowInletImpl<T> implements FlowInlet<T> {
    private final FlowLauncher<T> launcher;

    @Override
    public void push(T item) {
        launcher.launch(item);
    }

    @Override
    public void markSourceFinished() {
        launcher.getTaskOrchestrator().tracker().markSourceFinished(launcher.getJobId());
    }

    @Override
    public ProgressTracker getProgressTracker() {
        return launcher.getTaskOrchestrator().tracker();
    }

    @Override
    public CompletableFuture<Void> getCompletionFuture() {
        return getProgressTracker().getCompletionFuture();
    }

    @Override
    public void stop(boolean force) {
        launcher.stop(force);
    }
}
