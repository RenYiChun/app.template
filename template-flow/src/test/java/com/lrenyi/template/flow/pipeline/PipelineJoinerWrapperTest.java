package com.lrenyi.template.flow.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.model.EgressReason;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link PipelineJoinerWrapper} 产出路径单测（不启动 FlowManager）。
 */
class PipelineJoinerWrapperTest {

    @Test
    void pair_legacy_appliesTransformerPerSide() {
        List<Integer> out = new CopyOnWriteArrayList<>();
        FlowJoiner<Integer> delegate = new StubJoiner();
        Function<Integer, List<Integer>> tf = i -> List.of(i * 10);
        PipelineStageDispatch<Integer, Integer> dispatch = PipelineDispatchFactories.create(delegate, tf, null);
        PipelineJoinerWrapper<Integer, Integer> w = new PipelineJoinerWrapper<>(delegate, dispatch);
        w.addDownstream(out::add);
        w.onPairConsumed(2, 3, "j");
        assertEquals(List.of(20, 30), out);
    }

    @Test
    void pair_pipelineStageOutput_takesPrecedenceOverExplicitPair() {
        List<Integer> out = new CopyOnWriteArrayList<>();
        FlowJoiner<Integer> delegate = new StubJoinerWithOutput();
        Function<Integer, List<Integer>> tf = i -> List.of(i * 10);
        BiFunction<Integer, Integer, List<Integer>> ignoredExplicit = (a, b) -> List.of(-1);
        PipelineStageDispatch<Integer, Integer> dispatch = PipelineDispatchFactories.create(delegate, tf, ignoredExplicit);
        PipelineJoinerWrapper<Integer, Integer> w = new PipelineJoinerWrapper<>(delegate, dispatch);
        w.addDownstream(out::add);
        w.onPairConsumed(2, 3, "j");
        assertEquals(List.of(5), out);
    }

    @Test
    void pair_builderPairOutput_usedWhenNoExplicitInterface() {
        List<Integer> out = new CopyOnWriteArrayList<>();
        FlowJoiner<Integer> delegate = new StubJoiner();
        Function<Integer, List<Integer>> tf = i -> List.of(i * 10);
        BiFunction<Integer, Integer, List<Integer>> pairOut = (a, b) -> List.of(a + b);
        PipelineStageDispatch<Integer, Integer> dispatch = PipelineDispatchFactories.create(delegate, tf, pairOut);
        PipelineJoinerWrapper<Integer, Integer> w = new PipelineJoinerWrapper<>(delegate, dispatch);
        w.addDownstream(out::add);
        w.onPairConsumed(2, 3, "j");
        assertEquals(List.of(5), out);
    }

    @Test
    void single_singleConsumed_usesTransformerWhenStageOutputReturnsNull() {
        List<Integer> out = new ArrayList<>();
        FlowJoiner<Integer> delegate = new StubJoinerWithOutput();
        Function<Integer, List<Integer>> tf = i -> List.of(i + 1);
        PipelineStageDispatch<Integer, Integer> dispatch = PipelineDispatchFactories.create(delegate, tf, null);
        PipelineJoinerWrapper<Integer, Integer> w = new PipelineJoinerWrapper<>(delegate, dispatch);
        w.addDownstream(out::add);
        w.onSingleConsumed(7, "j", EgressReason.SINGLE_CONSUMED);
        assertEquals(List.of(8), out);
    }

    @Test
    void single_nonSingleConsumed_noEmitWhenStageOutputReturnsNull() {
        List<Integer> out = new ArrayList<>();
        FlowJoiner<Integer> delegate = new StubJoinerWithOutput();
        Function<Integer, List<Integer>> tf = i -> List.of(i + 1);
        PipelineStageDispatch<Integer, Integer> dispatch = PipelineDispatchFactories.create(delegate, tf, null);
        PipelineJoinerWrapper<Integer, Integer> w = new PipelineJoinerWrapper<>(delegate, dispatch);
        w.addDownstream(out::add);
        w.onSingleConsumed(7, "j", EgressReason.TIMEOUT);
        assertEquals(List.of(), out);
    }

    private static class StubJoiner implements FlowJoiner<Integer> {
        @Override
        public Class<Integer> getDataType() {
            return Integer.class;
        }

        @Override
        public FlowSourceProvider<Integer> sourceProvider() {
            return null;
        }

        @Override
        public String joinKey(Integer item) {
            return String.valueOf(item);
        }

        @Override
        public void onPairConsumed(Integer existing, Integer incoming, String jobId) {
        }

        @Override
        public void onSingleConsumed(Integer item, String jobId, EgressReason reason) {
        }
    }

    private static class StubJoinerWithOutput extends StubJoiner implements PipelineStageOutput<Integer, Integer> {
        @Override
        public List<Integer> outputsAfterPair(Integer existing, Integer incoming) {
            return List.of(existing + incoming);
        }

        @Override
        public List<Integer> outputsAfterSingle(Integer item, EgressReason reason) {
            return null;
        }
    }
}
