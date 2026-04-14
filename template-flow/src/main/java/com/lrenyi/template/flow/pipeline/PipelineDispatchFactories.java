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

    public static <I, O> PipelineStageDispatch<I, O> createSingleMap(
            FlowJoiner<I> joiner,
            Function<I, O> transformer) {
        if (joiner instanceof PipelineEmitter) {
            return new EmitterDispatch<>();
        }
        return new SingleMapDispatch<>(transformer);
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

    private static final class SingleMapDispatch<I, O> implements PipelineStageDispatch<I, O> {
        private final Function<I, O> transformer;

        private SingleMapDispatch(Function<I, O> transformer) {
            this.transformer = transformer;
        }

        @Override
        public void wireEmitter(FlowJoiner<I> delegate, Consumer<O> forwardDirect) {
        }

        @Override
        public void afterPairConsumed(I existing, I incoming, Consumer<List<O>> emitDownstream) {
            emitOne(transformer.apply(existing), emitDownstream);
            emitOne(transformer.apply(incoming), emitDownstream);
        }

        @Override
        public void afterSingleConsumed(I item, EgressReason reason, Consumer<List<O>> emitDownstream) {
            if (reason == EgressReason.SINGLE_CONSUMED) {
                emitOne(transformer.apply(item), emitDownstream);
            }
        }

        @Override
        public void afterPairConsumed(I existing,
                                      I incoming,
                                      Consumer<O> emitOneDownstream,
                                      Consumer<List<O>> emitBatchDownstream) {
            emitDirect(transformer.apply(existing), emitOneDownstream);
            emitDirect(transformer.apply(incoming), emitOneDownstream);
        }

        @Override
        public void afterSingleConsumed(I item,
                                        EgressReason reason,
                                        Consumer<O> emitOneDownstream,
                                        Consumer<List<O>> emitBatchDownstream) {
            if (reason == EgressReason.SINGLE_CONSUMED) {
                emitDirect(transformer.apply(item), emitOneDownstream);
            }
        }

        private void emitOne(O item, Consumer<List<O>> emitDownstream) {
            if (item != null) {
                emitDownstream.accept(List.of(item));
            }
        }

        private void emitDirect(O item, Consumer<O> emitOneDownstream) {
            if (item != null) {
                emitOneDownstream.accept(item);
            }
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
