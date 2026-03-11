package com.lrenyi.template.flow.internal;

import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.exception.FlowExceptionHelper;
import com.lrenyi.template.flow.exception.FlowPhase;
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.model.PreRetryResult;
import com.lrenyi.template.flow.storage.RetryStorageAdapter;
import com.lrenyi.template.flow.util.FlowLogHelper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RetryHandler<T> {
    private final MatchRetryCoordinator<T> retryCoordinator;
    private final RetryStorageAdapter<T> adapter;
    private final long backoffMill;
    
    public RetryHandler(MatchRetryCoordinator<T> retryCoordinator, RetryStorageAdapter<T> adapter, long backoffMill) {
        this.retryCoordinator = retryCoordinator;
        this.adapter = adapter;
        this.backoffMill = backoffMill;
    }
    
    public boolean tryHandleRetry(String key, FlowEntry<T> entry, EgressReason reason, FlowLauncher<Object> launcher) {
        if (!retryCoordinator.tryConsumeRetry(reason, entry)) {
            return false;
        }
        log.debug("Retry triggered, {}, reason={}, retryRemaining={}, key={}",
                  FlowLogHelper.formatJobContext(entry.getJobId(), launcher != null ? launcher.getMetricJobId() : null),
                  reason,
                  entry.getRetryRemaining(),
                  key
        );
        if (backoffMill > 0 && !sleepBackoff(entry, launcher)) {
            return false;
        }
        PreRetryResult result = adapter.preRetry(key, entry, launcher);
        if (result == PreRetryResult.HANDLED) {
            retryCoordinator.onRetrySucceeded(reason);
            log.debug("Retry handled in preRetry, {}, reason={}, key={}",
                    FlowLogHelper.formatJobContext(entry.getJobId(), launcher != null ? launcher.getMetricJobId() : null),
                    reason, key);
            return true;
        }
        if (adapter.tryRequeue(entry)) {
            retryCoordinator.onRetrySucceeded(reason);
            log.debug("Retry requeue succeeded, {}, reason={}, key={}",
                    FlowLogHelper.formatJobContext(entry.getJobId(), launcher != null ? launcher.getMetricJobId() : null),
                    reason, key);
            return true;
        }
        log.warn("Retry failed and fallback to passive egress, {}, reason={}, key={}",
                 FlowLogHelper.formatJobContext(entry.getJobId(), launcher != null ? launcher.getMetricJobId() : null),
                 reason,
                 key
        );
        adapter.handlePassiveFailure(entry, reason);
        return true;
    }
    
    private boolean sleepBackoff(FlowEntry<T> entry, FlowLauncher<Object> launcher) {
        try {
            Thread.sleep(backoffMill);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            FlowExceptionHelper.handleException(entry.getJobId(), null, e, FlowPhase.STORAGE, "retry_backoff_interrupted",
                    launcher != null ? launcher.getMetricJobId() : null);
            return false;
        }
    }
}
