package com.lrenyi.template.flow.storage;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * 过期调度索引抽象。
 */
public interface ExpiryIndex<T extends Delayed> {

    void schedule(T token);

    T take() throws InterruptedException;

    /**
     * 带超时的获取，用于定期唤醒以检查关闭等状态。
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return 已到期的 token，超时则返回 null
     */
    T poll(long timeout, TimeUnit unit) throws InterruptedException;

    void clear();
}

