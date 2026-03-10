package com.lrenyi.template.flow.model;

public enum FlowStorageType {
    /**
     * 本地受控超时存储（BoundedTimedFlowStorage）
     */
    LOCAL_BOUNDED,
    
    QUEUE;
    
    public static FlowStorageType from(String value) {
        for (FlowStorageType type : values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        return LOCAL_BOUNDED; // 默认值
    }
}