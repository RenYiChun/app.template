package com.lrenyi.template.flow.exception;

/**
 * Flow 处理阶段枚举
 * 用于标识异常发生的阶段
 */
public enum FlowPhase {
    /**
     * 生产阶段：数据进入系统
     */
    PRODUCTION,
    
    /**
     * 存储阶段：数据存入缓存
     */
    STORAGE,
    
    /**
     * 消费阶段：数据处理和回调
     */
    CONSUMPTION,
    
    /**
     * 终结阶段：资源清理和释放
     */
    FINALIZATION,
    
    /**
     * 未知阶段
     */
    UNKNOWN
}
