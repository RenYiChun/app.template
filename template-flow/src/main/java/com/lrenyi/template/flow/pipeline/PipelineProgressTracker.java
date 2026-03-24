package com.lrenyi.template.flow.pipeline;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.context.FlowProgressSnapshot;
import com.lrenyi.template.flow.internal.DefaultProgressTracker;

/**
 * 管道全局进度追踪器。通过聚合各阶段子 Job 的快照提供全局视图。
 * 针对 Java 21+ 虚拟线程进行了优化，避免使用 synchronized 导致的 Carrier Thread Pinning。
 * <p>
 * 生产/消费等生命周期信号由引擎按阶段下发到各子 {@link ProgressTracker}；
 * 本类对 {@link #onProductionAcquired()} 等方法保持空实现，避免将「管道级一次调用」误 fan-out 到各子任务导致重复计数。
 * 观测请以 {@link #getSnapshot()}、{@link #isCompleted(boolean)}、{@link #getCompletionFuture()} 为准。
 * </p>
 */
public class PipelineProgressTracker implements ProgressTracker {
    private final String jobId;
    private final List<ProgressTracker> trackers = new CopyOnWriteArrayList<>();
    private volatile String metricJobId;

    public PipelineProgressTracker(String jobId) {
        this.jobId = jobId;
    }

    public void addTracker(ProgressTracker tracker) {
        this.trackers.add(tracker);
    }

    @Override
    public void onProductionAcquired() {
    }

    @Override
    public void onProductionReleased() {
    }

    @Override
    public void onConsumerAcquired() {
    }

    @Override
    public void onConsumerReleased(String jobId) {
    }

    @Override
    public void onTerminated(int count) {
    }

    @Override
    public FlowProgressSnapshot getSnapshot() {
        if (trackers.isEmpty()) {
            return new FlowProgressSnapshot(jobId, 0, 0, 0, 0, 0, 0, System.currentTimeMillis(), 0);
        }

        // CopyOnWriteArrayList 的读操作是无锁且线程安全的
        FlowProgressSnapshot first = trackers.get(0).getSnapshot();
        FlowProgressSnapshot last = trackers.get(trackers.size() - 1).getSnapshot();

        long inStorage = 0;
        long activeConsumers = 0;

        for (ProgressTracker tracker : trackers) {
            FlowProgressSnapshot snapshot = tracker.getSnapshot();
            inStorage += snapshot.inStorage();
            activeConsumers += snapshot.activeConsumers();
        }

        return new FlowProgressSnapshot(
                jobId,
                first.totalExpected(),
                first.productionAcquired(),
                first.productionReleased(),
                activeConsumers,
                inStorage,
                last.terminated(),
                first.startTimeMillis(),
                last.endTimeMillis());
    }

    @Override
    public void setMetricJobId(String metricJobId) {
        this.metricJobId = metricJobId;
        if (metricJobId == null || metricJobId.isEmpty()) {
            return;
        }
        for (ProgressTracker t : trackers) {
            if (t instanceof DefaultProgressTracker dpt) {
                String internal = dpt.getInternalJobId();
                if (internal.startsWith(jobId)) {
                    dpt.setMetricJobId(metricJobId + internal.substring(jobId.length()));
                } else {
                    dpt.setMetricJobId(metricJobId + ":" + internal);
                }
            }
        }
    }

    @Override
    public String getMetricJobId() {
        return metricJobId != null && !metricJobId.isEmpty() ? metricJobId : jobId;
    }

    @Override
    public void setTotalExpected(String jobId, long total) {
        for (ProgressTracker tracker : trackers) {
            tracker.setTotalExpected(jobId, total);
        }
    }

    @Override
    public void markSourceFinished(String jobId, boolean showStatus) {
    }

    @Override
    public boolean isCompleted(boolean showStatus) {
        if (trackers.isEmpty()) {
            return false;
        }
        for (ProgressTracker tracker : trackers) {
            if (!tracker.isCompleted(showStatus)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isCompletionConditionMet() {
        if (trackers.isEmpty()) {
            return false;
        }
        for (ProgressTracker tracker : trackers) {
            if (!tracker.isCompletionConditionMet()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public CompletableFuture<Void> getCompletionFuture() {
        if (trackers.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<?>[] futures = trackers.stream()
                .map(ProgressTracker::getCompletionFuture)
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    List<ProgressTracker> getTrackers() {
        return trackers;
    }
}
