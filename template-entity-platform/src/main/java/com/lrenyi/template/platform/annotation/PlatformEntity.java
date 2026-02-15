package com.lrenyi.template.platform.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记平台实体，用于注册 EntityMeta。表名、path、CRUD 开关、权限可在此声明。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PlatformEntity {

    /**
     * 数据库表名，默认按类名转下划线复数。
     */
    String table() default "";

    /**
     * URL path 片段，如 users。默认按类名转小写复数。
     */
    String pathSegment() default "";

    /**
     * 显示名称。
     */
    String displayName() default "";

    /**
     * 是否启用 CRUD。
     */
    boolean crudEnabled() default true;

    /**
     * 创建权限标识。
     */
    String permissionCreate() default "";

    /**
     * 查询权限标识。
     */
    String permissionRead() default "";

    /**
     * 更新权限标识。
     */
    String permissionUpdate() default "";

    /**
     * 删除权限标识。
     */
    String permissionDelete() default "";

    /**
     * 是否为此实体生成 CRUD 用请求/响应 DTO（CreateDTO、UpdateDTO、ResponseDTO），默认 true。
     */
    boolean generateDtos() default true;
}
