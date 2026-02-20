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
     * 是否启用删除（DELETE /{entity}/batch）。仅当 crudEnabled=true 时生效。
     */
    boolean enableDeleteBatch() default true;

    /**
     * 是否启用更新（PUT /{entity}/batch）。仅当 crudEnabled=true 时生效。
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

    // ==================== 新增生产级属性 ====================

    /**
     * 实体描述信息。
     */
    String description() default "";

    /**
     * 默认排序字段。
     */
    String defaultSortField() default "";

    /**
     * 默认排序方向。
     */
    SortDirection defaultSortDirection() default SortDirection.DESC;

    /**
     * 默认分页大小。
     */
    int defaultPageSize() default 20;

    /**
     * 分页大小选项。
     */
    int[] pageSizeOptions() default {10, 20, 50, 100};

    /**
     * 是否启用虚拟滚动（大数据量场景）。
     */
    boolean enableVirtualScroll() default false;

    /**
     * 虚拟滚动行高（像素）。
     */
    int virtualScrollRowHeight() default 54;

    /**
     * 是否为树形实体。
     */
    boolean treeEntity() default false;

    /**
     * 树形父节点字段名。
     */
    String treeParentField() default "parentId";

    /**
     * 树形子节点字段名。
     */
    String treeChildrenField() default "children";

    /**
     * 树形节点名称字段。
     */
    String treeNameField() default "name";

    /**
     * 树形节点编码字段（用于路径）。
     */
    String treeCodeField() default "code";

    /**
     * 树形最大深度限制。
     */
    int treeMaxDepth() default 10;

    /**
     * 是否启用软删除。
     */
    boolean softDelete() default false;

    /**
     * 删除标记字段名。
     */
    String deleteFlagField() default "deleted";

    /**
     * 删除时间字段名。
     */
    String deleteTimeField() default "deleteTime";

    /**
     * 删除标记类型。
     */
    Class<?> deleteFlagType() default Boolean.class;

    /**
     * 是否启用创建审计。
     */
    boolean enableCreateAudit() default true;

    /**
     * 是否启用更新审计。
     */
    boolean enableUpdateAudit() default true;

    /**
     * 是否启用删除审计。
     */
    boolean enableDeleteAudit() default true;

    /**
     * 创建人字段名。
     */
    String createUserField() default "createBy";

    /**
     * 更新人字段名。
     */
    String updateUserField() default "updateBy";

    /**
     * 是否启用缓存。
     */
    boolean enableCache() default false;

    /**
     * 缓存过期时间（秒）。
     */
    long cacheExpireSeconds() default 300;

    /**
     * 缓存区域。
     */
    String cacheRegion() default "";

    /**
     * 是否启用查询优化。
     */
    boolean enableQueryOptimization() default true;

    /**
     * 最大批量操作大小。
     */
    int maxBatchSize() default 1000;

    /**
     * 实体标签，用于分类。
     */
    String[] tags() default {};

    /**
     * 实体图标。
     */
    String icon() default "";

    /**
     * 实体颜色。
     */
    String color() default "";

    /**
     * 是否在菜单中显示。
     */
    boolean showInMenu() default true;

    /**
     * 菜单排序权重。
     */
    int menuOrder() default 0;

    /**
     * 是否启用操作日志。
     */
    boolean enableOperationLog() default true;

    /**
     * 是否启用数据版本控制。
     */
    boolean enableVersionControl() default false;

    /**
     * 版本字段名。
     */
    String versionField() default "version";

    /**
     * 是否启用数据权限。
     */
    boolean enableDataPermission() default false;

    /**
     * 数据权限类型。
     */
    String dataPermissionType() default "";

    /**
     * 是否启用导入功能。
     */
    boolean enableImport() default true;

    /**
     * 导入模板文件路径。
     */
    String importTemplate() default "";

    /**
     * 导出模板文件路径。
     */
    String exportTemplate() default "";
}
