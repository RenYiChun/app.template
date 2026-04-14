package com.lrenyi.template.flow.context;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import com.lrenyi.template.flow.backpressure.DimensionLease;
import lombok.Getter;

@Getter
public class FlowEntry<T> implements AutoCloseable {
    private static final AtomicIntegerFieldUpdater<FlowEntry<?>> REF_UPDATER;
    private static final AtomicIntegerFieldUpdater<FlowEntry<?>> STATUS_UPDATER;
    private static final AtomicIntegerFieldUpdater<FlowEntry<?>> RETRY_REMAINING_UPDATER;
    private static final int BIT_LOGIC_CLAIMED = 1;
    
    static {
        try {
            @SuppressWarnings("unchecked") Class<FlowEntry<?>> clazz = (Class<FlowEntry<?>>) (Class<?>) FlowEntry.class;
            REF_UPDATER = AtomicIntegerFieldUpdater.newUpdater(clazz, "refCnt");
            STATUS_UPDATER = AtomicIntegerFieldUpdater.newUpdater(clazz, "status");
            RETRY_REMAINING_UPDATER = AtomicIntegerFieldUpdater.newUpdater(clazz, "retryRemaining");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    
    private final T data;
    private final String jobId;
    private volatile int refCnt = 1;
    private volatile int status = 0;
    private volatile int retryRemaining = -1;
    /** 存储槽位租约：entry 进入 storage 时持有，离库时通过 closeStorageLease() 释放。 */
    private volatile DimensionLease storageLease;
    
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
    
    /**
     * 初始化重试次数。仅当当前为 -1（未初始化）时生效。
     *
     * @param maxTimes 最大重试次数；-1 表示不重试，保持默认不变；>=0 时从 -1 设为该值
     */
    public void initRetryRemaining(int maxTimes) {
        if (maxTimes < 0) {
            return; // -1 表示不重试，保持默认 -1，不覆盖
        }
        RETRY_REMAINING_UPDATER.compareAndSet(this, -1, maxTimes);
    }
    
    public boolean tryConsumeOneRetry() {
        while (true) {
            int current = retryRemaining;
            if (current <= 0) {
                return false;
            }
            if (RETRY_REMAINING_UPDATER.compareAndSet(this, current, current - 1)) {
                return true;
            }
        }
    }
    
    /**
     * 将重入标志重置为 -1，使后续不再走重入流程。
     * 用于驱逐场景：当同槽位内有配对成功时，未匹配条目应直接走失败出口，避免重入耗尽。
     */
    public void resetRetryToIneligible() {
        RETRY_REMAINING_UPDATER.set(this, -1);
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
    
    /**
     * 绑定存储槽位租约。由 FlowLauncher 在 deposit 成功后调用，租约随 entry 在 slot 中存活。
     */
    public void setStorageLease(DimensionLease lease) {
        this.storageLease = lease;
    }

    /**
     * 释放存储槽位租约（幂等）。entry 离库时由存储层调用，触发 StorageDimension.onBusinessRelease。
     * 若租约已关闭或为 null，则为空操作。
     */
    public void closeStorageLease() {
        DimensionLease lease = storageLease;
        if (lease != null) {
            lease.close();
        }
    }

    @Override
    public void close() {
        // 仅仅是内存引用减一，不触发任何业务逻辑
        REF_UPDATER.decrementAndGet(this);
    }
}
