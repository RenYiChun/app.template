package com.lrenyi.template.dataforge.support;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于 JDK 9+ VarHandle 的高性能 BeanAccessor 实现。
 * <p>
 * 相比传统反射，VarHandle 提供了直接的内存访问能力，且对 JIT 更加友好，
 * 能够显著提升高频属性访问（如列表查询、Excel导出）的性能。
 * </p>
 */
@Slf4j
public class VarHandleBeanAccessor implements BeanAccessor {
    
    private final Class<?> beanClass;
    private final Map<String, VarHandle> getters = new ConcurrentHashMap<>();
    private final Constructor<?> constructor;
    
    public VarHandleBeanAccessor(Class<?> beanClass) {
        this.beanClass = beanClass;
        this.constructor = resolveConstructor(beanClass);
        initializeVarHandles(beanClass);
    }
    
    private Constructor<?> resolveConstructor(Class<?> clazz) {
        try {
            Constructor<?> c = clazz.getDeclaredConstructor();
            if (!c.canAccess(null)) {
                c.setAccessible(true); //NOSONAR
            }
            return c;
        } catch (NoSuchMethodException e) {
            // 某些实体可能没有无参构造（如记录类型Record），暂忽略
            return null;
        } catch (Exception e) {
            log.warn("Failed to resolve default constructor for {}", clazz.getName(), e);
            return null;
        }
    }
    
    private void initializeVarHandles(Class<?> clazz) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                // 使用 privateLookupIn 获取私有字段访问权限
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(current, MethodHandles.lookup());
                for (Field field : current.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    String name = field.getName();
                    // 子类字段覆盖父类同名字段时，优先保留子类（通常应该避免这种情况）
                    if (!getters.containsKey(name)) {
                        valueSet(field, lookup, current, name);
                    }
                }
            } catch (IllegalAccessException e) {
                // 模块化限制或安全管理器限制
                // 提示：若实体类位于未 open 的模块中，privateLookupIn 会失败
                // 此时 getters 中缺失该类定义的字段，运行时 get/set 将抛出异常或需回退
                log.warn("Failed to acquire private lookup for class {}. Ensure the package is open to this module if "
                                 + "using JPMS.", current.getName(), e
                );
            }
            current = current.getSuperclass();
        }
    }
    
    private void valueSet(Field field, MethodHandles.Lookup lookup, Class<?> current, String name) {
        try {
            VarHandle vh = lookup.findVarHandle(current, name, field.getType());
            getters.put(name, vh);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // 在 findVarHandle 失败时，可能是因为字段在父类中不可见或其他原因
            // 但由于我们使用了 privateLookupIn(current)，理论上应该能访问 current 的私有字段
            log.warn("Failed to create VarHandle for field {}.{}", current.getName(), name, e);
        }
    }
    
    @Override
    public Object get(Object bean, String propertyName) {
        VarHandle vh = getters.get(propertyName);
        if (vh == null) {
            throw new IllegalArgumentException("Property '" + propertyName + "' not found in " + beanClass.getName());
        }
        return vh.get(bean);
    }
    
    @Override
    public void set(Object bean, String propertyName, Object value) {
        VarHandle vh = getters.get(propertyName);
        if (vh == null) {
            throw new IllegalArgumentException("Property '" + propertyName + "' not found in " + beanClass.getName());
        }
        vh.set(bean, value);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> T newInstance() {
        if (constructor == null) {
            throw new IllegalStateException("No default constructor found for " + beanClass.getName());
        }
        try {
            return (T) constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to instantiate " + beanClass.getName(), e);
        }
    }
}
