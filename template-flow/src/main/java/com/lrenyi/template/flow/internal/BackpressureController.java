package com.lrenyi.template.flow.internal;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.model.FlowConstants;
import com.lrenyi.template.flow.storage.FlowStorage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BackpressureController {
    private final Lock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final FlowStorage<?> flowStorage;
    private final IntSupplier consumerAvailablePermitsSupplier;
    private final LongSupplier pendingCountSupplier;
    private final IntSupplier globalSemaphoreCapacitySupplier;
    private final MeterRegistry meterRegistry;
    private final String jobId;
    
    public BackpressureController(FlowStorage<?> flowStorage, MeterRegistry meterRegistry, String jobId) {
        this(flowStorage, null, null, null, meterRegistry, jobId);
    }
    
    public BackpressureController(FlowStorage<?> flowStorage,
            IntSupplier consumerAvailablePermitsSupplier,
            LongSupplier pendingCountSupplier,
            IntSupplier globalSemaphoreCapacitySupplier,
            MeterRegistry meterRegistry,
            String jobId) {
        this.flowStorage = flowStorage;
        this.consumerAvailablePermitsSupplier = consumerAvailablePermitsSupplier;
        this.pendingCountSupplier = pendingCountSupplier;
        this.globalSemaphoreCapacitySupplier = globalSemaphoreCapacitySupplier;
        this.meterRegistry = meterRegistry;
        this.jobId = jobId;
    }
    
    public BackpressureController(FlowStorage<?> flowStorage,
            IntSupplier consumerAvailablePermitsSupplier,
            MeterRegistry meterRegistry,
            String jobId) {
        this(flowStorage, consumerAvailablePermitsSupplier, null, null, meterRegistry, jobId);
    }
    
    /** 生产者调用：缓存满或消费许可耗尽时阻塞 */
    public void awaitSpace(BooleanSupplier stopCheck) throws InterruptedException {
        lock.lock();
        try {
            long waitStartTime = System.currentTimeMillis();
            int waitCount = 0;
            
            while (!stopCheck.getAsBoolean()) {
                boolean cacheFull = flowStorage.size() >= flowStorage.maxCacheSize();
                boolean consumerSaturated =
                        consumerAvailablePermitsSupplier != null && consumerAvailablePermitsSupplier.getAsInt() <= 0;
                boolean pendingOverflow = pendingCountSupplier != null && globalSemaphoreCapacitySupplier != null
                        && pendingCountSupplier.getAsLong() >= globalSemaphoreCapacitySupplier.getAsInt();
                if (!cacheFull && !consumerSaturated && !pendingOverflow) {
                    break;
                }
                waitCount++;
                if (!notFull.await(FlowConstants.DEFAULT_BACKPRESSURE_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS)
                        && log.isTraceEnabled()) {
                    log.trace("Backpressure: timeout waiting for space, retrying check...");
                }
            }
            
            if (waitCount > 0) {
                long waitDuration = System.currentTimeMillis() - waitStartTime;
                Timer.builder(FlowMetricNames.BACKPRESSURE_DURATION)
                     .tag(FlowMetricNames.TAG_JOB_ID, jobId)
                     .register(meterRegistry)
                     .record(waitDuration, TimeUnit.MILLISECONDS);
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
