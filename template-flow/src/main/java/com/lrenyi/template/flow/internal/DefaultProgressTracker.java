package com.lrenyi.template.flow.internal;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.context.FlowProgressSnapshot;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.storage.FlowStorage;
import com.lrenyi.template.flow.util.FlowLogHelper;
import io.micrometer.core.instrument.Counter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultProgressTracker implements ProgressTracker {
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
    
    // --- 物理水位计数器 (使用 LongAdder 保证高并发写性能) ---
    // [活跃消费许可数]：当前正在系统中"生存"的数据总量（已入库但未终结）
    private final LongAdder activeConsumers = new LongAdder();
    // [物理终结计数]：数据彻底离开框架、释放所有资源的累计总量
    private final LongAdder terminated = new LongAdder();
    private final CompletableFuture<Void> completionFuture = new CompletableFuture<>();
    // 使用 Lock 替代 synchronized，避免虚拟线程 Pinning 现象
    private final ReentrantLock finishLock = new ReentrantLock();
    private final AtomicLong lastCompletionBlockedLogMillis = new AtomicLong(0L);
    /** 用于指标标签的 jobId（可读展示名），null 时使用 jobId */
    private volatile String metricJobId;
    // 业务预期的总条数，由 Source 探测或业务方指定
    private volatile long totalExpected = -1L;
    // 生产端状态：标记 Source 是否已经彻底读完
    private volatile boolean sourceFinished = false;
    
    public DefaultProgressTracker(String jobId, FlowManager flowManager) {
        this.jobId = jobId;
        this.flowManager = flowManager;
    }
    
    @Override
    public void onProductionAcquired() {
        productionAcquired.increment();
        if (totalExpected != -1 && productionAcquired.sum() >= totalExpected) {
            markSourceFinished(jobId);
        }
    }
    
    @Override
    public void onProductionReleased() {
        productionReleased.increment();
    }
    
    /**
     * 获取全局消费许可：
     * 数据在入库（Storage）时调用，代表该单位正式进入业务生命周期
     */
    @Override
    public void onConsumerAcquired() {
        activeConsumers.increment();
    }
    
    @Override
    public void onActiveEgress() {
        terminated.increment();
        checkCompletion();
    }
    
    @Override
    public void onPassiveEgress(EgressReason reason) {
        terminated.increment();
        checkCompletion();
    }
    
    @Override
    public void onConsumerReleased(String jobId) {
        activeConsumers.decrement();
        incrementCounter(FlowMetricNames.TERMINATED);
        checkCompletion();
    }
    
    private void incrementCounter(String name) {
        String tagJobId = (metricJobId != null && !metricJobId.isEmpty()) ? metricJobId : jobId;
        Counter.builder(name)
               .tag(FlowMetricNames.TAG_JOB_ID, tagJobId)
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
                                        activeConsumers.sum(),
                                        inStorage,
                                        terminated.sum(),
                                        startTimeMillis,
                                        endTimeMillis.get()
        );
    }
    
    @Override
    public void setMetricJobId(String metricJobId) {
        this.metricJobId = metricJobId;
    }

    @Override
    public String getMetricJobId() {
        return metricJobId;
    }

    @Override
    public void setTotalExpected(String jobId, long total) {
        this.totalExpected = total;
    }
    
    @Override
    public void markSourceFinished(String jobId) {
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
        checkCompletion();
    }
    
    @Override
    public boolean isCompleted() {
        if (completionFuture.isDone()) {
            return true;
        }
        checkCompletion();
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
    
    /**
     * 核心判定逻辑：Source 已停止，且生产/存储/消费均已收敛。
     * 完成条件：sourceFinished && inStorage==0 && activeConsumers==0 && inProduction<=0 && pendingConsumer<=0 && inFlightPush==0。
     */
    private void checkCompletion() {
        CompletionState state = computeCompletionState();
        if (!state.completionConditionMet()) {
            logCompletionBlocked(state);
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
                FlowLauncher<Object> activeLauncher = flowManager.getActiveLauncher(jobId);
                boolean stopped =
                        flowManager.isStopped(jobId) || (activeLauncher != null && activeLauncher.isStopped());
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
                if (!stopped) {
                    flowManager.unregister(jobId);
                }
                completionFuture.complete(null);
            }
        } finally {
            finishLock.unlock();
        }
    }

    private void logCompletionBlocked(CompletionState state) {
        long now = System.currentTimeMillis();
        long last = lastCompletionBlockedLogMillis.get();
        if (now - last < 10_000L || !lastCompletionBlockedLogMillis.compareAndSet(last, now)) {
            return;
        }
        boolean waitingSourceFinished = !sourceFinished;
        boolean waitingStorageDrained = state.inStorage() > 0L;
        boolean waitingConsumerReleased = state.activeConsumers() > 0L;
        boolean waitingProductionReleased = state.inProduction() > 0L;
        boolean waitingPendingConsumer = state.pendingConsumer() > 0L;
        boolean waitingInFlightPush = state.inFlightPush() > 0;
        log.info("Job completion pending, {}, waitingSourceFinished={}, waitingStorageDrained={}, "
                         + "waitingConsumerReleased={}, waitingProductionReleased={}, waitingPendingConsumer={}, "
                         + "waitingInFlightPush={}, productionAcquired={}, productionReleased={}, terminated={}, "
                         + "inStorage={}, activeConsumers={}, inProduction={}, pendingConsumer={}, inFlightPush={}",
                 FlowLogHelper.formatJobContext(jobId, metricJobId),
                 waitingSourceFinished,
                 waitingStorageDrained,
                 waitingConsumerReleased,
                 waitingProductionReleased,
                 waitingPendingConsumer,
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
                sourceFinished && s.inStorage() <= 0L && s.activeConsumers() <= 0L && inProduction <= 0L
                        && pendingConsumer == 0L && inFlightPush == 0;
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
