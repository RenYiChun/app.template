package com.lrenyi.template.dataforge.annotation;

/**
 * 脱敏类型
 */
public enum MaskType {
    
    /** 不脱敏 */
    NONE,
    
    /** 手机号脱敏 */
    PHONE,
    
    /** 邮箱脱敏 */
    EMAIL,
    
    /** 身份证号脱敏 */
    ID_CARD,
    
    /** 银行卡号脱敏 */
    BANK_CARD,
    
    /** 姓名脱敏 */
    NAME,
    
    /** 自定义脱敏规则 */
    CUSTOM
}