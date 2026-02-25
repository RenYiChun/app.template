package com.lrenyi.template.core.flow.api;

import java.util.Iterator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

/**
 * Source 适配器：将 JDK Stream / Iterator 转为 FlowSource 或 FlowSourceProvider。
 */
@Slf4j
public final class FlowSourceAdapters {

    private FlowSourceAdapters() {
    }

    /**
     * 将 Stream&lt;Stream&lt;T&gt;&gt; 转为 FlowSourceProvider&lt;T&gt;。
     * 业务若已有该形态的数据，可在 sourceProvider() 中返回此方法结果。
     * 调用方需在 try-with-resources 中使用返回的 Provider，以关闭外层 Stream。
     */
    public static <T> FlowSourceProvider<T> fromStreams(Stream<Stream<T>> parentStream) {
        return new StreamFlowSourceProvider<>(parentStream);
    }

    /**
     * 将 Iterator&lt;T&gt; 转为 FlowSource&lt;T&gt;（单流）。
     * 单流场景下可构造「只有一个子流」的 Provider，其 nextSubSource() 返回 fromIterator(...)。
     *
     * @param iterator 数据迭代器
     * @param onClose  关闭时的回调，可为 null
     */
    public static <T> FlowSource<T> fromIterator(Iterator<T> iterator, Runnable onClose) {
        return new IteratorFlowSource<>(iterator, onClose);
    }

    /**
     * 将单个 FlowSource 包装为「仅含一个子流」的 Provider，供引擎单流 run 重载使用。
     * hasNextSubSource() 仅第一次为 true，nextSubSource() 返回该 singleSource；
     * 子流生命周期由引擎 try-with-resources 关闭，本 Provider 的 close() 为 no-op。
     *
     * @param singleSource 单条数据流，非 null
     */
    public static <T> FlowSourceProvider<T> singleSourceProvider(FlowSource<T> singleSource) {
        return new SingleFlowSourceProvider<>(singleSource);
    }

    /**
     * 返回无子流的空 Provider。推送专用 Joiner 的 sourceProvider() 可返回此结果，
     * 避免被误用于 run() 拉取模式。
     */
    public static <T> FlowSourceProvider<T> emptyProvider() {
        return new EmptyFlowSourceProvider<>();
    }

    /**
     * 由一组已构造的 {@link FlowSource} 组成 Provider，按顺序提供子流。
     * 适用于 Kafka/NATS 等多子流场景：先为每个 consumer/subscription 建一个 FlowSource，
     * 再传入本方法得到 Provider；close() 时会关闭列表中所有 FlowSource（已交给引擎的会重复 close，通常为 no-op）。
     *
     * @param sources 子流列表，非 null 非空
     */
    public static <T> FlowSourceProvider<T> fromFlowSources(List<FlowSource<T>> sources) {
        return new ListFlowSourceProvider<>(sources);
    }

    private static final class ListFlowSourceProvider<T> implements FlowSourceProvider<T> {
        private final List<FlowSource<T>> sources;
        private int index;
        private boolean closed;

        ListFlowSourceProvider(List<FlowSource<T>> sources) {
            if (sources == null || sources.isEmpty()) {
                throw new IllegalArgumentException("sources 非空");
            }
            this.sources = List.copyOf(sources);
        }

        @Override
        public boolean hasNextSubSource() throws InterruptedException {
            if (closed) {
                return false;
            }
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            return index < sources.size();
        }

        @Override
        public FlowSource<T> nextSubSource() {
            if (closed) {
                throw new NoSuchElementException();
            }
            if (index >= sources.size()) {
                throw new NoSuchElementException();
            }
            return sources.get(index++);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            for (FlowSource<T> s : sources) {
                try {
                    s.close();
                } catch (Exception e) {
                    log.debug("FlowSource close failed, ignoring for best-effort release", e);
                }
            }
        }
    }

    private static final class SingleFlowSourceProvider<T> implements FlowSourceProvider<T> {
        private final FlowSource<T> singleSource;
        private boolean consumed;

        SingleFlowSourceProvider(FlowSource<T> singleSource) {
            this.singleSource = singleSource;
        }

        @Override
        public boolean hasNextSubSource() throws InterruptedException {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            return !consumed;
        }

        @Override
        public FlowSource<T> nextSubSource() {
            if (consumed) {
                throw new NoSuchElementException();
            }
            consumed = true;
            return singleSource;
        }

        @Override
        public void close() {
            // 子流由引擎 try(sub) 关闭，此处不关闭避免重复 close
        }
    }

    private static final class EmptyFlowSourceProvider<T> implements FlowSourceProvider<T> {

        @Override
        public boolean hasNextSubSource() {
            return false;
        }

        @Override
        public FlowSource<T> nextSubSource() {
            throw new NoSuchElementException();
        }

        @Override
        public void close() {
            // 空 Provider 无资源持有，关闭时无需释放，故空实现。
        }
    }

    private static final class StreamFlowSource<T> implements FlowSource<T> {
        private final Stream<T> stream;
        private final Iterator<T> iterator;

        StreamFlowSource(Stream<T> stream) {
            this.stream = stream;
            this.iterator = stream.iterator();
        }

        @Override
        public boolean hasNext() throws InterruptedException {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            return iterator.hasNext();
        }

        @Override
        public T next() {
            if (!iterator.hasNext()) {
                throw new NoSuchElementException();
            }
            return iterator.next();
        }

        @Override
        public void close() {
            stream.close();
        }
    }

    private static final class StreamFlowSourceProvider<T> implements FlowSourceProvider<T> {
        private final Stream<Stream<T>> parentStream;
        private final Iterator<Stream<T>> subSourceIterator;

        StreamFlowSourceProvider(Stream<Stream<T>> parentStream) {
            this.parentStream = parentStream;
            this.subSourceIterator = parentStream.iterator();
        }

        @Override
        public boolean hasNextSubSource() throws InterruptedException {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            return subSourceIterator.hasNext();
        }

        @Override
        public FlowSource<T> nextSubSource() {
            if (!subSourceIterator.hasNext()) {
                throw new NoSuchElementException();
            }
            return new StreamFlowSource<>(subSourceIterator.next());
        }

        @Override
        public void close() {
            parentStream.close();
        }
    }

    private record IteratorFlowSource<T>(Iterator<T> iterator, Runnable onClose) implements FlowSource<T> {

        @Override
        public boolean hasNext() throws InterruptedException {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            return iterator.hasNext();
        }

        @Override
        public T next() {
            if (!iterator.hasNext()) {
                throw new NoSuchElementException();
            }
            return iterator.next();
        }

        @Override
        public void close() {
            if (onClose != null) {
                onClose.run();
            }
        }
    }
}
