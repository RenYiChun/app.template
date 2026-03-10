package com.lrenyi.template.flow.backpressure.dimension;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import com.lrenyi.template.flow.backpressure.BackpressureMetricNames;
import com.lrenyi.template.flow.backpressure.DimensionContext;
import com.lrenyi.template.flow.backpressure.ResourceBackpressureDimension;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * 存储背压维度：限制全局存储条数，达限时阻塞生产线程直到有数据消费离库。
 * 对应配置：{@code flow.limits.global.storage-capacity}。
 * <p>
 * 该维度使用全局存储信号量（globalStorageSemaphore），acquire 时占一个存储槽，
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
    public void acquire(DimensionContext ctx) throws InterruptedException, TimeoutException {
        Semaphore globalStorage = ctx.getGlobalStorageSemaphore();
        if (globalStorage == null) {
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
        try {
            globalStorage.acquire(1);
        } finally {
            sample.stop(Timer.builder(BackpressureMetricNames.DIM_ACQUIRE_DURATION)
                             .tag(BackpressureMetricNames.TAG_JOB_ID, jobId)
                             .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
                             .register(registry));
        }
    }
    
    @Override
    public void onBusinessRelease(DimensionContext ctx) {
        if (ctx.getResourceRegistry() != null) {
            ctx.getResourceRegistry().releaseGlobalStorage(1);
            Counter.builder(BackpressureMetricNames.DIM_RELEASE_COUNT)
                   .tag(BackpressureMetricNames.TAG_JOB_ID, ctx.getJobId())
                   .tag(BackpressureMetricNames.TAG_DIMENSION_ID, ID)
                   .register(ctx.getMeterRegistry())
                   .increment();
        }
    }
}
