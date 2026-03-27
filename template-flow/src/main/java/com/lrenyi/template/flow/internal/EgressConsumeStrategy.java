package com.lrenyi.template.flow.internal;

import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.model.EgressReason;

/**
 * Storage egress 后进入消费逻辑的可插拔策略。
 */
public interface EgressConsumeStrategy<T> {

    void submitSingle(FlowEntry<T> entry, FlowLauncher<?> launcher, EgressReason reason);
}
