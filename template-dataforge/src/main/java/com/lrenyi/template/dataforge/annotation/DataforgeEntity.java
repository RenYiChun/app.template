package com.lrenyi.template.dataforge.annotation;

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
public @interface DataforgeEntity {

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
     * 是否启用 CRUD（总开关）。为 false 时该实体不提供任何 CRUD 与导出接口。
     */
    boolean crudEnabled() default true;

    /**
     * 是否启用列表查询（GET /{entity}）。仅当 crudEnabled=true 时生效。
     */
    boolean enableList() default true;

    /**
     * 是否启用单条查询（GET /{entity}/{id}）。仅当 crudEnabled=true 时生效。
     */
    boolean enableGet() default true;

    /**
     * 是否启用创建（POST /{entity}）。仅当 crudEnabled=true 时生效。
     */
    boolean enableCreate() default true;

    /**
     * 是否启用更新（PUT /{entity}/{id}）。仅当 crudEnabled=true 时生效。
     */
    boolean enableUpdate() default true;

    /**
     * 是否启用删除（DELETE /{entity}/{id}）。仅当 crudEnabled=true 时生效。
     */
    boolean enableDelete() default true;

    /**
     * 是否启用批量删除（DELETE /{entity}/batch）。仅当 crudEnabled=true 时生效。
     */
    boolean enableDeleteBatch() default true;

    /**
     * 是否启用批量更新（PUT /{entity}/batch）。仅当 crudEnabled=true 时生效。
     */
    boolean enableUpdateBatch() default true;

    /**
     * 是否启用导出 Excel（GET /{entity}/export）。仅当 crudEnabled=true 时生效。
     */
    boolean enableExport() default true;

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

    /**
     * 主键类型（Long、String、UUID 等）。默认 void 表示未指定，由扫描时从实体 id 字段类型推断。
     */
    Class<?> primaryKeyType() default void.class;
}
