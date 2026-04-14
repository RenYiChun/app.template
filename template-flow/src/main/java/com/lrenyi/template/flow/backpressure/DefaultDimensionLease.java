package com.lrenyi.template.flow.backpressure;

import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * DimensionLease 默认实现：
 * <ul>
 *   <li>原子状态保证 close() 幂等</li>
 *   <li>注册 JVM Cleaner 兜底检测未关闭的泄露 lease</li>
 * </ul>
 */
@Slf4j
final class DefaultDimensionLease implements DimensionLease {
    
    private static final Cleaner CLEANER = Cleaner.create();
    
    private final String leaseId;
    private final String dimensionId;
    private final int permits;
    private final ResourceBackpressureDimension dimension;
    private final DimensionContext ctx;
    private final BackpressureManager manager;
    private final AtomicBoolean released = new AtomicBoolean(false);
    private final Cleaner.Cleanable cleanable;
    
    DefaultDimensionLease(String leaseId,
            String dimensionId,
            int permits,
            ResourceBackpressureDimension dimension,
            DimensionContext ctx,
            BackpressureManager manager) {
        this.leaseId = leaseId;
        this.dimensionId = dimensionId;
        this.permits = permits;
        this.dimension = dimension;
        this.ctx = ctx;
        this.manager = manager;
        this.cleanable = CLEANER.register(this,
                new LeakGuard(leaseId, dimensionId, permits, released, dimension, ctx, manager));
    }
    
    @Override
    public String dimensionId() {
        return dimensionId;
    }
    
    @Override
    public String getLeaseId() {
        return leaseId;
    }
    
    @Override
    public int permits() {
        return permits;
    }
    
    @Override
    public void close() {
        if (released.compareAndSet(false, true)) {
            // Deregister Cleaner first: prevents leak alert for a properly closed lease
            cleanable.clean();
            try {
                dimension.onBusinessRelease(ctx, permits);
            } catch (Exception e) {
                log.error("Dimension onBusinessRelease failed, leaseId={}, dimensionId={}", leaseId, dimensionId, e);
            }
            manager.onLeaseClose(leaseId, dimensionId, false);
        } else {
            manager.onLeaseClose(leaseId, dimensionId, true);
        }
    }
    
    /**
     * Cleaner action: MUST NOT hold a strong reference to the outer DefaultDimensionLease.
     * Runs if the lease is GC'd without being closed (leak detection).
     */
    public static final class LeakGuard implements Runnable {
        private final String leaseId;
        private final String dimensionId;
        private final int permits;
        private final AtomicBoolean released;
        private final ResourceBackpressureDimension dimension;
        private final DimensionContext ctx;
        private final BackpressureManager manager;
        
        LeakGuard(String leaseId,
                String dimensionId,
                int permits,
                AtomicBoolean released,
                ResourceBackpressureDimension dimension,
                DimensionContext ctx,
                BackpressureManager manager) {
            this.leaseId = leaseId;
            this.dimensionId = dimensionId;
            this.permits = permits;
            this.released = released;
            this.dimension = dimension;
            this.ctx = ctx;
            this.manager = manager;
        }
        
        @Override
        public void run() {
            if (released.get()) {
                // close() already called; Cleaner was invoked via cleanable.clean() — no action needed
                return;
            }
            // Lease was GC'd without close() → resource leak
            manager.onLeakDetected(leaseId, dimensionId);
            try {
                dimension.onBusinessRelease(ctx, permits);
            } catch (Exception e) {
                log.error("Leak recovery release failed, leaseId={}, dimensionId={}", leaseId, dimensionId, e);
            }
        }
    }
}
