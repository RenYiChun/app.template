package com.lrenyi.template.flow.backpressure.dimension;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.lrenyi.template.core.TemplateConfigProperties;
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
        
        MeterRegistry registry = ctx.getMeterRegistry();
        String metricJobId = ctx.getMetricJobIdForTags();
        
        Counter.builder(BackpressureMetricNames.DIM_ACQUIRE_ATTEMPTS)
               .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
               .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
               .register(registry)
               .increment();
        
        TemplateConfigProperties.Flow flowConfig = ctx.getFlowConfig();
        boolean blockForever = flowConfig == null || flowConfig.getProducerBackpressureBlockingMode()
                == TemplateConfigProperties.Flow.BackpressureBlockingMode.BLOCK_FOREVER;
        long timeoutMs = (flowConfig != null) ? flowConfig.getProducerBackpressureTimeoutMill() : 30_000L;
        
        Timer.Sample sample = Timer.start(registry);
        boolean acquired = false;
        boolean blocked = false;
        long startMs = System.currentTimeMillis();
        long startNanos = System.nanoTime();
        
        try {
            if (blockForever) {
                // Repeatedly try with short intervals to honour stopCheck
                while (!ctx.getStopCheck().getAsBoolean()) {
                    if (pair.tryAcquireBoth(permits, CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS)) {
                        acquired = true;
                        break;
                    }
                    blocked = true;
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
                    if (pair.tryAcquireBoth(permits, waitMs, TimeUnit.MILLISECONDS)) {
                        acquired = true;
                        break;
                    }
                    blocked = true;
                }
            }
            
            if (blocked) {
                Counter.builder(BackpressureMetricNames.DIM_ACQUIRE_BLOCKED)
                       .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
                       .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
                       .register(registry)
                       .increment();
            }
            
            if (!acquired && !ctx.getStopCheck().getAsBoolean()) {
                Counter.builder(BackpressureMetricNames.DIM_ACQUIRE_TIMEOUT)
                       .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
                       .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
                       .register(registry)
                       .increment();
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
                throw new TimeoutException(
                        "in-flight-production acquire timeout for jobId=" + ctx.getJobId() + ", waitedMs=" + waitedMs);
            }
        } finally {
            sample.stop(Timer.builder(BackpressureMetricNames.DIM_ACQUIRE_DURATION)
                             .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
                             .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
                             .register(registry));
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
        Counter.builder(BackpressureMetricNames.DIM_RELEASE_COUNT)
               .tag(BackpressureMetricNames.TAG_JOB_ID, ctx.getMetricJobIdForTags())
               .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
               .register(ctx.getMeterRegistry())
               .increment(permits);
    }
}
