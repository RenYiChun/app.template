package com.lrenyi.template.dataforge.support;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 单个过滤条件：字段、操作符、值。
 */
public record FilterCondition(String field, Op op, Object value) {
    
    @JsonCreator
    public FilterCondition(@JsonProperty("field") String field,
            @JsonProperty("op") String opStr,
            @JsonProperty("value") Object value) {
        this(field, Op.from(opStr != null ? opStr : ""), value);
    }
}
