package com.lrenyi.template.dataforge.annotation;

/**
 * 搜索类型
 */
public enum SearchType {
    
    /** 等于 */
    EQUALS,
    
    /** 不等于 */
    NOT_EQUALS,
    
    /** 模糊匹配 */
    LIKE,
    
    /** 不模糊匹配 */
    NOT_LIKE,
    
    /** 开头匹配 */
    STARTS_WITH,
    
    /** 结尾匹配 */
    ENDS_WITH,
    
    /** 大于 */
    GREATER_THAN,
    
    /** 小于 */
    LESS_THAN,
    
    /** 大于等于 */
    GREATER_EQUALS,
    
    /** 小于等于 */
    LESS_EQUALS,
    
    /** 介于之间 */
    BETWEEN,
    
    /** 包含在集合中 */
    IN,
    
    /** 不包含在集合中 */
    NOT_IN,
    
    /** 为空 */
    IS_NULL,
    
    /** 不为空 */
    IS_NOT_NULL
}