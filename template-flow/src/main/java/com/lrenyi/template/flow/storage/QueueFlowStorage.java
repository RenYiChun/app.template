package com.lrenyi.template.flow.storage;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.internal.FlowFinalizer;
import com.lrenyi.template.flow.internal.FlowLauncher;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.model.FailureReason;
import com.lrenyi.template.flow.resource.ActiveLauncherLookup;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
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
public class QueueFlowStorage<T> implements FlowStorage<T> {
    private final BlockingQueue<FlowEntry<T>> queue;
    private final long maxCacheSize;
    private final ProgressTracker progressTracker;
    private final FlowFinalizer<T> finalizer;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final FlowResourceRegistry resourceRegistry;
    private final MeterRegistry meterRegistry;
    private ScheduledFuture<?> scheduledFuture;
    
    public QueueFlowStorage(int capacity,
            ProgressTracker progressTracker,
            FlowFinalizer<T> finalizer,
            String jobId,
            long drainIntervalMs,
            MeterRegistry meterRegistry) {
        this.queue = new LinkedBlockingQueue<>(capacity);
        this.maxCacheSize = capacity;
        this.progressTracker = progressTracker;
        this.finalizer = finalizer;
        this.resourceRegistry = finalizer.resourceRegistry();
        this.meterRegistry = meterRegistry;
        
        Gauge.builder(FlowMetricNames.STORAGE_SIZE, queue, BlockingQueue::size)
             .tag(FlowMetricNames.TAG_JOB_ID, jobId)
             .tag(FlowMetricNames.TAG_STORAGE_TYPE, "queue")
             .description("当前队列中的数据条数")
             .register(meterRegistry);
        
        ScheduledExecutorService egressExecutor = resourceRegistry.getStorageEgressExecutor();
        if (egressExecutor != null && drainIntervalMs > 0) {
            this.scheduledFuture = egressExecutor.scheduleWithFixedDelay(this::drainLoop,
                                                                         drainIntervalMs,
                                                                         drainIntervalMs,
                                                                         TimeUnit.MILLISECONDS
            );
        }
    }
    
    private void drainLoop() {
        ActiveLauncherLookup launcherLookup = resourceRegistry.getLauncherLookup();
        if (launcherLookup == null) {
            log.warn("LauncherLookup not available for drainLoop");
            Counter.builder(FlowMetricNames.ERRORS)
                   .tag(FlowMetricNames.TAG_ERROR_TYPE, "flow_manager_unavailable")
                   .tag(FlowMetricNames.TAG_PHASE, "FINALIZATION")
                   .register(meterRegistry)
                   .increment();
            return;
        }
        FlowEntry<T> entry;
        while (!stopped.get() && (entry = queue.poll()) != null) {
            @SuppressWarnings("unchecked") FlowLauncher<Object> launcher =
                    (FlowLauncher<Object>) launcherLookup.getActiveLauncher(entry.getJobId());
            if (launcher == null) {
                if (progressTracker != null) {
                    progressTracker.onPassiveEgress(FailureReason.SHUTDOWN);
                }
                Counter.builder(FlowMetricNames.EGRESS_PASSIVE)
                       .tag(FlowMetricNames.TAG_JOB_ID, entry.getJobId())
                       .tag(FlowMetricNames.TAG_REASON, "SHUTDOWN")
                       .register(meterRegistry)
                       .increment();
                entry.close();
                continue;
            }
            finalizer.submitBodyOnly(entry, launcher);
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
               .register(meterRegistry)
               .increment();
        Counter.builder(FlowMetricNames.ERRORS)
               .tag(FlowMetricNames.TAG_ERROR_TYPE, "queue_full_rejected")
               .tag(FlowMetricNames.TAG_PHASE, "STORAGE")
               .register(meterRegistry)
               .increment();
        return false;
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
            if (progressTracker != null) {
                progressTracker.onPassiveEgress(FailureReason.SHUTDOWN);
            }
            remaining.close();
        }
        if (log.isDebugEnabled()) {
            log.debug("QueueFlowStorage shut down, drain task cancelled, queue drained.");
        }
    }
}
