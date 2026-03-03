package com.lrenyi.template.dataforge.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import com.lrenyi.template.dataforge.support.ExportValueConverter;

/**
 * 字段导出配置注解。
 * 整合了原{@code @ExportConverter}和{@code @ExportExclude}的功能。
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataforgeExport {
    
    /**
     * 是否启用导出。
     * 原{@code @ExportExclude}逻辑：false表示不导出。
     */
    boolean enabled() default true;
    
    /**
     * 导出列头名称，默认取{@code @DataforgeField.label}。
     */
    String header() default "";
    
    /**
     * 导出列顺序，值越小越靠前。
     */
    int order() default 0;
    
    /**
     * 导出格式化模式（日期、数字等）。
     */
    String format() default "";
    
    /**
     * 导出值转换器。
     * 原{@code @ExportConverter.value}
     */
    Class<? extends ExportValueConverter> converter() default ExportValueConverter.class;
    
    /**
     * Excel列宽（像素）。
     */
    int width() default 0;
    
    /**
     * 单元格样式（如：dataFormat, alignment, fill等）。
     */
    String cellStyle() default "";
    
    /**
     * 是否自动换行。
     */
    boolean wrapText() default false;
    
    /**
     * 列类型：
     * 0-字符串，1-数字，2-日期，3-布尔值，4-公式。
     */
    int columnType() default 0;
    
    /**
     * 列注释。
     */
    String comment() default "";
    
    /**
     * 是否隐藏列。
     */
    boolean hidden() default false;
    
    /**
     * 列分组。
     */
    String group() default "";
    
    /**
     * 是否冻结列。
     */
    boolean frozen() default false;
    
    /**
     * 数据验证规则（Excel数据验证）。
     */
    String dataValidation() default "";
    
    /**
     * 超链接公式。
     */
    String hyperlinkFormula() default "";
}