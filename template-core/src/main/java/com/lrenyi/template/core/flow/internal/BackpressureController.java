package com.lrenyi.template.core.flow.internal;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import com.lrenyi.template.core.flow.model.FlowConstants;
import com.lrenyi.template.core.flow.metrics.FlowMetrics;
import com.lrenyi.template.core.flow.storage.FlowStorage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BackpressureController {
    private final Lock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final FlowStorage<?> flowStorage;
    /** 可选：全局可用消费许可数；许可耗尽时即背压，避免持续填缓存导致大批 TTL 驱逐和 Pending 暴增 */
    private final IntSupplier consumerAvailablePermitsSupplier;

    public BackpressureController(FlowStorage<?> flowStorage) {
        this(flowStorage, null);
    }

    /**
     * @param consumerAvailablePermitsSupplier 可选；非 null 且许可≤0 时与「缓存满」一起触发背压，消费慢即停入库
     */
    public BackpressureController(FlowStorage<?> flowStorage, IntSupplier consumerAvailablePermitsSupplier) {
        this.flowStorage = flowStorage;
        this.consumerAvailablePermitsSupplier = consumerAvailablePermitsSupplier;
    }

    /** 生产者调用：缓存满或消费许可耗尽时阻塞，避免填满后大批驱逐导致 Pending 暴增。 */
    public void awaitSpace(BooleanSupplier stopCheck) throws InterruptedException {
        lock.lock();
        try {
            long waitStartTime = System.currentTimeMillis();
            int waitCount = 0;

            while (!stopCheck.getAsBoolean()) {
                boolean cacheFull = flowStorage.size() >= flowStorage.maxCacheSize();
                boolean consumerSaturated = consumerAvailablePermitsSupplier != null
                        && consumerAvailablePermitsSupplier.getAsInt() <= 0;
                if (!cacheFull && !consumerSaturated) {
                    break;
                }
                waitCount++;
                FlowMetrics.incrementCounter("backpressure_wait");
                if (!notFull.await(
                        FlowConstants.DEFAULT_BACKPRESSURE_CHECK_INTERVAL_MS,
                        TimeUnit.MILLISECONDS) && log.isTraceEnabled()) {
                    log.trace("Backpressure: timeout waiting for space, retrying check...");
                }
            }

            if (waitCount > 0) {
                long waitDuration = System.currentTimeMillis() - waitStartTime;
                FlowMetrics.recordLatency("backpressure_wait", waitDuration);
                FlowMetrics.incrementCounter("backpressure_wait_count", waitCount);
            }
        } finally {
            lock.unlock();
        }
    }

    /** 消费者调用：当数据处理完离场时调用 */
    public void signalRelease() {
        lock.lock();
        try {
            notFull.signal();
        } finally {
            lock.unlock();
        }
    }
}
