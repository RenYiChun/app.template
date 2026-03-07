package com.lrenyi.template.dataforge.support;

/**
 * 导出时对字段值的转换策略。可用于将枚举转为文案、日期格式化、布尔转「是/否」等。
 * 在实体字段上通过 {@link com.lrenyi.template.dataforge.annotation.DataforgeExport}(converter=XXX.class) 指定实现类。
 */
@FunctionalInterface
public interface ExportValueConverter {
    
    /**
     * 将字段原始值转换为导出到 Excel 的显示值。
     *
     * @param value 字段原始值（可能为 null）
     * @return 导出值，可为 String、Number、Boolean 等，null 表示导出为空单元格
     */
    Object toExportValue(Object value);
}
