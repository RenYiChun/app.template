package com.lrenyi.template.dataforge.annotation;

/**
 * DTO 类型：用于字段注解控制该字段出现在哪些 DTO 中。
 */
public enum DtoType {

    /** 创建请求 DTO（CreateDTO） */
    CREATE,

    /** 更新请求 DTO（UpdateDTO） */
    UPDATE,

    /** 响应 DTO（ResponseDTO） */
    RESPONSE,
    
    /** 查询请求 DTO（QueryDTO，搜索条件） */
    QUERY,
    
    /** 分页响应 DTO（PageResponseDTO） */
    PAGE_RESPONSE,
    
    /** 导出 DTO（ExportDTO） */
    EXPORT,
    
    /** 导入 DTO（ImportDTO） */
    IMPORT,
    
    /** 简化响应 DTO（用于下拉框等） */
    SIMPLE_RESPONSE,
    
    /** 详情响应 DTO（包含关联数据） */
    DETAIL_RESPONSE,
    
    /** 批量创建请求 DTO */
    BATCH_CREATE,
    
    /** 更新请求 DTO */
    BATCH_UPDATE
}
