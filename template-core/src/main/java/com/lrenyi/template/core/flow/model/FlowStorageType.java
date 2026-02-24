package com.lrenyi.template.core.flow.model;

public enum FlowStorageType {
    /**
     * 本地高性能缓存 (基于 Caffeine)
     */
    CAFFEINE,
    
    QUEUE;
    
    public static FlowStorageType from(String value) {
        for (FlowStorageType type : values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        return CAFFEINE; // 默认值
    }
}