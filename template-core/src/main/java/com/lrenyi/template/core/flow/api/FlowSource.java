package com.lrenyi.template.core.flow.api;

/**
 * 单子流数据源：按顺序产出 T，由引擎拉取并交给 Launcher。
 * 可包装 Stream、Iterator、分页 API、Kafka 等。
 */
public interface FlowSource<T> extends AutoCloseable {

    /**
     * 是否还有下一条数据。
     * 若底层阻塞（如 Kafka poll），可抛 InterruptedException。
     */
    boolean hasNext() throws InterruptedException;

    /**
     * 取下一条数据；若没有则抛 NoSuchElementException。
     * 调用前应保证 hasNext() 为 true。
     */
    T next();

    @Override
    void close();
}
