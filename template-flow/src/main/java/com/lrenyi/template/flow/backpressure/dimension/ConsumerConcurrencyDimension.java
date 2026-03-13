package com.lrenyi.template.flow.backpressure.dimension;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.backpressure.BackpressureTimeoutException;
import com.lrenyi.template.flow.backpressure.BackpressureMetricNames;
import com.lrenyi.template.flow.backpressure.DimensionContext;
import com.lrenyi.template.flow.backpressure.ResourceBackpressureDimension;
import com.lrenyi.template.flow.resource.PermitPair;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * 消费并发背压维度：限制同时持有消费许可的并发数。
 * 对应配置：{@code flow.limits.per-job.consumer-threads} 与全局
 * {@code flow.limits.global.consumer-threads}。
 * <p>
 * 该维度为 SPI 扩展点；框架核心消费并发由 {@link com.lrenyi.template.flow.internal.FlowFinalizer} 通过
 * {@link com.lrenyi.template.flow.backpressure.BackpressureManager} 管理（内含公平调度逻辑）。
 */
@Slf4j
public class ConsumerConcurrencyDimension implements ResourceBackpressureDimension {
    
    public static final String ID = "consumer-concurrency";
    
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
        PermitPair pair = ctx.getConsumerPermitPair();
        if (pair == null) {
            return;
        }
        TemplateConfigProperties.Flow flowConfig = ctx.getFlowConfig();
        boolean metricsEnabled = flowConfig != null
                && (flowConfig.getLimits().getGlobal().getConsumerThreads() > 0
                || flowConfig.getLimits().getPerJob().getConsumerThreads() > 0);
        MeterRegistry registry = ctx.getMeterRegistry();
        String metricJobId = ctx.getMetricJobIdForTags();
        if (metricsEnabled) {
            recordAttempts(registry, metricJobId, pair);
        }
        boolean blockForever = flowConfig == null || flowConfig.getConsumerAcquireBlockingMode()
                == TemplateConfigProperties.Flow.BackpressureBlockingMode.BLOCK_FOREVER;
        long timeoutMs = (flowConfig != null) ? flowConfig.getConsumerAcquireTimeoutMill() : 30_000L;
        Timer.Sample sample = metricsEnabled ? Timer.start(registry) : null;
        PermitPair.AcquireResult result;
        try {
            if (blockForever) {
                result = pair.tryAcquireBothWithResult(permits);
            } else {
                result = pair.tryAcquireBothWithResult(permits, timeoutMs, TimeUnit.MILLISECONDS);
            }
        } finally {
            if (metricsEnabled && sample != null) {
                recordDuration(registry, metricJobId, pair, sample);
            }
        }
        if (result != PermitPair.AcquireResult.SUCCESS) {
            if (metricsEnabled) {
                recordTimeout(registry, metricJobId, result);
                if (result == PermitPair.AcquireResult.FAILED_ON_GLOBAL) {
                    Counter.builder(BackpressureMetricNames.DIM_ACQUIRE_BLOCKED_GLOBAL)
                           .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
                           .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
                           .register(registry)
                           .increment();
                } else {
                    Counter.builder(BackpressureMetricNames.DIM_ACQUIRE_BLOCKED_PER_JOB)
                           .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
                           .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
                           .register(registry)
                           .increment();
                }
            }
            throw new BackpressureTimeoutException(
                    "consumer-concurrency acquire timeout for jobId=" + ctx.getJobId() + ", timeoutMs=" + timeoutMs,
                    result);
        }
    }
    @Override
    public void onBusinessRelease(DimensionContext ctx, int permits) {
        if (permits <= 0) {
            return;
        }
        PermitPair pair = ctx.getConsumerPermitPair();
        if (pair == null) {
            return;
        }
        pair.release(permits, ctx.getGlobalConsumerLimit());
        var fc = ctx.getFlowConfig();
        if (fc != null
                && (fc.getLimits().getGlobal().getConsumerThreads() > 0
                || fc.getLimits().getPerJob().getConsumerThreads() > 0)) {
            recordRelease(ctx.getMeterRegistry(), ctx.getMetricJobIdForTags(), pair, permits);
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

    private void recordTimeout(MeterRegistry registry, String metricJobId, PermitPair.AcquireResult result) {
        if (result == PermitPair.AcquireResult.FAILED_ON_GLOBAL) {
            Counter.builder(BackpressureMetricNames.DIM_ACQUIRE_TIMEOUT_GLOBAL)
                   .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
                   .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
                   .register(registry)
                   .increment();
        } else if (result == PermitPair.AcquireResult.FAILED_ON_PER_JOB) {
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
