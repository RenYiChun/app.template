package com.lrenyi.template.dataforge.support;

/**
 * 实体属性访问器接口，提供高性能的属性读写能力。
 * 旨在替代直接的反射调用，支持 VarHandle 等现代高性能实现。
 */
public interface BeanAccessor {
    
    /**
     * 获取属性值
     *
     * @param bean         实体对象
     * @param propertyName 属性名
     * @return 属性值
     */
    Object get(Object bean, String propertyName);
    
    /**
     * 设置属性值
     *
     * @param bean         实体对象
     * @param propertyName 属性名
     * @param value        属性值
     */
    void set(Object bean, String propertyName, Object value);
    
    /**
     * 创建实体实例
     *
     * @param <T> 实体类型
     * @return 新实例
     */
    <T> T newInstance();
}
