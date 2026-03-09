package com.lrenyi.template.dataforge.meta;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

/**
 * 实体列表页 UI 布局元数据。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EntityUiLayoutMeta {

    /** 布局模式：table | masterDetailTree */
    private String mode = "table";

    /** 左树右表时的树配置，mode=table 时可为 null */
    private MasterDetailTreeMeta masterDetailTree;
}
