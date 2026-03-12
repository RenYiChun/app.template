package com.lrenyi.template.flow.storage;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.TimeUnit;

/**
 * 基于 DelayQueue 的默认过期索引实现。
 */
final class DelayQueueExpiryIndex implements ExpiryIndex<SlotExpiryToken> {
    private final DelayQueue<SlotExpiryToken> queue = new DelayQueue<>();

    @Override
    public void schedule(SlotExpiryToken token) {
        if (token != null) {
            queue.offer(token);
        }
    }

    @Override
    public SlotExpiryToken take() throws InterruptedException {
        return queue.take();
    }

    @Override
    public SlotExpiryToken poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    @Override
    public void clear() {
        queue.clear();
    }
    
    DelayQueue<SlotExpiryToken> rawQueue() {
        return queue;
    }
}

