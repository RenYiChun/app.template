package com.lrenyi.template.platform.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在实体字段上，表示该字段不参与导出（如 GET /api/{entity}/export 的 Excel 导出）。
 * 未标注的字段默认会导出。
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExportExclude {
}
