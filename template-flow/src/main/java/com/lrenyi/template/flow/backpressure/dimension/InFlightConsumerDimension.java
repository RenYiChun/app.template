package com.lrenyi.template.flow.backpressure.dimension;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.lrenyi.template.flow.backpressure.BackpressureMetricNames;
import com.lrenyi.template.flow.backpressure.BackpressureTimeoutException;
import com.lrenyi.template.flow.backpressure.DimensionContext;
import com.lrenyi.template.flow.util.FlowLogHelper;
import com.lrenyi.template.flow.backpressure.ResourceBackpressureDimension;
import com.lrenyi.template.flow.resource.PermitPair;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * 在途消费背压维度（原 pending-consumer）：
 * 限制"已离库未终结"的条数，防止消费端积压过多、OOM。
 * 对应配置：{@code flow.limits.global.in-flight-consumer}、{@code flow.limits.per-job.in-flight-consumer}。
 */
@Slf4j
public class InFlightConsumerDimension implements ResourceBackpressureDimension {
    
    public static final String ID = "in-flight-consumer";
    
    /** 槽位获取超时时间（毫秒） */
    private static final long SLOT_ACQUIRE_TIMEOUT_MS = 30_000L;
    
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
        PermitPair pair = ctx.getInFlightConsumerPermitPair();
        if (pair == null) {
            return;
        }
        boolean metricsEnabled = ctx.getFlowConfig() != null
                && ctx.getFlowConfig().getLimits().getGlobal().getInFlightConsumer() > 0;
        MeterRegistry registry = ctx.getMeterRegistry();
        String metricJobId = ctx.getMetricJobIdForTags();
        if (metricsEnabled) {
            recordAttempts(registry, metricJobId, pair);
        }
        Timer.Sample sample = metricsEnabled ? Timer.start(registry) : null;
        PermitPair.AcquireResult result;
        try {
            result = pair.tryAcquireBothWithResult(permits, SLOT_ACQUIRE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
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
            log.warn("In-flight-consumer slot acquire timeout, {}, timeoutMs={}",
                    FlowLogHelper.formatJobContext(ctx.getJobId(), ctx.getMetricJobIdForTags()),
                    SLOT_ACQUIRE_TIMEOUT_MS);
            throw new BackpressureTimeoutException("in-flight-consumer slot acquire timeout for jobId=" + ctx.getJobId()
                                                           + ", timeoutMs="
                                                           + SLOT_ACQUIRE_TIMEOUT_MS,
                    result);
        }
    }
    @Override
    public void onBusinessRelease(DimensionContext ctx, int permits) {
        if (permits <= 0) {
            return;
        }
        PermitPair pair = ctx.getInFlightConsumerPermitPair();
        if (pair == null) {
            return;
        }
        pair.release(permits);
        if (ctx.getFlowConfig() != null
                && ctx.getFlowConfig().getLimits().getGlobal().getInFlightConsumer() > 0) {
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
