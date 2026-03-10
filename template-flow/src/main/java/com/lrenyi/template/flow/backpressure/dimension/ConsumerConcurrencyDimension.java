package com.lrenyi.template.flow.backpressure.dimension;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.lrenyi.template.core.TemplateConfigProperties;
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
 * 该维度为 SPI 扩展点；框架核心消费并发由 {@code Orchestrator.acquire()} 管理（内含公平调度逻辑）。
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
        
        MeterRegistry registry = ctx.getMeterRegistry();
        String jobId = ctx.getJobId();
        
        Counter.builder(BackpressureMetricNames.DIM_ACQUIRE_ATTEMPTS)
               .tag(BackpressureMetricNames.TAG_JOB_ID, jobId)
               .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
               .register(registry)
               .increment();
        
        TemplateConfigProperties.Flow flowConfig = ctx.getFlowConfig();
        boolean blockForever = flowConfig == null || flowConfig.getConsumerAcquireBlockingMode()
                == TemplateConfigProperties.Flow.BackpressureBlockingMode.BLOCK_FOREVER;
        long timeoutMs = (flowConfig != null) ? flowConfig.getConsumerAcquireTimeoutMill() : 30_000L;
        
        Timer.Sample sample = Timer.start(registry);
        boolean acquired;
        try {
            if (blockForever) {
                acquired = pair.tryAcquireBoth(permits);
            } else {
                acquired = pair.tryAcquireBoth(permits, timeoutMs, TimeUnit.MILLISECONDS);
            }
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
            throw new TimeoutException(
                    "consumer-concurrency acquire timeout for jobId=" + jobId + ", timeoutMs=" + timeoutMs);
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
        Counter.builder(BackpressureMetricNames.DIM_RELEASE_COUNT)
               .tag(BackpressureMetricNames.TAG_JOB_ID, ctx.getJobId())
               .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
               .register(ctx.getMeterRegistry())
               .increment(permits);
    }
}
