package com.lrenyi.template.flow.pipeline;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
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

    public PipelineJoinerWrapper(FlowJoiner<I> delegate,
                                 PipelineStageDispatch<I, O> dispatch) {
        this.delegate = delegate;
        this.dispatch = dispatch;
        dispatch.wireEmitter(delegate, this::forwardDirect);
    }

    public void addDownstream(Consumer<O> nextLauncher) {
        this.downstreams.add(nextLauncher);
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

    private void forwardDirect(O out) {
        if (downstreams.isEmpty()) {
            return;
        }
        for (Consumer<O> downstream : downstreams) {
            downstream.accept(out);
        }
    }

    private void emitDownstreamList(List<O> outs) {
        if (outs == null || outs.isEmpty()) {
            return;
        }
        log.info("Forwarding batch from delegate={}, size={}, downstreams={}",
                delegate.getClass().getSimpleName(), outs.size(), downstreams.size());
        for (O out : outs) {
            forwardDirect(out);
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
}
