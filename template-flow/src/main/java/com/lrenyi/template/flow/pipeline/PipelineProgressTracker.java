package com.lrenyi.template.flow.pipeline;

import java.util.Collections;
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
    /** 与 trackers 一一对应：该阶段是否为当前子链路的 Sink（无下游阶段）。Fork 下可有多个 true。 */
    private final List<Boolean> leafStage = new CopyOnWriteArrayList<>();
    private volatile String metricJobId;

    public PipelineProgressTracker(String jobId) {
        this.jobId = jobId;
    }

    public void addTracker(ProgressTracker tracker, boolean leaf) {
        this.trackers.add(tracker);
        this.leafStage.add(leaf);
    }

    /**
     * 与 {@link FlowPipelineImpl} 中 {@link Collections#reverse} 同步，保持阶段顺序与 launchers 一致。
     */
    void reversePipelineOrder() {
        Collections.reverse(trackers);
        Collections.reverse(leafStage);
    }

    List<ProgressTracker> getTrackers() {
        return trackers;
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

        FlowProgressSnapshot first = trackers.get(0).getSnapshot();

        long inStorage = 0;
        long activeConsumers = 0;
        long aggregatedTerminated = 0;
        long maxEndTime = 0;
        boolean allEnded = true;

        for (int i = 0; i < trackers.size(); i++) {
            ProgressTracker tracker = trackers.get(i);
            FlowProgressSnapshot snapshot = tracker.getSnapshot();
            inStorage += snapshot.inStorage();
            activeConsumers += snapshot.activeConsumers();
            if (i < leafStage.size() && Boolean.TRUE.equals(leafStage.get(i))) {
                aggregatedTerminated += snapshot.terminated();
            }
            long end = snapshot.endTimeMillis();
            if (end == 0) {
                allEnded = false;
            }
            maxEndTime = Math.max(maxEndTime, end);
        }
        if (leafStage.size() != trackers.size()) {
            aggregatedTerminated = trackers.get(trackers.size() - 1).getSnapshot().terminated();
        }
        long pipelineEnd = allEnded ? maxEndTime : 0L;

        return new FlowProgressSnapshot(
                jobId,
                first.totalExpected(),
                first.productionAcquired(),
                first.productionReleased(),
                activeConsumers,
                inStorage,
                aggregatedTerminated,
                first.startTimeMillis(),
                pipelineEnd);
    }

    /**
     * 更新管道级展示名并下发到各子阶段；Micrometer 清理与 Gauge/背压重绑由各 {@link DefaultProgressTracker#setMetricJobId} 统一处理。
     * 若此前已有数据写入，旧 Counter 上的累计值会随清理丢失，宜在首次生产前调用。
     */
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

    @Override
    public CompletableFuture<Void> getProductionDrainedFuture() {
        if (trackers.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<?>[] futures = trackers.stream()
                .map(ProgressTracker::getProductionDrainedFuture)
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }
}
