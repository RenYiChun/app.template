package com.lrenyi.template.flow.internal;

import java.util.EnumMap;
import java.util.Map;
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
    // [主动出口计数]：通过 onSuccess/onConsume 等业务路径正常终结的数量
    private final LongAdder activeEgress = new LongAdder();
    // [被动出口计数]：通过过期(TTL)、淘汰(Evicted)等非业务路径终结的数量
    private final LongAdder passiveEgress = new LongAdder();
    // [按原因统计的被动出口]：仅被动原因（见 EgressReason.isPassive()），用于 Snapshot/指标按原因统计
    private final Map<EgressReason, LongAdder> passiveEgressByReason = new EnumMap<>(EgressReason.class);
    // [物理终结计数]：数据彻底离开框架、释放所有资源的累计总量
    private final LongAdder terminated = new LongAdder();
    private final CompletableFuture<Void> completionFuture = new CompletableFuture<>();
    // 使用 Lock 替代 synchronized，避免虚拟线程 Pinning 现象
    private final ReentrantLock finishLock = new ReentrantLock();
    // 业务预期的总条数，由 Source 探测或业务方指定
    private volatile long totalExpected = -1L;
    // 生产端状态：标记 Source 是否已经彻底读完
    private volatile boolean sourceFinished = false;
    
    public DefaultProgressTracker(String jobId, FlowManager flowManager) {
        this.jobId = jobId;
        this.flowManager = flowManager;
        for (EgressReason r : EgressReason.values()) {
            if (r.isPassive()) {
                passiveEgressByReason.put(r, new LongAdder());
            }
        }
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
        activeEgress.increment();
    }
    
    @Override
    public void onPassiveEgress(EgressReason reason) {
        passiveEgress.increment();
        EgressReason key = (reason != null && reason.isPassive()) ? reason : EgressReason.UNKNOWN;
        passiveEgressByReason.computeIfAbsent(key, k -> new LongAdder()).increment();
        terminated.increment();
        checkCompletion();
    }
    
    @Override
    public void onConsumerReleased(String jobId) {
        terminated.increment();
        activeConsumers.decrement();
        checkCompletion();
    }
    
    @Override
    public FlowProgressSnapshot getSnapshot() {
        long inStorage = 0;
        FlowLauncher<Object> activeLauncher = flowManager.getActiveLauncher(jobId);
        if (activeLauncher != null) {
            FlowStorage<Object> storage = activeLauncher.getStorage();
            inStorage = storage.size();
        }
        Map<String, Long> reasonMap = new java.util.HashMap<>();
        passiveEgressByReason.forEach((r, adder) -> {
            long v = adder.sum();
            if (v > 0) {
                reasonMap.put(r.name(), v);
            }
        });
        return new FlowProgressSnapshot(jobId,
                                        totalExpected,
                                        productionAcquired.sum(),
                                        productionReleased.sum(),
                                        activeConsumers.sum(),
                                        inStorage,
                                        activeEgress.sum(),
                                        passiveEgress.sum(),
                                        terminated.sum(),
                                        startTimeMillis,
                                        endTimeMillis.get(),
                                        reasonMap
        );
    }
    
    @Override
    public void setTotalExpected(String jobId, long total) {
        this.totalExpected = total;
    }
    
    @Override
    public void markSourceFinished(String jobId) {
        this.sourceFinished = true;
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
        drainStorageIfReady();
        return computeCompletionState().completionConditionMet();
    }
    
    /**
     * 核心判定逻辑：Source 已停止，且生产/存储/消费均已收敛。
     * 完成条件：sourceFinished && inStorage==0 && activeConsumers==0 && inProduction<=0 && pendingConsumer<=0。
     */
    private void checkCompletion() {
        drainStorageIfReady();
        CompletionState state = computeCompletionState();
        if (!state.completionConditionMet()) {
            return;
        }
        log.debug("Job {} completed, acquired: {}, released: {}, terminated: {}, inStorage: {}, activeConsumers: {},"
                          + " inProduction: {}, pendingConsumer: {}",
                  jobId,
                  state.acquired(),
                  state.released(),
                  state.terminated(),
                  state.inStorage(),
                  state.activeConsumers(),
                  state.inProduction(),
                  state.pendingConsumer()
        );
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
                if (!stopped) {
                    Counter.builder(FlowMetricNames.JOB_COMPLETED)
                           .tag(FlowMetricNames.TAG_JOB_ID, jobId)
                           .register(flowManager.getMeterRegistry())
                           .increment();
                    flowManager.unregister(jobId);
                }
                completionFuture.complete(null);
            }
        } finally {
            finishLock.unlock();
        }
    }
    
    private CompletionState computeCompletionState() {
        long acquired = productionAcquired.sum();
        long released = productionReleased.sum();
        long term = terminated.sum();
        long inStorage = 0L;
        FlowLauncher<Object> activeLauncher = flowManager.getActiveLauncher(jobId);
        if (activeLauncher != null) {
            inStorage = activeLauncher.getStorage().size();
        }
        long active = activeConsumers.sum();
        long passive = passiveEgress.sum();
        long inProduction = acquired - released;
        long pendingConsumer = released - inStorage - active - term - passive;
        boolean completionConditionMet =
                sourceFinished && inStorage <= 0L && active <= 0L && inProduction <= 0L && pendingConsumer <= 0L;
        return new CompletionState(acquired,
                                   released,
                                   term,
                                   inStorage,
                                   active,
                                   inProduction,
                                   pendingConsumer,
                                   completionConditionMet
        );
    }
    
    private void drainStorageIfReady() {
        if (!sourceFinished) {
            return;
        }
        long inProduction = productionAcquired.sum() - productionReleased.sum();
        if (inProduction > 0L) {
            return;
        }
        FlowLauncher<Object> launcher = flowManager.getActiveLauncher(jobId);
        if (launcher == null) {
            return;
        }
        FlowStorage<?> storage = launcher.getStorage();
        int remain = storage.drainRemainingToFinalizer();
        log.debug("the storage is draining, remain is {}", remain);
    }
    
    private record CompletionState(long acquired,
                                   long released,
                                   long terminated,
                                   long inStorage,
                                   long activeConsumers,
                                   long inProduction,
                                   long pendingConsumer,
                                   boolean completionConditionMet) {
    }
}
