package com.lrenyi.template.platform.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记实体扩展 Action，用于注册到 ActionRegistry。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EntityAction {

    /**
     * 所属实体类。
     */
    Class<?> entity();

    /**
     * 动作名，对应 URL 最后一段，如 resetPassword。
     */
    String actionName();

    /**
     * 请求体类型，无 body 时用 Void 或 EmptyRequest。
     */
    Class<?> requestType() default Void.class;

    /**
     * 响应类型。
     */
    Class<?> responseType() default Void.class;

    /**
     * 简短描述，用于 OpenAPI summary。
     */
    String summary() default "";

    /**
     * 详细描述。
     */
    String description() default "";

    /**
     * 所需权限标识，可多个。
     */
    String[] permissions() default {};
}
