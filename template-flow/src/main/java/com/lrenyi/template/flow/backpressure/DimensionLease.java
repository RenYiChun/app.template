package com.lrenyi.template.flow.backpressure;

/**
 * 背压资源租约：{@link BackpressureManager#acquire} 成功后返回，
 * {@link #close()} 触发幂等资源释放。
 */
public interface DimensionLease extends AutoCloseable {
    
    /** 维度 ID */
    String dimensionId();
    
    /** 全局唯一租约 ID（用于 activeLeases 注册表跟踪与泄露检测） */
    String getLeaseId();
    
    /**
     * 释放资源；幂等，多次调用安全（首次后计入 idempotent_hit 指标）。
     * 覆盖 {@link AutoCloseable#close()} 以去掉受检异常，方便在 finally 中无条件调用。
     */
    @Override
    void close();
    
    /** 返回 noop 租约（无需占位时使用，close() 为空操作）。 */
    static DimensionLease noop(String dimensionId) {
        return new NoopDimensionLease(dimensionId);
    }
}
