package com.lrenyi.template.core.flow.config;

public enum FlowStorageType {
    /**
     * 本地高性能缓存 (基于 Caffeine)
     */
    CAFFEINE,
    
    /**
     * 分布式缓存 (基于 Redis)
     */
    REDIS,
    
    QUEUE,
    
    /**
     * 无状态存储 (直接丢弃或仅透传)
     */
    NONE;
    
    public static FlowStorageType from(String value) {
        for (FlowStorageType type : values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        return CAFFEINE; // 默认值
    }
}