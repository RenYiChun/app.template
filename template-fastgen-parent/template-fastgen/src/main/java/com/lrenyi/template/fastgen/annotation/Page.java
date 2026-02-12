package com.lrenyi.template.fastgen.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记页面描述类，驱动单页（如登录页）的生成。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Page {

    /**
     * 页面标题。
     */
    String title();

    /**
     * 布局类型，如 centered、default 等。
     */
    String layout() default "default";

    /**
     * 路由路径，用于前端路由注册。
     */
    String path() default "";
}
