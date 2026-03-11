package com.lrenyi.template.flow.backpressure.dimension;

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
 * 生产线程背压维度：限制同时持有生产线程许可的并发数。
 * 对应配置：{@code flow.limits.per-job.producer-threads} 与全局
 * {@code flow.limits.global.producer-threads}。
 * <p>
 * 该维度为 SPI 扩展点；框架核心生产线程限制已由 ProducerExecutor 的
 * 信号量受控线程池自动处理（通过 PermitPair 构造的执行器）。
 */
@Slf4j
public class ProducerConcurrencyDimension implements ResourceBackpressureDimension {
    
    public static final String ID = "producer-concurrency";
    
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
        PermitPair pair = ctx.getProducerPermitPair();
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
        
        Timer.Sample sample = Timer.start(registry);
        boolean acquired;
        try {
            acquired = pair.tryAcquireBoth(permits);
        } finally {
            sample.stop(Timer.builder(BackpressureMetricNames.DIM_ACQUIRE_DURATION)
                             .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
                             .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
                             .register(registry));
        }
        
        if (!acquired) {
            Counter.builder(BackpressureMetricNames.DIM_ACQUIRE_TIMEOUT)
                   .tag(BackpressureMetricNames.TAG_JOB_ID, metricJobId)
                   .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
                   .register(registry)
                   .increment();
            throw new TimeoutException("producer-concurrency acquire failed for jobId=" + ctx.getJobId());
        }
    }
    
    @Override
    public void onBusinessRelease(DimensionContext ctx, int permits) {
        if (permits <= 0) {
            return;
        }
        PermitPair pair = ctx.getProducerPermitPair();
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
