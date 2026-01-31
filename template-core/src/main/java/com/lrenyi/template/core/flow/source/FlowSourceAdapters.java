package com.lrenyi.template.core.flow.source;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

/**
 * Source 适配器：将 JDK Stream / Iterator 转为 FlowSource 或 FlowSourceProvider。
 */
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
     * 返回无子流的空 Provider。推送专用 Joiner 的 sourceProvider() 可返回此结果，
     * 避免被误用于 run() 拉取模式。
     */
    public static <T> FlowSourceProvider<T> emptyProvider() {
        return new EmptyFlowSourceProvider<>();
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
