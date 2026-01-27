package com.lrenyi.template.core.flow.impl;

import com.lrenyi.template.core.flow.ProgressTracker;
import com.lrenyi.template.core.flow.context.FlowProgressSnapshot;
import com.lrenyi.template.core.flow.manager.FlowManager;
import com.lrenyi.template.core.flow.storage.FlowStorage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultProgressTracker implements ProgressTracker {
    private final FlowManager flowManager;
    private final String jobId;
    
    // 任务启动的时间戳（毫秒）
    private final long startTimeMillis = System.currentTimeMillis();
    
    // 任务结束的时间戳，默认为 0。任务终结时会被锁定。
    private final AtomicLong endTimeMillis = new AtomicLong(0L);
    
    // 业务预期的总条数，由 Source 探测或业务方指定
    private volatile long totalExpected = -1L;
    
    // 生产端状态：标记 Source 是否已经彻底读完
    private volatile boolean sourceFinished = false;
    
    // --- 物理水位计数器 (使用 LongAdder 保证高并发写性能) ---
    
    // [生产许可获取数]：代表进入系统的原始请求总量
    private final LongAdder productionAcquired = new LongAdder();
    
    // [生产许可释放数]：代表成功存入存储层（Storage）的总量
    private final LongAdder productionReleased = new LongAdder();
    
    // [活跃消费许可数]：当前正在系统中“生存”的数据总量（已入库但未终结）
    private final LongAdder activeConsumers = new LongAdder();
    
    // [主动出口计数]：通过 onSuccess/onConsume 等业务路径正常终结的数量
    private final LongAdder activeEgress = new LongAdder();
    
    // [被动出口计数]：通过过期(TTL)、淘汰(Evicted)等非业务路径终结的数量
    private final LongAdder passiveEgress = new LongAdder();
    
    // [物理终结计数]：数据彻底离开框架、释放所有资源的累计总量
    private final LongAdder terminated = new LongAdder();
    
    private final CompletableFuture<Void> completionFuture = new CompletableFuture<>();
    
    // 使用 Lock 替代 synchronized，避免虚拟线程 Pinning 现象
    private final ReentrantLock finishLock = new ReentrantLock();
    
    public DefaultProgressTracker(String jobId, FlowManager flowManager) {
        this.jobId = jobId;
        this.flowManager = flowManager;
    }
    
    @Override
    public void onProductionAcquired() {
        productionAcquired.increment();
        if (totalExpected != -1 && productionAcquired.sum() >= totalExpected) {
            // 告诉 Tracker：生产端已关闭。
            // 这样当 activeConsumers 归零时，getCompletionFuture() 就会完成。
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
    public void onConsumerBegin() {
        activeConsumers.increment();
    }
    
    @Override
    public void onActiveEgress() {
        activeEgress.increment();
    }
    
    @Override
    public void onPassiveEgress() {
        passiveEgress.increment();
    }
    
    @Override
    public void onGlobalTerminated(String jobId) {
        terminated.increment();
        activeConsumers.decrement();
        // 尝试触发终结判定
        checkCompletion();
    }
    
    @Override
    public void markSourceFinished(String jobId) {
        this.sourceFinished = true;
    }
    
    /**
     * 核心判定逻辑：只有当 Source 停止且系统内无存量活跃数据时，锁定任务状态
     */
    private void checkCompletion() {
        if (sourceFinished && totalExpected != -1 && terminated.sum() >= totalExpected) {
            finishLock.lock();
            try {
                if (endTimeMillis.get() == 0L) {
                    endTimeMillis.set(System.currentTimeMillis());
                    completionFuture.complete(null);
                }
            } finally {
                finishLock.unlock();
            }
        }
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
                                        activeEgress.sum(),
                                        passiveEgress.sum(),
                                        terminated.sum(),
                                        startTimeMillis,
                                        endTimeMillis.get()
        );
    }
    
    @Override
    public CompletableFuture<Void> getCompletionFuture() {
        return completionFuture;
    }
    
    @Override
    public void setTotalExpected(String jobId, long total) {
        this.totalExpected = total;
    }
}