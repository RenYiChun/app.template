package com.lrenyi.template.platform.annotation;

/**
 * DTO 类型：用于字段注解控制该字段出现在哪些 DTO 中。
 */
public enum DtoType {

    /** 创建请求 DTO（CreateDTO） */
    CREATE,

    /** 更新请求 DTO（UpdateDTO） */
    UPDATE,

    /** 响应 DTO（ResponseDTO） */
    RESPONSE
}
