package com.lrenyi.template.flow.internal;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.context.FlowProgressSnapshot;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.metrics.FlowMetricTags;
import com.lrenyi.template.flow.metrics.FlowResourceMetrics;
import com.lrenyi.template.flow.storage.FlowStorage;
import com.lrenyi.template.flow.util.FlowLogHelper;
import io.micrometer.core.instrument.Counter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultProgressTracker implements ProgressTracker {
    private static final long COMPLETION_BLOCKED_SAME_STATE_LOG_INTERVAL_MILLIS = 60_000L;
    private final FlowManager flowManager;
    private final String jobId;

    // 任务启动的时间戳（毫秒）
    private final long startTimeMillis = System.currentTimeMillis();

    // 任务结束的时间戳，默认为 0。任务终结时会被锁定。
    private final AtomicLong endTimeMillis = new AtomicLong(0L);
    // [生产许可获取数]：代表进入系统的原始请求总量
    private final LongAdder productionAcquired = new LongAdder();
    // [生产许可释放数]：代表成功存入存储层（Storage）的总量
    private final LongAdder productionReleased = new LongAdder();

    // --- 物理水位计数器 ---
    // [活跃消费数]：ASYNC 模式表示正在执行中的 consumer 数；INLINE 模式表示已启动的 worker 数
    private final AtomicLong activeConsumers = new AtomicLong();
    // [物理终结计数]：数据彻底离开框架、释放所有资源的累计总量
    private final LongAdder terminated = new LongAdder();
    private final CompletableFuture<Void> completionFuture = new CompletableFuture<>();
    // 使用 Lock 替代 synchronized，避免虚拟线程 Pinning 现象
    private final ReentrantLock finishLock = new ReentrantLock();
    private final AtomicLong lastCompletionBlockedLogMillis = new AtomicLong(0L);
    private final AtomicLong lastCompletionBlockedReasonMask = new AtomicLong(-1L);
    /** 用于指标标签的 jobId（可读展示名），null 时使用 jobId */
    private volatile String metricJobId;
    /** 用于指标标签的阶段显示名，null 时走默认阶段名推导。 */
    private volatile String stageDisplayName;
    /**
     * 为 true 时完成时不调用 {@link FlowManager#scheduleUnregister(String)}，由管道在全部子阶段完成后再统一注销指标。
     */
    private final boolean deferMetricsUnregister;
    // 业务预期的总条数，由 Source 探测或业务方指定
    private volatile long totalExpected = -1L;
    // 生产端状态：标记 Source 是否已经彻底读完
    private volatile boolean sourceFinished = false;

    public DefaultProgressTracker(String jobId, FlowManager flowManager) {
        this(jobId, flowManager, false);
    }

    public DefaultProgressTracker(String jobId, FlowManager flowManager, boolean deferMetricsUnregister) {
        this.jobId = jobId;
        this.flowManager = flowManager;
        this.deferMetricsUnregister = deferMetricsUnregister;
    }

    @Override
    public void onProductionAcquired() {
        productionAcquired.increment();
        incrementCounter(FlowMetricNames.PRODUCTION_ACQUIRED);
    }

    @Override
    public void onProductionReleased() {
        productionReleased.increment();
        incrementCounter(FlowMetricNames.PRODUCTION_RELEASED);
    }

    /**
     * 获取全局消费许可：
     * 数据在入库（Storage）时调用，代表该单位正式进入业务生命周期
     */
    @Override
    public void onConsumerAcquired() {
        activeConsumers.incrementAndGet();
    }

    @Override
    public void onConsumerReleased(String jobId) {
        activeConsumers.decrementAndGet();
        terminated.increment();
        incrementCounter(FlowMetricNames.TERMINATED);
        checkCompletion(false);
    }

    @Override
    public void setActiveConsumers(long activeConsumers) {
        this.activeConsumers.set(Math.max(0L, activeConsumers));
        checkCompletion(false);
    }

    @Override
    public void onTerminated(int count) {
        for (int i = 0; i < count; i++) {
            terminated.increment();
            incrementCounter(FlowMetricNames.TERMINATED);
        }
        checkCompletion(false);
    }

    private void incrementCounter(String name) {
        FlowMetricTags metricTags = FlowMetricTags.resolve(jobId, getMetricJobId(), getStageDisplayName());
        Counter.builder(name)
               .tags(metricTags.toTags())
               .register(flowManager.getMeterRegistry())
               .increment();
    }

    @Override
    public FlowProgressSnapshot getSnapshot() {
        long inStorage = 0;
        FlowLauncher<Object> activeLauncher = flowManager.getActiveLauncher(jobId);
        if (activeLauncher != null) {
            FlowStorage<Object> storage = activeLauncher.getStorage();
            inStorage = storage.size();
        }
        return new FlowProgressSnapshot(jobId,
                                        totalExpected,
                                        productionAcquired.sum(),
                                        productionReleased.sum(),
                                        activeConsumers.get(),
                                        inStorage,
                                        terminated.sum(),
                                        startTimeMillis,
                                        endTimeMillis.get()
        );
    }

    @Override
    public void setMetricJobId(String metricJobId) {
        String oldEffective = getMetricJobId();
        this.metricJobId = metricJobId;
        String newEffective = getMetricJobId();
        if (Objects.equals(oldEffective, newEffective)) {
            return;
        }
        if (flowManager == null) {
            return;
        }
        flowManager.removeMetricsForJob(jobId, oldEffective, getStageDisplayName());
        FlowLauncher<?> launcher = flowManager.getActiveLauncher(jobId);
        if (launcher != null) {
            FlowResourceMetrics.reregisterPerJob(launcher, flowManager.getMeterRegistry());
            launcher.getBackpressureManager().onMetricJobIdChanged();
        }
    }

    @Override
    public String getMetricJobId() {
        return (metricJobId != null && !metricJobId.isEmpty()) ? metricJobId : jobId;
    }

    @Override
    public void setRootJobDisplayName(String rootJobDisplayName) {
        setMetricJobId(rootJobDisplayName);
    }

    @Override
    public String getRootJobDisplayName() {
        return getMetricJobId();
    }

    @Override
    public void setStageDisplayName(String stageDisplayName) {
        this.stageDisplayName = stageDisplayName;
    }

    @Override
    public String getStageDisplayName() {
        return stageDisplayName;
    }

    /**
     * 引擎内部注册与存储键（含阶段索引、fork 路径），与 {@link #getMetricJobId()} 监控展示串分离。
     */
    public String getInternalJobId() {
        return jobId;
    }

    @Override
    public void setTotalExpected(String jobId, long total) {
        this.totalExpected = total;
    }

    @Override
    public CompletableFuture<Void> getCompletionFuture() {
        return completionFuture;
    }

    @Override
    public void markSourceFinished(String jobId, boolean showStatus) {
        if (sourceFinished) {
            return;
        }
        this.sourceFinished = true;
        CompletionState state = computeCompletionState();
        log.info("Source marked finished, {}, productionAcquired={}, productionReleased={}, terminated={}, "
                         + "inStorage={}, activeConsumers={}, inProduction={}, pendingConsumer={}, inFlightPush={}",
                 FlowLogHelper.formatJobContext(jobId, metricJobId),
                 state.acquired(),
                 state.released(),
                 state.terminated(),
                 state.inStorage(),
                 state.activeConsumers(),
                 state.inProduction(),
                 state.pendingConsumer(),
                 state.inFlightPush()
        );
        checkCompletion(showStatus);
    }

    @Override
    public boolean isCompleted(boolean showStatus) {
        if (completionFuture.isDone()) {
            return true;
        }
        checkCompletion(showStatus);
        return completionFuture.isDone();
    }

    @Override
    public boolean isCompletionConditionMet() {
        return computeCompletionState().completionConditionMet();
    }

    @Override
    public boolean isProductionComplete() {
        return sourceFinished && productionReleased.sum() >= productionAcquired.sum();
    }

    @Override
    public boolean isSourceFinished() {
        return sourceFinished;
    }

    /**
     * 核心判定逻辑：Source 已停止，且 storage / activeConsumers / inFlightPush 收敛，
     * 同时所有已 released 数据都已 terminated。
     * 当前框架语义下，inProduction / pendingConsumer 不再参与完成判定，但 terminated 仍需追平 released，
     * 否则会出现最后一条尚未真正终结、下游却被提前 markSourceFinished 的早收敛问题。
     */
    private void checkCompletion(boolean showStatus) {
        FlowLauncher<Object> activeLauncher = flowManager.getActiveLauncher(jobId);
        if (activeLauncher == null) {
            return;
        }
        TemplateConfigProperties.Flow flow = activeLauncher.getFlow();
        CompletionState state = computeCompletionState();
        if (showStatus || flow.isShowStatus()) {
            logCompletionBlocked(state);
        }
        if (!state.completionConditionMet()) {
            return;
        }
        finishLock.lock();
        try {
            if (endTimeMillis.get() == 0L) {
                CompletionState lockedState = computeCompletionState();
                if (!lockedState.completionConditionMet()) {
                    return;
                }
                endTimeMillis.set(System.currentTimeMillis());
                boolean stopped = flowManager.isStopped(jobId) || activeLauncher.isStopped();
                log.info("Job completion confirmed, {}, sourceFinished={}, productionAcquired={}, "
                                 + "productionReleased={}, terminated={}, inStorage={}, activeConsumers={}, "
                                 + "inProduction={}, pendingConsumer={}, inFlightPush={}, stopped={}",
                         FlowLogHelper.formatJobContext(jobId, metricJobId),
                         sourceFinished,
                         lockedState.acquired(),
                         lockedState.released(),
                         lockedState.terminated(),
                         lockedState.inStorage(),
                         lockedState.activeConsumers(),
                         lockedState.inProduction(),
                         lockedState.pendingConsumer(),
                         lockedState.inFlightPush(),
                         stopped
                );
                flowManager.markStageTerminal(jobId,
                                              getMetricJobId(),
                                              getStageDisplayName(),
                                              startTimeMillis,
                                              endTimeMillis.get());
                if (!stopped) {
                    activeLauncher.releaseRuntimeResources();
                }
                if (!stopped && !deferMetricsUnregister) {
                    flowManager.scheduleUnregister(jobId);
                }
                completionFuture.complete(null);
            }
        } finally {
            finishLock.unlock();
        }
    }

    private void logCompletionBlocked(CompletionState state) {
        long now = System.currentTimeMillis();
        boolean waitingSourceFinished = !sourceFinished;
        boolean waitingStorageDrained = state.inStorage() > 0L;
        boolean waitingConsumerReleased = state.activeConsumers() > 0L;
        boolean waitingProductionReleased = state.terminated() < state.released();
        boolean waitingInFlightPush = state.inFlightPush() > 0;
        long reasonMask = completionBlockedReasonMask(waitingSourceFinished,
                waitingStorageDrained,
                waitingConsumerReleased,
                waitingProductionReleased,
                false,
                waitingInFlightPush);
        long lastReasonMask = lastCompletionBlockedReasonMask.get();
        if (reasonMask != lastReasonMask) {
            if (!lastCompletionBlockedReasonMask.compareAndSet(lastReasonMask, reasonMask)) {
                return;
            }
            lastCompletionBlockedLogMillis.set(now);
        } else {
            long last = lastCompletionBlockedLogMillis.get();
            if (now - last < COMPLETION_BLOCKED_SAME_STATE_LOG_INTERVAL_MILLIS
                    || !lastCompletionBlockedLogMillis.compareAndSet(last, now)) {
                return;
            }
        }
        log.info("Job completion pending, {}, waitingSourceFinished={}, waitingStorageDrained={}, "
                         + "waitingConsumerReleased={}, waitingProductionReleased={}, waitingPendingConsumer={}, "
                         + "waitingInFlightPush={}, productionAcquired={}, productionReleased={}, terminated={}, "
                         + "inStorage={}, activeConsumers={}, inProduction={}, pendingConsumer={}, inFlightPush={}",
                 FlowLogHelper.formatJobContext(jobId, metricJobId),
                 waitingSourceFinished,
                 waitingStorageDrained,
                 waitingConsumerReleased,
                 waitingProductionReleased,
                 false,
                 waitingInFlightPush,
                 state.acquired(),
                 state.released(),
                 state.terminated(),
                 state.inStorage(),
                 state.activeConsumers(),
                 state.inProduction(),
                 state.pendingConsumer(),
                 state.inFlightPush()
        );
    }

    private long completionBlockedReasonMask(boolean waitingSourceFinished,
                                             boolean waitingStorageDrained,
                                             boolean waitingConsumerReleased,
                                             boolean waitingProductionReleased,
                                             boolean waitingPendingConsumer,
                                             boolean waitingInFlightPush) {
        long mask = 0L;
        if (waitingSourceFinished) {
            mask |= 1L;
        }
        if (waitingStorageDrained) {
            mask |= 1L << 1;
        }
        if (waitingConsumerReleased) {
            mask |= 1L << 2;
        }
        if (waitingProductionReleased) {
            mask |= 1L << 3;
        }
        if (waitingPendingConsumer) {
            mask |= 1L << 4;
        }
        if (waitingInFlightPush) {
            mask |= 1L << 5;
        }
        return mask;
    }

    /**
     * 基于快照计算完成状态，保证与 getSnapshot() 暴露的数据一致、一次取数避免中间状态不一致。
     * inFlightPush 不在快照中，仍从 launcher 读取。
     */
    private CompletionState computeCompletionState() {
        FlowProgressSnapshot s = getSnapshot();
        int inFlightPush = 0;
        FlowLauncher<Object> activeLauncher = flowManager.getActiveLauncher(jobId);
        if (activeLauncher != null) {
            inFlightPush = activeLauncher.getInFlightPushCount();
        }
        long inProduction = s.getInProductionCount();
        long pendingConsumer = s.getPendingConsumerCount();
        boolean completionConditionMet =
                sourceFinished
                        && s.inStorage() <= 0L
                        && s.activeConsumers() <= 0L
                        && s.terminated() >= s.productionReleased()
                        && inFlightPush == 0;
        return new CompletionState(s.productionAcquired(),
                                   s.productionReleased(),
                                   s.terminated(),
                                   s.inStorage(),
                                   s.activeConsumers(),
                                   inProduction,
                                   pendingConsumer,
                                   inFlightPush,
                                   completionConditionMet
        );
    }

    private record CompletionState(long acquired,
                                   long released,
                                   long terminated,
                                   long inStorage,
                                   long activeConsumers,
                                   long inProduction,
                                   long pendingConsumer,
                                   int inFlightPush,
                                   boolean completionConditionMet) {
    }
}
