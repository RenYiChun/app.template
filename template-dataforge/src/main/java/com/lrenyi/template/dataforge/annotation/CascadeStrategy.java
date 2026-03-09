package com.lrenyi.template.dataforge.annotation;

/**
 * 级联删除策略。
 */
public enum CascadeStrategy {

    /**
     * 限制删除：如果有关联数据，禁止删除
     */ 
    RESTRICT,

    /**
     * 置空：删除时将关联字段设为 NULL
     */
    SET_NULL,

    /**
     * 级联删除：删除时同时删除关联数据（危险，需谨慎使用）
     */
    CASCADE
}
