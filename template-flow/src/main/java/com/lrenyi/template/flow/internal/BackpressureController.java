package com.lrenyi.template.flow.internal;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.model.FlowConstants;
import com.lrenyi.template.flow.resource.PermitPair;
import com.lrenyi.template.flow.storage.FlowStorage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BackpressureController {
    private final Lock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final FlowStorage<?> flowStorage;
    private final MeterRegistry meterRegistry;
    private final String jobId;
    private final TemplateConfigProperties.Flow flowConfig;
    private final BackpressureSnapshotProvider snapshotProvider;
    private final BackpressurePolicy backpressurePolicy;
    
    public BackpressureController(FlowStorage<?> flowStorage,
            MeterRegistry meterRegistry,
            String jobId,
            TemplateConfigProperties.Flow flowConfig) {
        this.flowStorage = flowStorage;
        this.meterRegistry = meterRegistry;
        this.jobId = jobId;
        this.flowConfig = flowConfig;
        this.snapshotProvider = new StorageBackpressureSnapshotProvider(flowStorage);
        this.backpressurePolicy = new DefaultBackpressurePolicy();
    }

    public BackpressureController(FlowStorage<?> flowStorage,
            MeterRegistry meterRegistry,
            String jobId,
            TemplateConfigProperties.Flow flowConfig,
            BackpressureSnapshotProvider snapshotProvider,
            BackpressurePolicy backpressurePolicy) {
        this.flowStorage = flowStorage;
        this.meterRegistry = meterRegistry;
        this.jobId = jobId;
        this.flowConfig = flowConfig;
        this.snapshotProvider = snapshotProvider;
        this.backpressurePolicy = backpressurePolicy;
    }
    
    /** 生产者调用：缓存满或消费许可耗尽时阻塞 */
    public void awaitSpace(BooleanSupplier stopCheck) throws InterruptedException, TimeoutException {
        long maxWaitMs = flowConfig.getProducerBackpressureBlockingMode()
                == TemplateConfigProperties.Flow.BackpressureBlockingMode.BLOCK_WITH_TIMEOUT ?
                flowConfig.getProducerBackpressureTimeoutMill() : 0L;
        awaitSpace(stopCheck, maxWaitMs);
    }
    
    public void awaitSpace(BooleanSupplier stopCheck, long maxWaitMs) throws InterruptedException, TimeoutException {
        lock.lock();
        try {
            long waitStartTime = System.currentTimeMillis();
            long waitStartNanos = System.nanoTime();
            long maxWaitNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0L, maxWaitMs));
            int waitCount = 0;
            boolean loggedBlocked = false;
            
            while (!stopCheck.getAsBoolean()) {
                BackpressureSnapshot snapshot = snapshotProvider.snapshot();
                BackpressureDecision decision = backpressurePolicy.decide(snapshot);
                if (decision.getType() == BackpressureDecision.Type.PROCEED) {
                    break;
                }
                if (!loggedBlocked && log.isDebugEnabled()) {
                    log.debug("Backpressure triggered, jobId={}, cacheSize={}, cacheLimit={}, waitLimitMs={}",
                              jobId,
                              flowStorage.size(),
                              flowStorage.maxCacheSize(),
                              maxWaitMs
                    );
                    loggedBlocked = true;
                }
                if (maxWaitNanos > 0 && System.nanoTime() - waitStartNanos >= maxWaitNanos) {
                    long waitDuration = System.currentTimeMillis() - waitStartTime;
                    log.warn("Backpressure timeout, jobId={}, waitedMs={}, cacheSize={}, cacheLimit={}",
                             jobId,
                             waitDuration, flowStorage.size(), flowStorage.maxCacheSize()
                    );
                    throw new TimeoutException(
                            "Backpressure awaitSpace exceeded maxWaitMs=" + maxWaitMs + ", jobId=" + jobId
                                    + ", waitedMs=" + waitDuration);
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
                if (log.isDebugEnabled()) {
                    log.debug("Backpressure released, jobId={}, waitCount={}, waitedMs={}", jobId, waitCount, waitDuration);
                }
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
    
    /**
     * 生产者调用：基于多维背压视图与策略获取 in-flight 生产许可。
     * <p>
     * - snapshot + policy 决定是否需要先阻塞一段时间；
     * - 阻塞时长受 Flow 配置的 blockingMode/timeout 控制；
     * - 真正的许可获取依然通过 PermitPair.tryAcquireBoth 完成。
     */
    public boolean acquireInFlight(PermitPair inFlightPermitPair,
            BooleanSupplier stopCheck) throws InterruptedException, TimeoutException {
        if (inFlightPermitPair == null) {
            return true;
        }
        TemplateConfigProperties.Flow.BackpressureBlockingMode mode = flowConfig.getProducerBackpressureBlockingMode();
        boolean b = mode == TemplateConfigProperties.Flow.BackpressureBlockingMode.BLOCK_WITH_TIMEOUT;
        long maxWaitMs = b ? flowConfig.getProducerBackpressureTimeoutMill() : 0L;
        long startMs = System.currentTimeMillis();
        long startNanos = System.nanoTime();
        long maxWaitNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0L, maxWaitMs));
        
        while (!stopCheck.getAsBoolean()) {
            BackpressureSnapshot snapshot = snapshotProvider.snapshot();
            BackpressureDecision decision = backpressurePolicy.decide(snapshot);
            if (decision.getType() == BackpressureDecision.Type.PROCEED) {
                break;
            }
            if (maxWaitNanos > 0 && System.nanoTime() - startNanos >= maxWaitNanos) {
                long waitedMs = System.currentTimeMillis() - startMs;
                throw new TimeoutException(
                        "In-flight permit backpressure timeout for job " + jobId + ", waitedMs=" + waitedMs);
            }
            long waitMs = decision.getWaitMs();
            if (waitMs > 0L) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(waitMs));
            }
        }
        
        Timer.Sample inFlightSample = Timer.start(meterRegistry);
        boolean acquired;
        if (b && maxWaitMs > 0L) {
            long elapsedMs = System.currentTimeMillis() - startMs;
            long remainingMs = maxWaitMs - elapsedMs;
            if (remainingMs <= 0L) {
                acquired = false;
            } else {
                acquired = inFlightPermitPair.tryAcquireBoth(1, remainingMs, TimeUnit.MILLISECONDS);
            }
        } else {
            acquired = inFlightPermitPair.tryAcquireBoth(1);
        }
        inFlightSample.stop(Timer.builder(FlowMetricNames.LIMITS_ACQUIRE_WAIT_DURATION)
                                 .tag(FlowMetricNames.TAG_JOB_ID, jobId)
                                 .tag(FlowMetricNames.TAG_DIMENSION, FlowMetricNames.DIMENSION_IN_FLIGHT)
                                 .register(meterRegistry));
        return acquired;
    }
}
