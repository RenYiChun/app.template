package com.lrenyi.template.dataforge.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @deprecated 已废弃，请使用 {@link DataforgeField} 注解的 {@code searchable} 属性。
 * 例如：{@code @DataforgeField(searchable = true)}
 */
@Deprecated(since = "2.4.3", forRemoval = true)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Searchable {

    /**
     * 自定义搜索栏显示的标签。若不指定，默认复用 Schema/Field 上的描述或字段名。
     */
    String label() default "";

    /**
     * 定义排序权重（整数，值越小越靠前）。
     */
    int order() default 0;
}

