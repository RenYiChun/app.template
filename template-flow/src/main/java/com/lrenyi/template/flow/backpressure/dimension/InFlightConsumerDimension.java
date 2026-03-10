package com.lrenyi.template.flow.backpressure.dimension;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.lrenyi.template.flow.backpressure.BackpressureMetricNames;
import com.lrenyi.template.flow.backpressure.DimensionContext;
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
    public void acquire(DimensionContext ctx) throws InterruptedException, TimeoutException {
        PermitPair pair = ctx.getInFlightConsumerPermitPair();
        if (pair == null) {
            return;
        }
        
        MeterRegistry registry = ctx.getMeterRegistry();
        String jobId = ctx.getJobId();
        
        Counter.builder(BackpressureMetricNames.DIM_ACQUIRE_ATTEMPTS)
               .tag(BackpressureMetricNames.TAG_JOB_ID, jobId)
               .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
               .register(registry)
               .increment();
        
        Timer.Sample sample = Timer.start(registry);
        boolean acquired;
        try {
            acquired = pair.tryAcquireBoth(1, SLOT_ACQUIRE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } finally {
            sample.stop(Timer.builder(BackpressureMetricNames.DIM_ACQUIRE_DURATION)
                             .tag(BackpressureMetricNames.TAG_JOB_ID, jobId)
                             .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
                             .register(registry));
        }
        
        if (!acquired) {
            Counter.builder(BackpressureMetricNames.DIM_ACQUIRE_TIMEOUT)
                   .tag(BackpressureMetricNames.TAG_JOB_ID, jobId)
                   .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
                   .register(registry)
                   .increment();
            log.warn("In-flight-consumer slot acquire timeout, jobId={}, timeoutMs={}", jobId, SLOT_ACQUIRE_TIMEOUT_MS);
            throw new TimeoutException("in-flight-consumer slot acquire timeout for jobId=" + jobId + ", timeoutMs="
                                               + SLOT_ACQUIRE_TIMEOUT_MS);
        }
        
        Counter.builder(BackpressureMetricNames.DIM_ACQUIRE_BLOCKED)
               .tag(BackpressureMetricNames.TAG_JOB_ID, jobId)
               .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
               .register(registry)
               .increment();
    }
    
    @Override
    public void onBusinessRelease(DimensionContext ctx) {
        PermitPair pair = ctx.getInFlightConsumerPermitPair();
        if (pair == null) {
            return;
        }
        pair.release(1);
        Counter.builder(BackpressureMetricNames.DIM_RELEASE_COUNT)
               .tag(BackpressureMetricNames.TAG_JOB_ID, ctx.getJobId())
               .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
               .register(ctx.getMeterRegistry())
               .increment();
    }
}
