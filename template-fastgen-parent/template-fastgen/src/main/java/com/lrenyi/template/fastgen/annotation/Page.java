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

    /**
     * 后端 API 路径。非空时自动生成对应的 Controller 与 Request DTO，接收表单提交。
     * 例如 "/api/auth/login" 会生成 POST 该路径的接口。
     */
    String apiPath() default "";

    /**
     * 提交成功后的前端跳转路径。非空时提交成功会执行 router.push(successPath)，如登录页填 "/" 表示进入主页。
     */
    String successPath() default "";
}
