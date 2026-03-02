package com.lrenyi.template.dataforge.support;

/**
 * 导入值转换器接口，用于将导入的字符串值转换为目标类型。
 */
@FunctionalInterface
public interface ImportValueConverter {
    
    /**
     * 将导入的字符串值转换为目标类型。
     *
     * @param value 导入的字符串值
     * @param targetType 目标类型
     * @return 转换后的值
     */
    Object convert(String value, Class<?> targetType);
}