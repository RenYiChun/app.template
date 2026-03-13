package com.lrenyi.template.flow.backpressure.dimension;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.backpressure.BackpressureTimeoutException;
import com.lrenyi.template.flow.backpressure.BackpressureMetricNames;
import com.lrenyi.template.flow.backpressure.DimensionContext;
import com.lrenyi.template.flow.util.FlowLogHelper;
import com.lrenyi.template.flow.backpressure.ResourceBackpressureDimension;
import com.lrenyi.template.flow.resource.PermitPair;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * 在途生产背压维度：限制同时"已 acquire 未 release"的生产条数，达限时阻塞生产线程。
 * 对应配置：{@code flow.limits.per-job.in-flight-production} 与全局 {@code in-flight-production}。
 */
@Slf4j
public class InFlightProductionDimension implements ResourceBackpressureDimension {
    
    public static final String ID = "in-flight-production";
    
    /** 轮询间隔（毫秒）：在停止检查循环中每次等待的最长时间 */
    private static final long CHECK_INTERVAL_MS = 200L;
    
    @Override
    public String id() {
        return ID;
    }
    
    @Override
    public int order() {
        return 100;
    }
    
    @Override
    public void acquire(DimensionContext ctx, int permits) throws InterruptedException, TimeoutException {
        if (permits <= 0) {
            return;
        }
        PermitPair pair = ctx.getInFlightPermitPair();
        if (pair == null) {
            return;
        }
        TemplateConfigProperties.Flow flowConfig = ctx.getFlowConfig();
        boolean metricsEnabled = flowConfig != null
                && flowConfig.getLimits().getGlobal().getInFlightProduction() > 0;
        MeterRegistry registry = ctx.getMeterRegistry();
        String metricJobId = ctx.getMetricJobIdForTags();
        if (metricsEnabled) {
            recordAttempts(registry, metricJobId, pair);
        }
        boolean blockForever = flowConfig == null || flowConfig.getProducerBackpressureBlockingMode()
                == TemplateConfigProperties.Flow.BackpressureBlockingMode.BLOCK_FOREVER;
        long timeoutMs = (flowConfig != null) ? flowConfig.getProducerBackpressureTimeoutMill() : 30_000L;
        Timer.Sample sample = metricsEnabled ? Timer.start(registry) : null;
        boolean acquired = false;
        PermitPair.AcquireResult lastResult = PermitPair.AcquireResult.SUCCESS;
        long startMs = System.currentTimeMillis();
        long startNanos = System.nanoTime();
        try {
            if (blockForever) {
                while (!ctx.getStopCheck().getAsBoolean()) {
                    PermitPair.AcquireResult result =
                            pair.tryAcquireBothWithResult(permits, CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
                    lastResult = result;
                    if (result == PermitPair.AcquireResult.SUCCESS) {
                        acquired = true;
                        break;
                    }
                    if (metricsEnabled) {
                        recordBlocked(registry, metricJobId, result);
                    }
                }
            } else {
                long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs);
                while (!ctx.getStopCheck().getAsBoolean()) {
                    long elapsedNanos = System.nanoTime() - startNanos;
                    if (elapsedNanos >= timeoutNanos) {
                        break;
                    }
                    long remainingMs = TimeUnit.NANOSECONDS.toMillis(timeoutNanos - elapsedNanos);
                    long waitMs = Math.min(remainingMs, CHECK_INTERVAL_MS);
                    PermitPair.AcquireResult result =
                            pair.tryAcquireBothWithResult(permits, waitMs, TimeUnit.MILLISECONDS);
                    lastResult = result;
                    if (result == PermitPair.AcquireResult.SUCCESS) {
                        acquired = true;
                        break;
                    }
                    if (metricsEnabled) {
                        recordBlocked(registry, metricJobId, result);
                    }
                }
            }
            if (!acquired && !ctx.getStopCheck().getAsBoolean()) {
                if (metricsEnabled) {
                    recordTimeout(registry, metricJobId, lastResult);
                }
                long waitedMs = System.currentTimeMillis() - startMs;
                int perJobAvail = pair.getPerJobAvailablePermits();
                int globalAvail = pair.getGlobalAvailablePermits();
                log.warn("In-flight-production acquire timeout, {}, waitedMs={}, "
                                 + "perJobAvailablePermits={}, globalAvailablePermits={}",
                         FlowLogHelper.formatJobContext(ctx.getJobId(), ctx.getMetricJobIdForTags()),
                         waitedMs,
                         perJobAvail,
                         globalAvail == -1 ? "unlimited" : globalAvail
                );
                throw new BackpressureTimeoutException(
                        "in-flight-production acquire timeout for jobId=" + ctx.getJobId() + ", waitedMs=" + waitedMs,
                        lastResult);
            }
        } finally {
            if (metricsEnabled && sample != null) {
                recordDuration(registry, metricJobId, pair, sample);
            }
        }
    }

