package com.lrenyi.template.flow.internal;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import com.lrenyi.template.flow.api.FlowInlet;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.util.FlowLogHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 推送模式入口实现：委托给 FlowLauncher 与 ProgressTracker。
 */
@RequiredArgsConstructor
@Slf4j
public class FlowInletImpl<T> implements FlowInlet<T> {
    private final FlowLauncher<T> launcher;
    private final AtomicBoolean sourceClosing = new AtomicBoolean(false);
    private final AtomicBoolean sourceClosed = new AtomicBoolean(false);
    private final AtomicInteger inFlightPush = new AtomicInteger(0);

    @Override
    public void push(T item) {
        if (sourceClosed.get()) {
            log.warn("Push rejected because source already closed, {}",
                    FlowLogHelper.formatJobContext(launcher.getJobId(), launcher.getMetricJobId()));
            throw new IllegalStateException("Source already closed for job " + launcher.getJobId());
        }
        inFlightPush.incrementAndGet();
        try {
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
        // 先声明 source 结束并通知 tracker，使引擎可排空存储、解除背压；若先无限等待 inFlightPush，
        // 而 push 因背压阻塞，会导致死锁（caller 等 inFlightPush，inFlightPush 等存储排空，排空依赖 sourceFinished）。
        launcher.getTracker().markSourceFinished(launcher.getJobId(), true);
        log.info("Mark source finished declared, {}, inFlightPush={}",
                FlowLogHelper.formatJobContext(launcher.getJobId(), launcher.getMetricJobId()), inFlightPush.get());
        // 有限等待 in-flight 排空后再关闭入口，避免「已提交未执行」的 push 被误拒（结束标志更准确）
        long deadlineMs = System.currentTimeMillis() + 30_000L;
        while (inFlightPush.get() > 0 && System.currentTimeMillis() < deadlineMs) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                log.warn("Waiting inFlightPush interrupted, {}, remaining={}",
                        FlowLogHelper.formatJobContext(launcher.getJobId(), launcher.getMetricJobId()),
                        inFlightPush.get());
                break;
            }
        }
        if (inFlightPush.get() > 0) {
            log.info("Mark source finished done with in-flight remaining, {}, remainingInFlightPush={}",
                    FlowLogHelper.formatJobContext(launcher.getJobId(), launcher.getMetricJobId()),
                    inFlightPush.get());
        }
        // 在等待 in-flight 排空后再关闭入口，避免其他线程刚准备 push 时被拒绝
        sourceClosed.set(true);
        // push 全部完成后若生产也已结束，触发 completion drain（非匹配模式）
        if (launcher.getTracker().isProductionComplete()) {
            launcher.getStorage().triggerCompletionDrain();
        }
    }

    /** 供完成判定使用：当前尚未结束的 push 调用数（已 increment 未 decrement）。 */
    public int getInFlightPushCount() {
        return inFlightPush.get();
    }

    @Override
    public ProgressTracker getProgressTracker() {
        return launcher.getTracker();
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
