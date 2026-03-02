package com.lrenyi.template.dataforge.support;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 过滤操作符。JSON 反序列化时按小写字符串映射。
 */
public enum Op {
    EQ,
    NE,
    LIKE,
    GT,
    GTE,
    LT,
    LTE,
    IN;
    
    /**
     * 从 JSON 字符串解析为 Op，不区分大小写。解析失败返回 null。
     */
    public static Op from(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        for (Op op : values()) {
            if (op.name().equalsIgnoreCase(s)) {
                return op;
            }
        }
        return null;
    }
    
    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }
}
