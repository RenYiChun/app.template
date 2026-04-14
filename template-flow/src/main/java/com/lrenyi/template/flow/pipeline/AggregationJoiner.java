package com.lrenyi.template.flow.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.model.FlowStorageType;

/**
 * 聚合 Joiner。用于将多个单一数据项（T）攒批为列表（List&lt;T&gt;）。
 * 支持基于数量（batchSize）和时间（timeout）的双重触发机制。
 * <p>内部创建的线性/聚合阶段默认统一使用 {@link FlowStorageType#QUEUE}，
 * 保证数据尽快离开 stage storage 进入消费侧，而不是停留在本地有界缓存中等待驱逐。</p>
 *
 * @param <T> 待聚合的原始数据类型
 */
public class AggregationJoiner<T> implements FlowJoiner<T>, PipelineEmitter<List<T>> {
    private final Class<T> dataType;
    private final int batchSize;
    private final long timeout;
    private final TimeUnit unit;
    private final List<T> buffer = new ArrayList<>();
    /**
     * 每条入站分配唯一存储键，避免引擎按固定 key 将多条数据配对/顶掉，导致无法逐条进入攒批缓冲。
     */
    private final AtomicLong ingressSequence = new AtomicLong();
    private final ReentrantLock lock = new ReentrantLock();
    private Consumer<List<T>> downstream;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> timer;

    public AggregationJoiner(Class<T> dataType, int batchSize, long timeout, TimeUnit unit) {
        this.dataType = dataType;
        this.batchSize = batchSize;
        this.timeout = timeout;
        this.unit = unit;
    }

    public void setScheduler(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public void setDownstream(Consumer<List<T>> downstream) {
        this.downstream = downstream;
    }

    @Override
    public FlowStorageType getStorageType() {
        return FlowStorageType.QUEUE;
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
        return "AGG:" + ingressSequence.incrementAndGet();
    }

    @Override
    public void onPairConsumed(T existing, T incoming, String jobId) {
    }

    @Override
    public void onSingleConsumed(T item, String jobId, EgressReason reason) {
        if (reason != EgressReason.SINGLE_CONSUMED) {
            return;
        }
        lock.lock();
        try {
            buffer.add(item);
            if (buffer.size() >= batchSize) {
                flush();
            } else if (timer == null && scheduler != null) {
                timer = scheduler.schedule(this::flush, timeout, unit);
            }
        } finally {
            lock.unlock();
        }
    }

    private void flush() {
        lock.lock();
        try {
            if (buffer.isEmpty()) {
                return;
            }
            List<T> batch = new ArrayList<>(buffer);
            buffer.clear();
            if (timer != null) {
                timer.cancel(false);
                timer = null;
            }
            if (downstream != null) {
                downstream.accept(batch);
            }
        } finally {
            lock.unlock();
        }
    }
}
