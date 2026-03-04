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
import com.lrenyi.template.flow.internal.FlowFinalizer;
import com.lrenyi.template.flow.internal.FlowLauncher;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.model.FailureReason;
import com.lrenyi.template.flow.model.PreRetryResult;
import com.lrenyi.template.flow.resource.ActiveLauncherLookup;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
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
            ProgressTracker progressTracker,
            FlowFinalizer<T> finalizer,
            String jobId,
            long drainIntervalMs,
            MeterRegistry meterRegistry) {
        super(joiner, finalizer, progressTracker, meterRegistry);
        this.queue = new LinkedBlockingQueue<>(capacity);
        this.maxCacheSize = capacity;
        
        Gauge.builder(FlowMetricNames.LIMITS_STORAGE_USED, queue, BlockingQueue::size)
             .tag(FlowMetricNames.TAG_JOB_ID, jobId)
             .tag(FlowMetricNames.TAG_STORAGE_TYPE, "queue")
             .description("每 Job 缓存当前条数")
             .register(meterRegistry);
        Gauge.builder(FlowMetricNames.LIMITS_STORAGE_LIMIT, () -> maxCacheSize).tag(FlowMetricNames.TAG_JOB_ID, jobId)
             .tag(FlowMetricNames.TAG_STORAGE_TYPE, "queue").description("每 Job 缓存容量上限")
             .register(meterRegistry);
        
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
            resourceRegistry().releaseGlobalStorage(1);
            FlowLauncher<Object> launcher = launcherLookup.getActiveLauncher(entry.getJobId());
            if (launcher == null) {
                handlePassiveFailure(entry, FailureReason.SHUTDOWN);
                continue;
            }
            String key = joiner().joinKey(entry.getData());
            handleEgress(key, entry, FailureReason.TIMEOUT);
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
        Counter.builder(FlowMetricNames.EGRESS_PASSIVE)
               .tag(FlowMetricNames.TAG_JOB_ID, ctx.getJobId())
               .tag(FlowMetricNames.TAG_REASON, "REJECT")
               .register(meterRegistry())
               .increment();
        Counter.builder(FlowMetricNames.ERRORS)
               .tag(FlowMetricNames.TAG_ERROR_TYPE, "queue_full_rejected")
               .tag(FlowMetricNames.TAG_PHASE, "STORAGE")
               .register(meterRegistry())
               .increment();
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
    public void handlePassiveFailure(FlowEntry<T> entry, FailureReason reason) {
        try (entry) {
            joiner().onFailed(entry.getData(), entry.getJobId(), reason);
            Counter.builder(FlowMetricNames.EGRESS_PASSIVE)
                   .tag(FlowMetricNames.TAG_JOB_ID, entry.getJobId())
                   .tag(FlowMetricNames.TAG_REASON, reason.name())
                   .register(meterRegistry())
                   .increment();
        } finally {
            if (progressTracker() != null) {
                progressTracker().onPassiveEgress(reason);
            }
        }
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
            resourceRegistry().releaseGlobalStorage(1);
            Counter.builder(FlowMetricNames.EGRESS_PASSIVE)
                   .tag(FlowMetricNames.TAG_JOB_ID, remaining.getJobId())
                   .tag(FlowMetricNames.TAG_REASON, FailureReason.SHUTDOWN.name())
                   .register(meterRegistry())
                   .increment();
            if (progressTracker() != null) {
                progressTracker().onPassiveEgress(FailureReason.SHUTDOWN);
            }
            remaining.close();
        }
        if (log.isDebugEnabled()) {
            log.debug("QueueFlowStorage shut down, drain task cancelled, queue drained.");
        }
    }
}
