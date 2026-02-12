package com.lrenyi.template.fastgen.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记领域实体，驱动后端 CRUD 与前端列表/表单的生成。
 * 表名用于生成建表语句与 Mapper。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Domain {

    /**
     * 数据库表名，默认按类名转下划线。
     */
    String table() default "";

    /**
     * 显示名称，用于页面标题等，默认按类名。
     */
    String displayName() default "";
}
