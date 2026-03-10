package com.lrenyi.template.flow.storage;

import java.util.concurrent.Delayed;

/**
 * 过期调度索引抽象。
 */
public interface ExpiryIndex<T extends Delayed> {

    void schedule(T token);

    T take() throws InterruptedException;

    void clear();
}

