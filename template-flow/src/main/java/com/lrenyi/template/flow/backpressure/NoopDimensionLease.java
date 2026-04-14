package com.lrenyi.template.flow.backpressure;

/** 无操作租约，close() 为空操作，用于维度未注册或无需占位的情况。 */
record NoopDimensionLease(String dimensionId) implements DimensionLease {
    
    @Override
    public String getLeaseId() {
        return "noop";
    }
    
    @Override
    public int permits() {
        return 0;
    }
    
    @Override
    public void close() {
        // no-op
    }
}
