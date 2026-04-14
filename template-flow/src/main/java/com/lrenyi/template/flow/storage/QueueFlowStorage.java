package com.lrenyi.template.flow.storage;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.internal.FlowEgressHandler;
import com.lrenyi.template.flow.internal.FlowFinalizer;
import com.lrenyi.template.flow.internal.FlowLauncher;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.metrics.FlowMetricTags;
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.model.FlowConsumeExecutionMode;
import com.lrenyi.template.flow.model.FlowConstants;
import com.lrenyi.template.flow.model.PreRetryResult;
import com.lrenyi.template.flow.resource.ActiveLauncherLookup;
import com.lrenyi.template.flow.util.FlowLogHelper;
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
    private final long drainIntervalMs;
    private final java.util.List<Thread> drainThreads;
    private final int egressWorkerThreads;
    private final FlowConsumeExecutionMode consumeExecutionMode;
    private final AtomicInteger activeEgressWorkers = new AtomicInteger();
    private final AtomicInteger remainingInlineWorkers;
    
    public QueueFlowStorage(int capacity,
            FlowJoiner<T> joiner,
            ProgressTracker progressTracker, FlowFinalizer<T> finalizer, FlowEgressHandler<T> egressHandler,
            String jobId,
            long drainIntervalMs,
            FlowConsumeExecutionMode consumeExecutionMode,
            int egressWorkerThreads,
            MeterRegistry meterRegistry) {
        super(joiner, finalizer, progressTracker, meterRegistry, egressHandler);
        this.queue = new LinkedBlockingQueue<>(capacity);
        this.maxCacheSize = capacity;
        this.drainIntervalMs = drainIntervalMs > 0 ? drainIntervalMs : 10L;
        this.consumeExecutionMode = consumeExecutionMode != null ? consumeExecutionMode : FlowConsumeExecutionMode.ASYNC;
        this.egressWorkerThreads = Math.max(1, egressWorkerThreads);
        this.remainingInlineWorkers = new AtomicInteger(this.egressWorkerThreads);
        this.drainThreads = new java.util.ArrayList<>(this.egressWorkerThreads);
        if (this.consumeExecutionMode == FlowConsumeExecutionMode.INLINE) {
            progressTracker.setActiveConsumers(this.egressWorkerThreads);
        }
        registerEgressMetrics(jobId, progressTracker, meterRegistry);
        for (int i = 0; i < this.egressWorkerThreads; i++) {
            this.drainThreads.add(Thread.ofVirtual()
                    .name(FlowConstants.THREAD_NAME_PREFIX_STORAGE_EGRESS, i)
                    .start(this::drainLoop));
        }
    }
    
    private void drainLoop() {
        ActiveLauncherLookup launcherLookup = resourceRegistry().getLauncherLookup();
        if (launcherLookup == null) {
            log.warn("LauncherLookup not available for drainLoop");
            Counter.builder(FlowMetricNames.ERRORS)
                   .tags(FlowMetricTags.resolve("queue-drain",
                           progressTracker().getMetricJobId(),
                           progressTracker().getStageDisplayName()).toTags())
                   .tag(FlowMetricNames.TAG_ERROR_TYPE, "flow_manager_unavailable")
                   .tag(FlowMetricNames.TAG_PHASE, "FINALIZATION")
                   .register(meterRegistry())
                   .increment();
            onDrainWorkerStopped();
            return;
        }
        try {
            while (!stopped.get()) {
                boolean workerActive = false;
                try {
                    FlowEntry<T> first = queue.poll(drainIntervalMs, TimeUnit.MILLISECONDS);
                    if (first == null) {
                        if (shouldStopInlineWorker()) {
                            return;
                        }
                        continue;
                    }
                    activeEgressWorkers.incrementAndGet();
                    workerActive = true;
                    drainEntry(first, launcherLookup);
                    FlowEntry<T> entry;
                    while (!stopped.get() && (entry = queue.poll()) != null) {
                        drainEntry(entry, launcherLookup);
                    }
                    if (shouldStopInlineWorker()) {
                        return;
                    }
                } catch (InterruptedException e) {
                    if (stopped.get()) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                } catch (Throwable t) {
                    Counter.builder(FlowMetricNames.ERRORS)
                           .tags(FlowMetricTags.resolve("queue-drain",
                                   progressTracker().getMetricJobId(),
                                   progressTracker().getStageDisplayName()).toTags())
                           .tag(FlowMetricNames.TAG_ERROR_TYPE, "queue_drain_failed")
                           .tag(FlowMetricNames.TAG_PHASE, "FINALIZATION")
                           .register(meterRegistry())
                           .increment();
                    log.error("Queue drain loop failed", t);
                } finally {
                    if (workerActive) {
                        activeEgressWorkers.decrementAndGet();
                    }
                }
            }
        } finally {
            onDrainWorkerStopped();
        }
    }

    private boolean shouldStopInlineWorker() {
        return consumeExecutionMode == FlowConsumeExecutionMode.INLINE
                && progressTracker().isProductionComplete()
                && queue.isEmpty();
    }

    private void onDrainWorkerStopped() {
        if (consumeExecutionMode != FlowConsumeExecutionMode.INLINE) {
            return;
        }
        if (remainingInlineWorkers.decrementAndGet() == 0) {
            progressTracker().setActiveConsumers(0);
        }
    }

    private void drainEntry(FlowEntry<T> entry, ActiveLauncherLookup launcherLookup) {
        try {
            entry.closeStorageLease();
            FlowLauncher<Object> launcher = launcherLookup.getActiveLauncher(entry.getJobId());
            String key = joiner().joinKey(entry.getData());
            if (launcher == null) {
                handleEgress(key, entry, EgressReason.SHUTDOWN, true);
                return;
            }
            handleEgress(key, entry, EgressReason.SINGLE_CONSUMED, false);
        } catch (Throwable t) {
            FlowLauncher<Object> launcherForLog = launcherLookup.getActiveLauncher(entry.getJobId());
            Counter.builder(FlowMetricNames.ERRORS)
                   .tags(FlowMetricTags.resolve(entry.getJobId(),
                           launcherForLog != null ? launcherForLog.getMetricJobId() : progressTracker().getMetricJobId(),
                           launcherForLog != null
                                   ? launcherForLog.getTracker().getStageDisplayName()
                                   : progressTracker().getStageDisplayName()).toTags())
                   .tag(FlowMetricNames.TAG_ERROR_TYPE, "queue_drain_failed")
                   .tag(FlowMetricNames.TAG_PHASE, "FINALIZATION")
                   .register(meterRegistry())
                   .increment();
            log.error("Queue drain failed for job {}",
                    FlowLogHelper.formatJobContext(entry.getJobId(),
                            launcherForLog != null ? launcherForLog.getMetricJobId() : null), t);
            try {
                String key = joiner().joinKey(entry.getData());
                handleEgress(key, entry, EgressReason.SHUTDOWN, true);
            } catch (Throwable ignored) {
                entry.close();
            }
        }
    }
    
    @Override
    public boolean doDeposit(FlowEntry<T> ctx) {
        boolean success = queue.offer(ctx);
        if (success) {
            if (log.isDebugEnabled()) {
                log.debug("Data deposited into queue: {}, queueSize={}",
                        FlowLogHelper.formatJobContext(ctx.getJobId(), null), queue.size());
            }
            return true;
        }
        if (log.isWarnEnabled()) {
            log.warn("Queue full, task rejected: {}", FlowLogHelper.formatJobContext(ctx.getJobId(), null));
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
        for (Thread drainThread : drainThreads) {
            if (drainThread != null) {
                drainThread.interrupt();
            }
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

    private void registerEgressMetrics(String jobId, ProgressTracker progressTracker, MeterRegistry meterRegistry) {
        FlowMetricTags tags = FlowMetricTags.resolve(jobId,
                progressTracker.getMetricJobId(),
                progressTracker.getStageDisplayName());
        Gauge.builder(FlowMetricNames.EGRESS_ACTIVE_WORKERS, activeEgressWorkers, AtomicInteger::get)
                .tags(tags.toTags())
                .tag(FlowMetricNames.TAG_CONSUME_EXECUTION_MODE, consumeExecutionMode.name().toLowerCase())
                .register(meterRegistry);
        Gauge.builder(FlowMetricNames.EGRESS_WORKER_LIMIT, () -> egressWorkerThreads)
                .tags(tags.toTags())
                .tag(FlowMetricNames.TAG_CONSUME_EXECUTION_MODE, consumeExecutionMode.name().toLowerCase())
                .register(meterRegistry);
    }
}
