package com.lrenyi.template.flow.storage;

import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.internal.FlowLauncher;
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.model.PreRetryResult;

public interface RetryStorageAdapter<T> {
    PreRetryResult preRetry(String key, FlowEntry<T> entry, FlowLauncher<Object> launcher);
    
    boolean tryRequeue(FlowEntry<T> entry);
    
    void handlePassiveFailure(FlowEntry<T> entry, EgressReason reason);
}
