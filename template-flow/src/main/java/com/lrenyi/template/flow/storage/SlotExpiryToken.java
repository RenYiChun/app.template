package com.lrenyi.template.flow.storage;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.NonNull;

/**
 * slot 级过期调度 token，作为 DelayQueue 中的轻量对象。
 * 仅携带 slotId + nextCheckAt + version，禁止引用业务对象。
 */
public final class SlotExpiryToken implements Delayed {
    private final long slotId;
    private final long nextCheckAt;
    private final int version;
    
    SlotExpiryToken(long slotId, long nextCheckAt, int version) {
        this.slotId = slotId;
        this.nextCheckAt = nextCheckAt;
        this.version = version;
    }
    
    long slotId() {
        return slotId;
    }
    
    long nextCheckAt() {
        return nextCheckAt;
    }
    
    int version() {
        return version;
    }
    
    /**
     * 剩余延迟。已过期时返回 0 以便 DelayQueue 立即取出；上限 1 天避免转成 NANOSECONDS 溢出导致只唤醒一次或长时间阻塞。
     */
    @Override
    public long getDelay(TimeUnit unit) {
        long diff = nextCheckAt - System.currentTimeMillis();
        if (diff <= 0L) {
            return 0L;
        }
        long delayMs = Math.min(diff, TimeUnit.DAYS.toMillis(1L));
        return unit.convert(delayMs, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public int compareTo(@NonNull Delayed other) {
        if (this == other) {
            return 0;
        }
        if (other instanceof SlotExpiryToken o) {
            return Long.compare(this.nextCheckAt, o.nextCheckAt);
        }
        long diff = getDelay(TimeUnit.MILLISECONDS) - other.getDelay(TimeUnit.MILLISECONDS);
        return diff < 0 ? -1 : (diff > 0 ? 1 : 0);
    }
}

