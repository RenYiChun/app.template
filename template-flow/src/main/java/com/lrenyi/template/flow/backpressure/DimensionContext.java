package com.lrenyi.template.flow.backpressure;

import java.util.function.BooleanSupplier;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import com.lrenyi.template.flow.resource.PermitPair;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Builder;
import lombok.Getter;

/**
 * 维度上下文：封装单次 acquire 调用所需的所有资源引用与上下文信息。
 * 由 {@link BackpressureManager} 在每次 acquire 调用时构建，维度实现按需从中取用所需句柄。
 */
@Builder
@Getter
public final class DimensionContext {
    
    /** Job 标识 */
    private final String jobId;
    
    /** 当前路由的维度 ID */
    private final String dimensionId;
    
    /** 本次申请/释放的 permit 数量（通常为 1） */
    private final int permits;
    
    /** 停止检查：返回 true 时维度应提前退出等待 */
    private final BooleanSupplier stopCheck;
    
    /** Micrometer 指标注册表 */
    private final MeterRegistry meterRegistry;
    
    /** Flow 配置（含阻塞模式与超时设置） */
    private final TemplateConfigProperties.Flow flowConfig;
    
    /** 全局资源注册表（供 storage 维度调用 releaseGlobalStorage） */
    private final FlowResourceRegistry resourceRegistry;
    
    // ─── 各维度使用的资源句柄（不需要的为 null）─────────────────────────────────
    
    /** 在途生产许可对（in-flight-production 维度使用） */
    private final PermitPair inFlightPermitPair;
    
    /** 生产线程许可对（producer-concurrency 维度使用） */
    private final PermitPair producerPermitPair;
    
    /** 消费线程许可对（consumer-concurrency 维度使用） */
    private final PermitPair consumerPermitPair;
    
    /** 在途消费许可对（in-flight-consumer 维度使用） */
    private final PermitPair inFlightConsumerPermitPair;
    
    /** 存储许可对（storage 维度使用） */
    private final PermitPair storagePermitPair;
    
    /** 全局消费线程上限（consumer-concurrency 维度释放时使用） */
    private final int globalConsumerLimit;
}