    private void recordAttempts(MeterRegistry registry, String metricJobId, PermitPair pair) {
        if (pair.hasGlobal()) {
            Counter.builder(BackpressureMetricNames.DIM_ACQUIRE_ATTEMPTS_GLOBAL)
                   .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
                   .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
                   .register(registry)
                   .increment();
        }
        if (pair.hasPerJob()) {
            Counter.builder(BackpressureMetricNames.DIM_ACQUIRE_ATTEMPTS_PER_JOB)
                   .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
                   .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
                   .register(registry)
                   .increment();
        }
    }

    private void recordTimeout(MeterRegistry registry, String metricJobId, PermitPair.AcquireResult lastResult) {
        if (lastResult == PermitPair.AcquireResult.FAILED_ON_GLOBAL) {
            Counter.builder(BackpressureMetricNames.DIM_ACQUIRE_TIMEOUT_GLOBAL)
                   .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
                   .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
                   .register(registry)
                   .increment();
        } else if (lastResult == PermitPair.AcquireResult.FAILED_ON_PER_JOB) {
            Counter.builder(BackpressureMetricNames.DIM_ACQUIRE_TIMEOUT_PER_JOB)
                   .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
                   .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
                   .register(registry)
                   .increment();
        }
    }

    private void recordDuration(MeterRegistry registry, String metricJobId, PermitPair pair, Timer.Sample sample) {
        if (pair.hasGlobal()) {
            Timer t = Timer.builder(BackpressureMetricNames.DIM_ACQUIRE_DURATION_GLOBAL)
                           .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
                           .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
                           .register(registry);
            long nanos = sample.stop(t);
            if (pair.hasPerJob()) {
                Timer.builder(BackpressureMetricNames.DIM_ACQUIRE_DURATION_PER_JOB)
                     .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
                     .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
                     .register(registry)
                     .record(nanos, TimeUnit.NANOSECONDS);
            }
        } else if (pair.hasPerJob()) {
            sample.stop(Timer.builder(BackpressureMetricNames.DIM_ACQUIRE_DURATION_PER_JOB)
                            .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
                            .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
                            .register(registry));
        }
    }

    private void recordBlocked(MeterRegistry registry, String metricJobId, PermitPair.AcquireResult result) {
        if (result == PermitPair.AcquireResult.FAILED_ON_GLOBAL) {
            Counter.builder(BackpressureMetricNames.DIM_ACQUIRE_BLOCKED_GLOBAL)
                   .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
                   .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
                   .register(registry)
                   .increment();
        } else if (result == PermitPair.AcquireResult.FAILED_ON_PER_JOB) {
            Counter.builder(BackpressureMetricNames.DIM_ACQUIRE_BLOCKED_PER_JOB)
                   .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
                   .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
                   .register(registry)
                   .increment();
        }
    }
    
    @Override
    public void onBusinessRelease(DimensionContext ctx, int permits) {
        if (permits <= 0) {
            return;
        }
        PermitPair pair = ctx.getInFlightPermitPair();
        if (pair == null) {
            return;
        }
        pair.release(permits);
        if (ctx.getFlowConfig() != null
                && ctx.getFlowConfig().getLimits().getGlobal().getInFlightProduction() > 0) {
            recordRelease(ctx.getMeterRegistry(), ctx.getMetricJobIdForTags(), pair, permits);
        }
    }

    private void recordRelease(MeterRegistry registry, String metricJobId, PermitPair pair, int permits) {
        if (pair.hasGlobal()) {
            Counter.builder(BackpressureMetricNames.DIM_RELEASE_COUNT_GLOBAL)
                   .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
                   .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
                   .register(registry)
                   .increment(permits);
        }
        if (pair.hasPerJob()) {
            Counter.builder(BackpressureMetricNames.DIM_RELEASE_COUNT_PER_JOB)
                   .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
                   .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
                   .register(registry)
                   .increment(permits);
        }
    }
}
