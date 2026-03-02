package com.lrenyi.template.flow.api;

/**
 * 多子流提供者：产出多个 FlowSource&lt;T&gt;，每个对应一个并发单元。
 * 由引擎按 jobProducerLimit 并发消费。
 */
public interface FlowSourceProvider<T> extends AutoCloseable {
    
    /**
     * 是否还有下一个子流。
     */
    boolean hasNextSubSource() throws InterruptedException;
    
    /**
     * 取下一个子流；调用前应保证 hasNextSubSource() 为 true。
     */
    FlowSource<T> nextSubSource();
    
    @Override
    void close();
}
