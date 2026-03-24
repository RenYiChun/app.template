package com.lrenyi.template.flow.pipeline;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.executor.ExecutorAcquireTimeoutException;
import com.lrenyi.template.flow.internal.FlowLauncher;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.model.FlowStorageType;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * 终端 Joiner。作为管道的最后一个阶段，执行最终的业务落库或收尾逻辑。
 * <p>存储类型说明同 {@link MapOperatorJoiner}（线性阶段与 {@link FlowStorageType#QUEUE} 的取舍）。</p>
 * <p>当 {@code limits.global.sink-consumer-threads &gt; 0} 时，在调用用户回调前获取全主机共享的 Sink 并发许可，
 * 阻塞策略与超时复用 {@link TemplateConfigProperties.Flow#getConsumerAcquireBlockingMode()} 与
 * {@link TemplateConfigProperties.Flow#getConsumerAcquireTimeoutMill()}。</p>
 *
 * @param <T> 到达终端的数据类型
 */
public class SinkJoiner<T> implements FlowJoiner<T> {
    private final Class<T> dataType;
    private final BiConsumer<T, String> onSink;
    private final FlowManager flowManager;
    /**
     * 终端阶段不应因固定 joinKey 导致多条到达数据在同一槽位上被配对/顶掉；
     * 每条入站分配唯一键，保证按条进入 SINGLE_CONSUMED 并触发 onSink。
     */
    private final AtomicLong sinkSequence = new AtomicLong();

    public SinkJoiner(Class<T> dataType, BiConsumer<T, String> onSink) {
        this(dataType, onSink, null);
    }

    /**
     * @param flowManager 非 null 时启用全局 Sink 并发（由 {@link FlowResourceRegistry#getGlobalSinkSemaphore()} 提供）
     */
    public SinkJoiner(Class<T> dataType, BiConsumer<T, String> onSink, FlowManager flowManager) {
        this.dataType = dataType;
        this.onSink = onSink;
        this.flowManager = flowManager;
    }

    @Override
    public FlowStorageType getStorageType() {
        return FlowStorageType.LOCAL_BOUNDED;
    }

    @Override
    public Class<T> getDataType() {
        return dataType;
    }

    @Override
    public FlowSourceProvider<T> sourceProvider() {
        return null;
    }

    @Override
    public String joinKey(T item) {
        return "SINK:" + sinkSequence.incrementAndGet();
    }

    @Override
    public void onPairConsumed(T existing, T incoming, String jobId) {
    }

    @Override
    public void onSingleConsumed(T item, String jobId, EgressReason reason) {
        if (reason == EgressReason.SINGLE_CONSUMED || reason == EgressReason.PAIR_MATCHED) {
            invokeOnSink(item, jobId);
        }
    }

    private void invokeOnSink(T item, String jobId) {
        if (flowManager == null) {
            onSink.accept(item, jobId);
            return;
        }
        FlowResourceRegistry registry = flowManager.getResourceRegistry();
        Semaphore sem = registry.getGlobalSinkSemaphore();
        if (sem == null) {
            onSink.accept(item, jobId);
            return;
        }
        TemplateConfigProperties.Flow flow = resolveFlow(jobId);
        MeterRegistry meterRegistry = flowManager.getMeterRegistry();
        long startNanos = System.nanoTime();
        boolean acquired = false;
        try {
            boolean blockForever = flow.getConsumerAcquireBlockingMode()
                    == TemplateConfigProperties.Flow.BackpressureBlockingMode.BLOCK_FOREVER;
            long timeoutMs = flow.getConsumerAcquireTimeoutMill();
            if (blockForever) {
                sem.acquire();
                acquired = true;
            } else {
                acquired = sem.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
                if (!acquired) {
                    Counter.builder(FlowMetricNames.SINK_CONCURRENCY_ACQUIRE_TIMEOUT)
                           .description("Sink 全局并发许可获取超时次数")
                           .register(meterRegistry)
                           .increment();
                    throw new ExecutorAcquireTimeoutException(
                            new TimeoutException("sink global concurrency acquire timeout, jobId=" + jobId));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("sink global concurrency acquire interrupted, jobId=" + jobId, e);
        } finally {
            long waitedNanos = System.nanoTime() - startNanos;
            Timer.builder(FlowMetricNames.SINK_CONCURRENCY_WAIT_DURATION)
                 .description("等待 Sink 全局并发许可耗时")
                 .register(meterRegistry)
                 .record(waitedNanos, TimeUnit.NANOSECONDS);
        }
        try {
            onSink.accept(item, jobId);
        } finally {
            if (acquired) {
                sem.release();
            }
        }
    }

    private TemplateConfigProperties.Flow resolveFlow(String jobId) {
        FlowLauncher<?> launcher = flowManager.getActiveLauncher(jobId);
        if (launcher != null) {
            return launcher.getFlow();
        }
        return flowManager.getGlobalConfig();
    }
}
