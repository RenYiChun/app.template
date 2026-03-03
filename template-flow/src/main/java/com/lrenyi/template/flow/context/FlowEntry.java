package com.lrenyi.template.flow.context;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import lombok.Getter;

@Getter
public class FlowEntry<T> implements AutoCloseable {
    private static final AtomicIntegerFieldUpdater<FlowEntry<?>> REF_UPDATER;
    private static final AtomicIntegerFieldUpdater<FlowEntry<?>> STATUS_UPDATER;
    private static final int BIT_LOGIC_CLAIMED = 1;
    
    static {
        try {
            @SuppressWarnings("unchecked") Class<FlowEntry<?>> clazz = (Class<FlowEntry<?>>) (Class<?>) FlowEntry.class;
            REF_UPDATER = AtomicIntegerFieldUpdater.newUpdater(clazz, "refCnt");
            STATUS_UPDATER = AtomicIntegerFieldUpdater.newUpdater(clazz, "status");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    
    private final T data;
    private final String jobId;
    private volatile int refCnt = 1;
    private volatile int status = 0;
    /** 移除原因（如 EVICTION 的 EXPIRED/SIZE），供 Finalizer 在 claimLogic 失败时计数 EGRESS_PASSIVE 使用 */
    private volatile String removalReason;
    
    public FlowEntry(T data, String jobId) {
        this.data = data;
        this.jobId = jobId;
    }
    
    public void retain() {
        REF_UPDATER.incrementAndGet(this);
    }
    
    public void release() {
        REF_UPDATER.decrementAndGet(this);
    }
    
    /** 包级可见，供单元测试验证 refCnt */
    int getRefCntForTest() {
        return refCnt;
    }

    /** 设置移除原因（供 Caffeine removalListener 在驱逐时设置） */
    public void setRemovalReason(String reason) {
        this.removalReason = reason;
    }

    /** 获取移除原因，用于 EGRESS_PASSIVE 的 reason 标签 */
    public String getRemovalReason() {
        return removalReason;
    }
    
    /**
     * 抢占执行权：确保配对和驱逐只有一个能成功，
     * 只有抢占成功的线程，才有资格去 Orchestrator 申请和释放席位。
     */
    public boolean claimLogic() {
        while (true) {
            int current = status;
            if ((current & BIT_LOGIC_CLAIMED) != 0) {
                return false;
            }
            if (STATUS_UPDATER.compareAndSet(this, current, current | BIT_LOGIC_CLAIMED)) {
                return true;
            }
        }
    }
    
    @Override
    public void close() {
        // 仅仅是内存引用减一，不触发任何业务逻辑
        REF_UPDATER.decrementAndGet(this);
    }
}