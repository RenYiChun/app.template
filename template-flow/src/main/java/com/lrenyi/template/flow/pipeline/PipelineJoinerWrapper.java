package com.lrenyi.template.flow.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import com.lrenyi.template.flow.api.EmbeddedBatchSpec;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.model.FlowStorageType;
import lombok.extern.slf4j.Slf4j;

/**
 * 管道各阶段 Joiner 的包装器。
 * 负责在原业务逻辑（delegate）执行后，将产出数据经过转换（transformer）后交给后续阶段（downstreams）。
 * 配对/单条/ Emitter 绑定由 {@link PipelineStageDispatch} 完成，在构建期由 {@link PipelineDispatchFactories} 装配。
 *
 * @param <I> 本阶段接收的输入数据类型
 * @param <O> 本阶段产出的输出数据类型
 */
@Slf4j
public class PipelineJoinerWrapper<I, O> implements FlowJoiner<I> {
    private final FlowJoiner<I> delegate;
    private final PipelineStageDispatch<I, O> dispatch;
    private final List<Consumer<O>> downstreams = new CopyOnWriteArrayList<>();
    private final EmbeddedBatchDownstream<O> embeddedBatch;

    public PipelineJoinerWrapper(FlowJoiner<I> delegate, PipelineStageDispatch<I, O> dispatch) {
        this(delegate, dispatch, null);
    }

    /**
     * @param embeddedBatch 非 null 时在本段出口对映射结果攒批后再下发，下游每次收到一条 {@link List}{@code <O>}（与独立 {@code aggregate} 段语义一致）
     */
    public PipelineJoinerWrapper(FlowJoiner<I> delegate,
                                 PipelineStageDispatch<I, O> dispatch,
                                 EmbeddedBatchSpec embeddedBatch) {
        this.delegate = delegate;
        this.dispatch = dispatch;
        this.embeddedBatch = embeddedBatch != null
                ? new EmbeddedBatchDownstream<>(embeddedBatch, this::pushBatchToDownstreams)
                : null;
        dispatch.wireEmitter(delegate, this::forwardOneOrBatch);
    }

    public void addDownstream(Consumer<O> nextLauncher) {
        this.downstreams.add(nextLauncher);
    }

    /**
     * 与 {@link AggregationJoiner#setScheduler} 相同：供时间窗触发 flush。
     */
    public void setSchedulerForEmbeddedBatch(ScheduledExecutorService scheduler) {
        if (embeddedBatch != null) {
            embeddedBatch.setScheduler(scheduler);
        }
    }

    /**
     * 本段 Launcher 结束时刷出残余批次，须在通知下游 {@code markSourceFinished} 之前调用。
     */
    public void flushEmbeddedBatchOnUpstreamComplete() {
        if (embeddedBatch != null) {
            embeddedBatch.flush();
        }
    }

    @Override
    public FlowStorageType getStorageType() {
        return delegate.getStorageType();
    }

    @Override
    public Class<I> getDataType() {
        return delegate.getDataType();
    }

    @Override
    public FlowSourceProvider<I> sourceProvider() {
        return delegate.sourceProvider();
    }

    @Override
    public String joinKey(I item) {
        return delegate.joinKey(item);
    }

    @Override
    public void onPairConsumed(I existing, I incoming, String jobId) {
        delegate.onPairConsumed(existing, incoming, jobId);
        if (downstreams.isEmpty()) {
            return;
        }
        dispatch.afterPairConsumed(existing, incoming, this::emitDownstreamList);
    }

    @Override
    public void onSingleConsumed(I item, String jobId, EgressReason reason) {
        delegate.onSingleConsumed(item, jobId, reason);
        dispatch.afterSingleConsumed(item, reason, this::emitDownstreamList);
    }

    private void forwardOneOrBatch(O out) {
        if (embeddedBatch != null) {
            embeddedBatch.add(out);
        } else {
            forwardDirectRaw(out);
        }
    }

    private void forwardDirectRaw(O out) {
        if (downstreams.isEmpty()) {
            return;
        }
        for (Consumer<O> downstream : downstreams) {
            downstream.accept(out);
        }
    }

    private void pushBatchToDownstreams(List<O> batch) {
        if (downstreams.isEmpty() || batch.isEmpty()) {
            return;
        }
        Object batchObj = batch;
        for (Consumer<O> downstream : downstreams) {
            @SuppressWarnings("unchecked")
            Consumer<Object> wide = (Consumer<Object>) (Object) downstream;
            wide.accept(batchObj);
        }
    }

    private void emitDownstreamList(List<O> outs) {
        if (outs == null || outs.isEmpty()) {
            return;
        }
        if (embeddedBatch != null) {
            embeddedBatch.addAll(outs);
        } else {
            log.debug("Forwarding batch from delegate={}, size={}, downstreams={}",
                    delegate.getClass().getSimpleName(), outs.size(), downstreams.size());
            for (O out : outs) {
                forwardDirectRaw(out);
            }
        }
    }

    @Override
    public boolean needMatched() {
        return delegate.needMatched();
    }

    @Override
    public boolean isMatched(I existing, I incoming) {
        return delegate.isMatched(existing, incoming);
    }

    @Override
    public boolean isRetryable(I item, String jobId) {
        return delegate.isRetryable(item, jobId);
    }

    /**
     * 与 {@link AggregationJoiner} 对齐的数量/时间双触发攒批，挂在本段映射出口。
     */
    private static final class EmbeddedBatchDownstream<O> {
        private final int batchSize;
        private final long timeout;
        private final TimeUnit unit;
        private final Consumer<List<O>> flushSink;
        private final List<O> buffer = new ArrayList<>();
        private final ReentrantLock lock = new ReentrantLock();
        private ScheduledExecutorService scheduler;
        private ScheduledFuture<?> timer;

        EmbeddedBatchDownstream(EmbeddedBatchSpec config, Consumer<List<O>> flushSink) {
            this.batchSize = config.batchSize();
            this.timeout = config.timeout();
            this.unit = config.unit();
            this.flushSink = flushSink;
        }

        void setScheduler(ScheduledExecutorService scheduler) {
            this.scheduler = scheduler;
        }

        void add(O o) {
            lock.lock();
            try {
                buffer.add(o);
                if (buffer.size() >= batchSize) {
                    flushLocked();
                } else if (timer == null && scheduler != null) {
                    timer = scheduler.schedule(this::flush, timeout, unit);
                }
            } finally {
                lock.unlock();
            }
        }

        void addAll(List<O> outs) {
            lock.lock();
            try {
                for (O o : outs) {
                    buffer.add(o);
                    if (buffer.size() >= batchSize) {
                        flushLocked();
                    }
                }
                if (!buffer.isEmpty() && timer == null && scheduler != null) {
                    timer = scheduler.schedule(this::flush, timeout, unit);
                }
            } finally {
                lock.unlock();
            }
        }

        void flush() {
            lock.lock();
            try {
                flushLocked();
            } finally {
                lock.unlock();
            }
        }

        private void flushLocked() {
            if (buffer.isEmpty()) {
                return;
            }
            List<O> batch = new ArrayList<>(buffer);
            buffer.clear();
            if (timer != null) {
                timer.cancel(false);
                timer = null;
            }
            flushSink.accept(batch);
        }
    }
}
