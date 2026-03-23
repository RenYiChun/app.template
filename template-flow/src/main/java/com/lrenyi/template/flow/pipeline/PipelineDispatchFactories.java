package com.lrenyi.template.flow.pipeline;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.model.EgressReason;

/**
 * 在 {@link com.lrenyi.template.flow.pipeline.FlowPipelineBuilderImpl} 中根据 Joiner 类型装配 {@link PipelineStageDispatch}，
 * unchecked 与 {@code instanceof} 均收口在本类及内部实现，不进入 {@link PipelineJoinerWrapper}。
 */
public final class PipelineDispatchFactories {

    private PipelineDispatchFactories() {
    }

    /**
     * @param transformer        已解析为非 null（含 identity {@code t -> List.of(t)}）
     * @param explicitPairOutput Builder 四参数重载注入的配对产出，可为 null
     */
    @SuppressWarnings("unchecked")
    public static <I, O> PipelineStageDispatch<I, O> create(
            FlowJoiner<I> joiner,
            Function<I, List<O>> transformer,
            BiFunction<I, I, List<O>> explicitPairOutput) {
        if (joiner instanceof PipelineEmitter) {
            return new EmitterDispatch<>();
        }
        if (joiner instanceof PipelineStageOutput) {
            return new PsoDispatch<>((PipelineStageOutput<I, O>) joiner, transformer, explicitPairOutput);
        }
        if (explicitPairOutput != null) {
            return new ExplicitPairDispatch<>(transformer, explicitPairOutput);
        }
        return new LegacyDispatch<>(transformer);
    }

    private static final class EmitterDispatch<I, O> implements PipelineStageDispatch<I, O> {
        @Override
        public void wireEmitter(FlowJoiner<I> delegate, Consumer<O> forwardDirect) {
            if (delegate instanceof PipelineEmitter) {
                ((PipelineEmitter<O>) delegate).setDownstream(forwardDirect);
            }
        }

        @Override
        public void afterPairConsumed(I existing, I incoming, Consumer<List<O>> emitDownstream) {
        }

        @Override
        public void afterSingleConsumed(I item, EgressReason reason, Consumer<List<O>> emitDownstream) {
        }
    }

    private static final class LegacyDispatch<I, O> implements PipelineStageDispatch<I, O> {
        private final Function<I, List<O>> transformer;

        LegacyDispatch(Function<I, List<O>> transformer) {
            this.transformer = transformer;
        }

        @Override
        public void wireEmitter(FlowJoiner<I> delegate, Consumer<O> forwardDirect) {
        }

        @Override
        public void afterPairConsumed(I existing, I incoming, Consumer<List<O>> emitDownstream) {
            emitOnce(transformer.apply(existing), emitDownstream);
            emitOnce(transformer.apply(incoming), emitDownstream);
        }

        @Override
        public void afterSingleConsumed(I item, EgressReason reason, Consumer<List<O>> emitDownstream) {
            if (reason == EgressReason.SINGLE_CONSUMED) {
                emitOnce(transformer.apply(item), emitDownstream);
            }
        }

        private void emitOnce(List<O> batch, Consumer<List<O>> emitDownstream) {
            if (batch == null || batch.isEmpty()) {
                return;
            }
            emitDownstream.accept(batch);
        }
    }

    private static final class ExplicitPairDispatch<I, O> implements PipelineStageDispatch<I, O> {
        private final Function<I, List<O>> transformer;
        private final BiFunction<I, I, List<O>> explicitPairOutput;

        ExplicitPairDispatch(Function<I, List<O>> transformer, BiFunction<I, I, List<O>> explicitPairOutput) {
            this.transformer = transformer;
            this.explicitPairOutput = explicitPairOutput;
        }

        @Override
        public void wireEmitter(FlowJoiner<I> delegate, Consumer<O> forwardDirect) {
        }

        @Override
        public void afterPairConsumed(I existing, I incoming, Consumer<List<O>> emitDownstream) {
            List<O> built = explicitPairOutput.apply(existing, incoming);
            if (built != null) {
                if (!built.isEmpty()) {
                    emitDownstream.accept(built);
                }
                return;
            }
            emitIfNonEmpty(transformer.apply(existing), emitDownstream);
            emitIfNonEmpty(transformer.apply(incoming), emitDownstream);
        }

        @Override
        public void afterSingleConsumed(I item, EgressReason reason, Consumer<List<O>> emitDownstream) {
            if (reason == EgressReason.SINGLE_CONSUMED) {
                emitIfNonEmpty(transformer.apply(item), emitDownstream);
            }
        }

        private static <O> void emitIfNonEmpty(List<O> batch, Consumer<List<O>> emitDownstream) {
            if (batch != null && !batch.isEmpty()) {
                emitDownstream.accept(batch);
            }
        }
    }

    private static final class PsoDispatch<I, O> implements PipelineStageDispatch<I, O> {
        private final PipelineStageOutput<I, O> pso;
        private final Function<I, List<O>> transformer;
        private final BiFunction<I, I, List<O>> explicitPairOutput;

        PsoDispatch(PipelineStageOutput<I, O> pso,
                    Function<I, List<O>> transformer,
                    BiFunction<I, I, List<O>> explicitPairOutput) {
            this.pso = pso;
            this.transformer = transformer;
            this.explicitPairOutput = explicitPairOutput;
        }

        @Override
        public void wireEmitter(FlowJoiner<I> delegate, Consumer<O> forwardDirect) {
            if (delegate instanceof PipelineEmitter) {
                ((PipelineEmitter<O>) delegate).setDownstream(forwardDirect);
            }
        }

        @Override
        public void afterPairConsumed(I existing, I incoming, Consumer<List<O>> emitDownstream) {
            List<O> explicit = pso.outputsAfterPair(existing, incoming);
            if (explicit != null) {
                if (!explicit.isEmpty()) {
                    emitDownstream.accept(explicit);
                }
                return;
            }
            if (explicitPairOutput != null) {
                List<O> built = explicitPairOutput.apply(existing, incoming);
                if (built != null) {
                    if (!built.isEmpty()) {
                        emitDownstream.accept(built);
                    }
                    return;
                }
            }
            emitIfNonEmpty(transformer.apply(existing), emitDownstream);
            emitIfNonEmpty(transformer.apply(incoming), emitDownstream);
        }

        @Override
        public void afterSingleConsumed(I item, EgressReason reason, Consumer<List<O>> emitDownstream) {
            List<O> explicit = pso.outputsAfterSingle(item, reason);
            if (explicit != null) {
                if (!explicit.isEmpty()) {
                    emitDownstream.accept(explicit);
                }
                return;
            }
            if (reason == EgressReason.SINGLE_CONSUMED) {
                emitIfNonEmpty(transformer.apply(item), emitDownstream);
            }
        }

        private static <O> void emitIfNonEmpty(List<O> batch, Consumer<List<O>> emitDownstream) {
            if (batch != null && !batch.isEmpty()) {
                emitDownstream.accept(batch);
            }
        }
    }
}
