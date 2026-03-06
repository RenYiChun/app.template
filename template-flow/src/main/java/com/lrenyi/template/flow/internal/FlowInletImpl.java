package com.lrenyi.template.flow.internal;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import com.lrenyi.template.flow.api.FlowInlet;
import com.lrenyi.template.flow.api.ProgressTracker;
import lombok.RequiredArgsConstructor;

/**
 * 推送模式入口实现：委托给 FlowLauncher 与 ProgressTracker。
 */
@RequiredArgsConstructor
public class FlowInletImpl<T> implements FlowInlet<T> {
    private final FlowLauncher<T> launcher;
    private final AtomicBoolean sourceClosing = new AtomicBoolean(false);
    private final AtomicBoolean sourceClosed = new AtomicBoolean(false);
    private final AtomicInteger inFlightPush = new AtomicInteger(0);
    
    @Override
    public void push(T item) {
        if (sourceClosing.get() || sourceClosed.get()) {
            throw new IllegalStateException("Source already closed for job " + launcher.getJobId());
        }
        inFlightPush.incrementAndGet();
        try {
            if (sourceClosing.get() || sourceClosed.get()) {
                throw new IllegalStateException("Source already closed for job " + launcher.getJobId());
            }
            launcher.launch(item);
        } finally {
            inFlightPush.decrementAndGet();
        }
    }
    
    @Override
    public void markSourceFinished() {
        if (!sourceClosing.compareAndSet(false, true)) {
            return;
        }
        while (inFlightPush.get() > 0) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        sourceClosed.set(true);
        launcher.getTaskOrchestrator().tracker().markSourceFinished(launcher.getJobId());
    }
    
    @Override
    public ProgressTracker getProgressTracker() {
        return launcher.getTaskOrchestrator().tracker();
    }
    
    @Override
    public boolean isCompleted() {
        return launcher.isCompleted();
    }
    
    @Override
    public void stop(boolean force) {
        launcher.stop(force);
    }
}
