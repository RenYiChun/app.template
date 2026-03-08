package com.lrenyi.template.dataforge.support;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * options 接口单项：id、label，可选 extra。
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record EntityOption(Object id, String label, Map<String, Object> extra) {

    public EntityOption(Object id, String label) {
        this(id, label, null);
    }
}
