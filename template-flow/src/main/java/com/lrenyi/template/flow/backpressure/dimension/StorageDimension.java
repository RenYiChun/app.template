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
 * 存储背压维度：限制全局存储条数，达限时阻塞生产线程直到有数据消费离库。
 * 对应配置：{@code flow.limits.global.storage-capacity}。
 * <p>
 * 使用 storagePermitPair（global + per-job），acquire 时占一个存储槽，
 * 数据从存储中消费或驱逐时通过 {@link #onBusinessRelease} 释放槽位。
 */
@Slf4j
public class StorageDimension implements ResourceBackpressureDimension {
    
    public static final String ID = "storage";
    
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
        PermitPair pair = ctx.getStoragePermitPair();
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
        try {
            if (!pair.tryAcquireBoth(permits)) {
                throw new TimeoutException("storage acquire failed for jobId=" + ctx.getJobId());
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
        PermitPair pair = ctx.getStoragePermitPair();
        if (pair != null) {
            pair.release(permits);
            Counter.builder(BackpressureMetricNames.DIM_RELEASE_COUNT)
                   .tag(BackpressureMetricNames.TAG_JOB_ID, ctx.getMetricJobIdForTags())
                   .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
                   .register(ctx.getMeterRegistry())
                   .increment(permits);
        }
    }
}
