package com.lrenyi.template.dataforge.support;

/**
 * Dataforge 模块业务错误码（3000–3071）。
 * 用于关联、实体、批量查询、导入、级联删除等场景的统一错误响应。
 */
public final class DataforgeErrorCodes {

    private DataforgeErrorCodes() {}

    /** 实体不存在（options/tree/batch-lookup 等按 entityName 查找时） */
    public static final int ENTITY_NOT_FOUND = 3000;
    /** 关联目标不存在或已软删 */
    public static final int ASSOCIATION_TARGET_NOT_FOUND = 3001;
    /** 存在关联数据，禁止删除（RESTRICT） */
    public static final int CASCADE_DELETE_RESTRICT = 3010;
    /** 批量查询 ID 超限 */
    public static final int BATCH_LOOKUP_IDS_OVERFLOW = 3041;
    /** 导入时关联预加载失败 */
    public static final int IMPORT_ASSOCIATION_PRELOAD_FAILED = 3070;
    /** 检测到循环引用 */
    public static final int CIRCULAR_REFERENCE = 3071;
    /** 导入：关联显示值找不到对应 ID（第 row 行，字段 field，值 value） */
    public static final int IMPORT_ASSOCIATION_NOT_FOUND = 3020;
}
