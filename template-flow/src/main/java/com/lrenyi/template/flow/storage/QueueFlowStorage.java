package com.lrenyi.template.flow.storage;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.internal.FlowEgressHandler;
import com.lrenyi.template.flow.internal.FlowFinalizer;
import com.lrenyi.template.flow.internal.FlowLauncher;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.model.PreRetryResult;
import com.lrenyi.template.flow.resource.ActiveLauncherLookup;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于阻塞队列的流式存储实现。
 *
 * @param <T> 存储的数据类型
 */
@Slf4j
public class QueueFlowStorage<T> extends AbstractEgressFlowStorage<T> implements FlowStorage<T> {
    private final BlockingQueue<FlowEntry<T>> queue;
    private final long maxCacheSize;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private ScheduledFuture<?> scheduledFuture;
    
    public QueueFlowStorage(int capacity,
            FlowJoiner<T> joiner,
            ProgressTracker progressTracker, FlowFinalizer<T> finalizer, FlowEgressHandler<T> egressHandler,
            String jobId,
            long drainIntervalMs,
            MeterRegistry meterRegistry) {
        super(joiner, finalizer, progressTracker, meterRegistry, egressHandler);
        this.queue = new LinkedBlockingQueue<>(capacity);
        this.maxCacheSize = capacity;
        
        ScheduledExecutorService egressExecutor = resourceRegistry().getStorageEgressExecutor();
        if (egressExecutor != null && drainIntervalMs > 0) {
            this.scheduledFuture = egressExecutor.scheduleWithFixedDelay(this::drainLoop,
                                                                         drainIntervalMs,
                                                                         drainIntervalMs,
                                                                         TimeUnit.MILLISECONDS
            );
        }
    }
    
    private void drainLoop() {
        ActiveLauncherLookup launcherLookup = resourceRegistry().getLauncherLookup();
        if (launcherLookup == null) {
            log.warn("LauncherLookup not available for drainLoop");
            Counter.builder(FlowMetricNames.ERRORS)
                   .tag(FlowMetricNames.TAG_ERROR_TYPE, "flow_manager_unavailable")
                   .tag(FlowMetricNames.TAG_PHASE, "FINALIZATION")
                   .register(meterRegistry())
                   .increment();
            return;
        }
        FlowEntry<T> entry;
        while (!stopped.get() && (entry = queue.poll()) != null) {
            try {
                entry.closeStorageLease();
                FlowLauncher<Object> launcher = launcherLookup.getActiveLauncher(entry.getJobId());
                String key = joiner().joinKey(entry.getData());
                if (launcher == null) {
                    handleEgress(key, entry, EgressReason.SHUTDOWN, true);
                    continue;
                }
                handleEgress(key, entry, EgressReason.TIMEOUT, false);
            } catch (Throwable t) {
                Counter.builder(FlowMetricNames.ERRORS)
                       .tag(FlowMetricNames.TAG_ERROR_TYPE, "queue_drain_failed")
                       .tag(FlowMetricNames.TAG_PHASE, "FINALIZATION")
                       .register(meterRegistry())
                       .increment();
                log.error("Queue drain failed for job {}", entry.getJobId(), t);
                try {
                    String key = joiner().joinKey(entry.getData());
                    handleEgress(key, entry, EgressReason.SHUTDOWN, true);
                } catch (Throwable ignored) {
                    entry.close();
                }
            }
        }
    }
    
    @Override
    public boolean doDeposit(FlowEntry<T> ctx) {
        boolean success = queue.offer(ctx);
        if (success) {
            if (log.isDebugEnabled()) {
                log.debug("Data deposited into queue: jobId={}, queueSize={}", ctx.getJobId(), queue.size());
            }
            return true;
        }
        if (log.isWarnEnabled()) {
            log.warn("Queue full, task rejected: jobId={}", ctx.getJobId());
        }
        // 本分支不关闭 storageLease，由调用方 FlowLauncher 在 deposit 返回 false 时通过 ctx.closeStorageLease() 统一释放，避免双释放
        String key = joiner().joinKey(ctx.getData());
        handleEgress(key, ctx, EgressReason.REJECT, true);
        return false;
    }
    
    @Override
    public PreRetryResult preRetry(String key, FlowEntry<T> entry, FlowLauncher<Object> launcher) {
        return PreRetryResult.PROCEED_TO_REQUEUE;
    }
    
    @Override
    public boolean tryRequeue(FlowEntry<T> entry) {
        return doDeposit(entry);
    }
    
    @Override
    public long size() {
        return queue.size();
    }
    
    @Override
    public long maxCacheSize() {
        return maxCacheSize;
    }
    
    @Override
    public void shutdown() {
        stopped.set(true);
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
        FlowEntry<T> remaining;
        while ((remaining = queue.poll()) != null) {
            remaining.closeStorageLease();
            String key = joiner().joinKey(remaining.getData());
            handleEgress(key, remaining, EgressReason.SHUTDOWN, true);
        }
        if (log.isDebugEnabled()) {
            log.debug("QueueFlowStorage shut down, drain task cancelled, queue drained.");
        }
    }
}
