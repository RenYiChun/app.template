package com.lrenyi.template.platform.meta;

import lombok.Getter;
import lombok.Setter;

/**
 * 字段元数据，用于 CRUD 与 OpenAPI。
 */
@Getter
@Setter
public class FieldMeta {

    private String name;
    private String type;
    private String columnName;
    private boolean primaryKey;
    private boolean required;
    private boolean nullable = true;
    /** 是否从导出中排除（由 @ExportExclude 注解设置） */
    private boolean exportExcluded;
    /** 导出值转换器类名（由 @ExportConverter 注解设置），为空则不转换 */
    private String exportConverterClassName;
}
