package com.lrenyi.template.core.flow.storage;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import com.lrenyi.template.core.flow.api.ProgressTracker;
import com.lrenyi.template.core.flow.context.FlowEntry;
import com.lrenyi.template.core.flow.internal.FlowFinalizer;
import com.lrenyi.template.core.flow.internal.FlowLauncher;
import com.lrenyi.template.core.flow.model.FailureReason;
import com.lrenyi.template.core.flow.metrics.FlowMetrics;
import com.lrenyi.template.core.flow.resource.ActiveLauncherLookup;
import com.lrenyi.template.core.flow.resource.FlowResourceRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于阻塞队列的流式存储实现。
 * 与 Caffeine 不同，队列没有超时驱逐线程，消费由框架统一的
 * {@link com.lrenyi.template.core.flow.manager.FlowCacheManager} 中的 {@code storageEgressExecutor}（单物理线程）完成：
 * 在构造函数中按配置的 TTL 周期向该 executor 提交排空任务，从队列取数 → {@link FlowFinalizer#submitBodyOnly}（acquire 由 FlowGlobalExecutor 处理），
 * 与 Caffeine 驱逐路径共用同一离场线程，离场语义一致。
 * <p>
 * 适用于：顺序消费、削峰填谷。不支持按 Key 检索，{@link #remove(String)} 使用接口默认实现。
 * <p>
 * 若由框架通过 {@link com.lrenyi.template.core.flow.manager.FlowCacheManager#getOrCreateStorage} 创建并传入 finalizer、jobId
 * 、storageEgressExecutor、drainIntervalMs、drainScheduler，
 * 则在构造函数中向 FlowManager 的 queueDrainScheduler 注册定时任务（按 drainIntervalMs 周期向 storageEgressExecutor 提交 drainLoop），与
 * Caffeine 共用离场线程；
 */
@Slf4j
public class QueueFlowStorage<T> implements FlowStorage<T> {
    private final BlockingQueue<FlowEntry<T>> queue;
    private final long maxCacheSize;
    private final ProgressTracker progressTracker;
    private final FlowFinalizer<T> finalizer;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private ScheduledFuture<?> scheduledFuture;
    private final FlowResourceRegistry resourceRegistry;
    
    /**
     * 框架创建时使用：传入 finalizer、jobId、storageEgressExecutor、drainIntervalMs、drainScheduler 时，
     * 使用定时调度（scheduleAtFixedRate）按 drainIntervalMs 周期向 storageEgressExecutor 提交 drainLoop，与 Caffeine 共用离场线程。
     *
     * @param capacity              队列容量，决定背压触发的阈值
     * @param progressTracker       可为 null；shutdown 排空时对每条调用 onPassiveEgress()
     * @param finalizer             非 null 时消费通过 submitBodyOnly 执行 onConsume + release
     * @param jobId                 getActiveLauncher(jobId) 使用
     * @param drainIntervalMs       排空周期（毫秒），与 JobConfig.ttlMill 对齐；&lt;=0 则不注册
     */
    public QueueFlowStorage(int capacity,
                            ProgressTracker progressTracker,
                            FlowFinalizer<T> finalizer,
                            String jobId,
                            long drainIntervalMs) {
        this.queue = new LinkedBlockingQueue<>(capacity);
        this.maxCacheSize = capacity;
        this.progressTracker = progressTracker;
        this.finalizer = finalizer;
        this.resourceRegistry = finalizer.resourceRegistry();
        ScheduledExecutorService egressExecutor = resourceRegistry.getStorageEgressExecutor();
        if (jobId != null && egressExecutor != null && drainIntervalMs > 0) {
            this.scheduledFuture = egressExecutor.scheduleWithFixedDelay(this::drainLoop,
                                                                                drainIntervalMs,
                                                                                drainIntervalMs,
                                                                                TimeUnit.MILLISECONDS
            );
        }
    }
    
    /**
     * 在 storageEgressExecutor 单线程上执行：非阻塞取队直到空，每条 submitBodyOnly（acquire 由 FlowGlobalExecutor 处理）。
     */
    private void drainLoop() {
        ActiveLauncherLookup launcherLookup = resourceRegistry.getLauncherLookup();
        if (launcherLookup == null) {
            log.warn("LauncherLookup not available for drainLoop");
            FlowMetrics.recordError("flow_manager_unavailable_drain", null);
            return;
        }
        FlowEntry<T> entry;
        int drainedCount = 0;
        while (!stopped.get() && (entry = queue.poll()) != null) {
            drainedCount++;
            @SuppressWarnings("unchecked")
            FlowLauncher<Object> launcher = (FlowLauncher<Object>) launcherLookup.getActiveLauncher(entry.getJobId());
            if (launcher == null) {
                if (progressTracker != null) {
                    progressTracker.onPassiveEgress(FailureReason.SHUTDOWN);
                }
                FlowMetrics.recordError("launcher_not_found_drain", entry.getJobId());
                FlowMetrics.recordFailureReason(FailureReason.SHUTDOWN, entry.getJobId());
                entry.close();
                continue;
            }
            finalizer.submitBodyOnly(entry, launcher);
        }
        if (drainedCount > 0) {
            FlowMetrics.incrementCounter("queue_drain_count", drainedCount);
        }
    }

    @Override
    public boolean doDeposit(FlowEntry<T> ctx) {
        boolean success = queue.offer(ctx);
        if (success) {
            if (log.isDebugEnabled()) {
                log.debug("Data deposited into queue: jobId={}, queueSize={}", ctx.getJobId(), queue.size());
            }
            FlowMetrics.recordResourceUsage("queue_size", queue.size());
            return true;
        }
        if (log.isWarnEnabled()) {
            log.warn("Queue full, task rejected: jobId={}", ctx.getJobId());
        }
        FlowMetrics.recordError("queue_full_rejected", ctx.getJobId());
        return false;
    }
    
    @Override
    public long size() {
        long currentSize = queue.size();
        // 记录队列使用情况
        FlowMetrics.recordResourceUsage("queue_size", currentSize);
        FlowMetrics.recordResourceUsage("queue_max_size", maxCacheSize);
        return currentSize;
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