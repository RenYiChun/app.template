package com.lrenyi.template.entityplatform.annotation;

import com.lrenyi.template.entityplatform.support.ExportValueConverter;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在实体字段上，指定该字段导出到 Excel 时的值转换策略。
 * 转换器实现需为无参可实例化类。
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExportConverter {

    /**
     * 转换器实现类，需实现 {@link ExportValueConverter} 且具无参构造。
     */
    Class<? extends ExportValueConverter> value();
}
