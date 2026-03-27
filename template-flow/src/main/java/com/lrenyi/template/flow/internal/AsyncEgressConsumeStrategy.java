package com.lrenyi.template.flow.internal;

import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.model.EgressReason;

/**
 * 兼容当前行为的异步消费策略。
 */
public final class AsyncEgressConsumeStrategy<T> implements EgressConsumeStrategy<T> {
    private final FlowFinalizer<T> finalizer;

    public AsyncEgressConsumeStrategy(FlowFinalizer<T> finalizer) {
        this.finalizer = finalizer;
    }

    @Override
    public void submitSingle(FlowEntry<T> entry, FlowLauncher<?> launcher, EgressReason reason) {
        finalizer.submitDataToConsumer(entry, launcher, reason);
    }
}
