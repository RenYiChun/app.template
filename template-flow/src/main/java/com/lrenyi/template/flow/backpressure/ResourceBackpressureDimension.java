package com.lrenyi.template.flow.backpressure;

import java.util.concurrent.TimeoutException;

/**
 * 背压维度 SPI 接口：每个实现独立封装资源申请、背压判断与指标记录。
 * <p>
 * 同一维度 ID 如有多个实现，{@link com.lrenyi.template.flow.backpressure.BackpressureManager}
 * 只执行 {@link #order()} 最小的实现。
 * <p>
 * 通过 Java SPI 注册：在
 * {@code META-INF/services/com.lrenyi.template.flow.backpressure.ResourceBackpressureDimension}
 * 中列出实现类全限定名。
 */
public interface ResourceBackpressureDimension {
    
    /** 维度唯一标识，如 {@code "storage"}、{@code "in-flight-production"}。 */
    String id();
    
    /** 优先级：同 ID 多个实现时，选 order 最小者执行。 */
    int order();
    
    /**
     * 申请资源：内部完成占位、背压等待和指标记录。
     * 申请失败（含超时、中断）时必须已回滚已占资源。
     *
     * @param ctx    封装 Job 资源句柄与本次调用上下文
     * @param permits 申请数量（通常为 1，消费配对等场景可为 2 或更多）
     * @throws InterruptedException 等待期间线程被中断
     * @throws TimeoutException     超过配置超时时间
     */
    void acquire(DimensionContext ctx, int permits) throws InterruptedException, TimeoutException;
    
    /**
     * 释放资源：由框架在 lease 关闭时回调，完成资源释放和指标记录。
     *
     * @param ctx    与 acquire 时相同的上下文
     * @param permits 释放数量（与 acquire 时一致）
     */
    void onBusinessRelease(DimensionContext ctx, int permits);
}
