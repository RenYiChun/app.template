package com.lrenyi.template.fastgen.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 列定义，用于建表与 ORM 映射。
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface Column {

    /**
     * 数据库列名，默认按字段名转下划线。
     */
    String name() default "";

    /**
     * 长度，适用于字符串类型。
     */
    int length() default 255;

    /**
     * 是否非空。
     */
    boolean nullable() default true;
}
