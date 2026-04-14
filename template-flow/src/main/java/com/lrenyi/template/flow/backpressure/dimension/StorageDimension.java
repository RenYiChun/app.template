package com.lrenyi.template.flow.backpressure.dimension;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.backpressure.BackpressureMetricNames;
import com.lrenyi.template.flow.backpressure.BackpressureTimeoutException;
import com.lrenyi.template.flow.backpressure.DimensionContext;
import com.lrenyi.template.flow.backpressure.ResourceBackpressureDimension;
import com.lrenyi.template.flow.resource.PermitPair;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
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
        var fc = ctx.getFlowConfig();
        boolean metricsEnabled = fc != null
                && (fc.getLimits().getGlobal().getStorageCapacity() > 0
                || fc.getLimits().getPerJob().getStorageCapacity() > 0);
        MeterRegistry registry = ctx.getMeterRegistry();
        Tags metricTags = ctx.getDimensionMetricTags(ID);
        if (metricsEnabled) {
            recordAttempts(registry, metricTags, pair);
        }
        Timer.Sample sample = metricsEnabled ? Timer.start(registry) : null;
        PermitPair.AcquireResult result;
        try {
            boolean blockForever = fc == null || fc.getProducerBackpressureBlockingMode()
                    == TemplateConfigProperties.Flow.BackpressureBlockingMode.BLOCK_FOREVER;
            long timeoutMs = (fc != null) ? fc.getProducerBackpressureTimeoutMill() : 30_000L;
            if (blockForever) {
                result = pair.tryAcquireBothWithResult(permits);
            } else {
                result = pair.tryAcquireBothWithResult(permits, timeoutMs, TimeUnit.MILLISECONDS);
            }
        } finally {
            if (metricsEnabled && sample != null) {
                recordDuration(registry, metricTags, pair, sample);
            }
        }
        if (result != PermitPair.AcquireResult.SUCCESS) {
            if (metricsEnabled) {
                recordBlockedAndTimeout(registry, metricTags, result);
            }
            throw new BackpressureTimeoutException("storage acquire failed for jobId=" + ctx.getJobId(), result);
        }
    }

    private void recordAttempts(MeterRegistry registry, Tags metricTags, PermitPair pair) {
        if (pair.hasGlobal()) {
            Counter.builder(BackpressureMetricNames.DIM_ACQUIRE_ATTEMPTS_GLOBAL)
                   .tags(metricTags)
                   .register(registry)
                   .increment();
        }
        if (pair.hasPerJob()) {
            Counter.builder(BackpressureMetricNames.DIM_ACQUIRE_ATTEMPTS_PER_JOB)
                   .tags(metricTags)
                   .register(registry)
                   .increment();
        }
    }

    private void recordTimeout(MeterRegistry registry, Tags metricTags, PermitPair.AcquireResult result) {
        if (result == PermitPair.AcquireResult.FAILED_ON_GLOBAL) {
            Counter.builder(BackpressureMetricNames.DIM_ACQUIRE_TIMEOUT_GLOBAL)
                   .tags(metricTags)
                   .register(registry)
                   .increment();
        } else if (result == PermitPair.AcquireResult.FAILED_ON_PER_JOB) {
            Counter.builder(BackpressureMetricNames.DIM_ACQUIRE_TIMEOUT_PER_JOB)
                   .tags(metricTags)
                   .register(registry)
                   .increment();
        }
    }

    private void recordBlockedAndTimeout(MeterRegistry registry, Tags metricTags, PermitPair.AcquireResult result) {
        recordTimeout(registry, metricTags, result);
        String metricName = result == PermitPair.AcquireResult.FAILED_ON_GLOBAL
                ? BackpressureMetricNames.DIM_ACQUIRE_BLOCKED_GLOBAL
                : BackpressureMetricNames.DIM_ACQUIRE_BLOCKED_PER_JOB;
        Counter.builder(metricName)
               .tags(metricTags)
               .register(registry)
               .increment();
    }

    private void recordDuration(MeterRegistry registry, Tags metricTags, PermitPair pair, Timer.Sample sample) {
        if (pair.hasGlobal()) {
            Timer t = Timer.builder(BackpressureMetricNames.DIM_ACQUIRE_DURATION_GLOBAL)
                           .tags(metricTags)
                           .register(registry);
            long nanos = sample.stop(t);
            if (pair.hasPerJob()) {
                Timer.builder(BackpressureMetricNames.DIM_ACQUIRE_DURATION_PER_JOB)
                     .tags(metricTags)
                     .register(registry)
                     .record(nanos, TimeUnit.NANOSECONDS);
            }
        } else if (pair.hasPerJob()) {
            sample.stop(Timer.builder(BackpressureMetricNames.DIM_ACQUIRE_DURATION_PER_JOB)
                            .tags(metricTags)
                            .register(registry));
        }
    }

    private void recordRelease(MeterRegistry registry, Tags metricTags, PermitPair pair, int permits) {
        if (pair.hasGlobal()) {
            Counter.builder(BackpressureMetricNames.DIM_RELEASE_COUNT_GLOBAL)
                   .tags(metricTags)
                   .register(registry)
                   .increment(permits);
        }
        if (pair.hasPerJob()) {
            Counter.builder(BackpressureMetricNames.DIM_RELEASE_COUNT_PER_JOB)
                   .tags(metricTags)
                   .register(registry)
                   .increment(permits);
        }
    }
    @Override
    public void onBusinessRelease(DimensionContext ctx, int permits) {
        if (permits <= 0) {
            return;
        }
        PermitPair pair = ctx.getStoragePermitPair();
        if (pair != null) {
            // 防御性释放：使用 global limit 避免信号量超限
            var fc = ctx.getFlowConfig();
            int globalLimit = (fc != null && fc.getLimits().getGlobal().getStorageCapacity() > 0)
                    ? fc.getLimits().getGlobal().getStorageCapacity()
                    : Integer.MAX_VALUE;
            pair.release(permits, globalLimit);
            if (fc != null
                    && (fc.getLimits().getGlobal().getStorageCapacity() > 0
                    || fc.getLimits().getPerJob().getStorageCapacity() > 0)) {
                recordRelease(ctx.getMeterRegistry(), ctx.getDimensionMetricTags(ID), pair, permits);
            }
        }
    }
}
