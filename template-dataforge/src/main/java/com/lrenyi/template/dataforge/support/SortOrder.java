package com.lrenyi.template.dataforge.support;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 排序项：字段名与方向（asc/desc）。
 */
public record SortOrder(String field, String dir) {
    @JsonCreator
    public SortOrder(
            @JsonProperty("field") String field,
            @JsonProperty("dir") String dir) {
        this.field = field;
        this.dir = dir;
    }
}
