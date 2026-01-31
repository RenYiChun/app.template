package com.lrenyi.template.core.flow.impl;

import com.lrenyi.template.core.flow.FlowConstants;
import com.lrenyi.template.core.flow.metrics.FlowMetrics;
import com.lrenyi.template.core.flow.storage.FlowStorage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BackpressureController {
    private final Lock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final FlowStorage<?> flowStorage;
    
    public BackpressureController(FlowStorage<?> flowStorage) {
        this.flowStorage = flowStorage;
    }
    
    // 生产者调用：如果满了就挂起
    public void awaitSpace(BooleanSupplier stopCheck) throws InterruptedException {
        lock.lock();
        try {
            long waitStartTime = System.currentTimeMillis();
            int waitCount = 0;
            
            // 只要缓存中的数据量（Active/In-Cache）超过阈值，就阻塞
            while (flowStorage.size() >= flowStorage.maxCacheSize() && !stopCheck.getAsBoolean()) {
                waitCount++;
                // 记录背压等待
                FlowMetrics.incrementCounter("backpressure_wait");
                
                // 增加超时兜底，即使信号丢失，超时后也会重新 check 一次 size
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
    
    // 消费者调用：当数据处理完离场时调用
    public void signalRelease() {
        lock.lock();
        try {
            // 每次只唤醒一个等待者，减轻惊群、提升出缓存并发下的 TPS
            notFull.signal();
        } finally {
            lock.unlock();
        }
    }
}
