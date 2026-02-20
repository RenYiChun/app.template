package com.lrenyi.template.dataforge.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @deprecated 已废弃，请使用 {@link DataforgeExport} 注解的 {@code enabled} 属性。
 * 例如：{@code @DataforgeExport(enabled = false)}
 * 
 * 标注在实体字段上，表示该字段不参与导出（如 GET /api/{entity}/export 的 Excel 导出）。
 * 未标注的字段默认会导出。
 */
@Deprecated(since = "2.4.3", forRemoval = true)
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExportExclude {
}
