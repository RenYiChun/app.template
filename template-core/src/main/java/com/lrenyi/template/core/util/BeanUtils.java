package com.lrenyi.template.core.util;

import com.alibaba.fastjson2.JSON;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BeanUtils {
    
    private static final LruCache<String, Method> GET_METHOD_LRU_CACHE = new LruCache<>(256);
    private static final LruCache<String, Method> SET_METHOD_LRU_CACHE = new LruCache<>(256);
    private static final LruCache<String, Field> FIELDS_LIU_CACHE = new LruCache<>(256);
    private static final LruCache<String, List<Field>> classFields = new LruCache<>(128);
    private static final LruCache<String, Annotation> ANNOTATION_LRU_CACHE = new LruCache<>(256);
    
    /**
     * 通过字段取值,优先使用get方法
     */
    public static Object getFiledValue(Field field, Object o) {
        String key = field.getName() + "&" + o.getClass().getName();
        Method method = GET_METHOD_LRU_CACHE.get(key);
        try {
            if (method == null) {
                PropertyDescriptor propertyDescriptor =
                        new PropertyDescriptor(field.getName(), o.getClass());
                method = propertyDescriptor.getReadMethod();
                if (method != null) {
                    GET_METHOD_LRU_CACHE.put(key, method);
                }
            }
        } catch (IntrospectionException e) {
            log.error("", e);
        }
        if (method != null) {
            try {
                return method.invoke(o);
            } catch (IllegalAccessException | InvocationTargetException e) {
                log.error("", e);
            }
        } else {
            field.setAccessible(true);
            try {
                return field.get(o);
            } catch (IllegalAccessException e) {
                log.error("", e);
            }
        }
        return null;
    }
    
    /**
     * 给字段赋值,优先使用set方法
     */
    public static void setFieldValue(Field field, Object o, Object value) throws Exception {
        if (field == null) {
            throw new IllegalAccessException();
        }
        String key = field.getName() + "&" + o.getClass().getName();
        Method method = SET_METHOD_LRU_CACHE.get(key);
        if (method == null) {
            PropertyDescriptor propertyDescriptor = null;
            try {
                propertyDescriptor = new PropertyDescriptor(field.getName(), o.getClass());
                method = propertyDescriptor.getWriteMethod();
                if (method != null) {
                    SET_METHOD_LRU_CACHE.put(key, method);
                }
            } catch (Exception e) {
                log.error("", e);
            }
        }
        if (method != null) {
            method.invoke(o, value);
        } else {
            field.setAccessible(true);
            field.set(o, value);
        }
    }
    
    /**
     * 属性复制
     *
     * @param source 源Bean对象
     * @param target 目标Bean对象
     */
    public static void copyProperties(Object source, Object target) {
        copyProperties(source, target, false);
    }
    
    /**
     * 属性复制
     *
     * @param source        源Bean对象
     * @param target        目标Bean对象
     * @param nullValueCopy 是否复制null值
     */
    public static void copyProperties(Object source, Object target, boolean nullValueCopy) {
        List<Field> declaredFields = getAllFields(source.getClass());
        for (Field field : declaredFields) {
            Object feildValue = getFiledValue(field, source);
            if (feildValue == null && !nullValueCopy) {
                continue;
            }
            try {
                Field declaredField = getAllFieldsByName(target.getClass(), field.getName());
                if (declaredField == null) {
                    //如果目标不存在字段. 跳过
                    continue;
                }
                setFieldValue(field, target, feildValue);
            } catch (Exception e) {
                continue;
            }
        }
    }
    
    /**
     * 根据名字获取类字段. 包含父类字段
     */
    public static Field getAllFieldsByName(Class<?> cls, String fieldName) {
        if (cls == null || EmptyUtil.isEmpty(fieldName)) {
            return null;
        }
        //根据.分隔符 依次往下查找
        if (fieldName.contains(".")) {
            return getSplitByName(cls, fieldName);
        } else {
            List<Field> fields = classFields.get(cls.getName());
            if (fields == null) {
                fields = getAllFields(cls);
            }
            for (Field allField : fields) {
                if (fieldName.equals(allField.getName())) {
                    return allField;
                }
            }
        }
        return null;
    }
    
    private static Field getSplitByName(Class<?> cls, String fieldName) {
        if (fieldName.contains(".")) {
            int i = fieldName.indexOf(".");
            String fieldNm = fieldName.substring(0, i);
            String subFieldNm = fieldName.substring(i + 1);
            Field field = null;
            try {
                field = cls.getDeclaredField(fieldNm);
            } catch (NoSuchFieldException e) {
                log.error("", e);
            }
            if (field == null) {
                return null;
            }
            Class<?> type = field.getType();
            if (Collection.class.isAssignableFrom(type)) {
                Type genericType = field.getGenericType();
                ParameterizedType pt = (ParameterizedType) genericType;
                Type[] actualTypeArguments = pt.getActualTypeArguments();
                Type actualTypeArgument = actualTypeArguments[0];
                String typeName = actualTypeArgument.getTypeName();
                try {
                    cls = Class.forName(typeName);
                    return getSplitByName(cls, subFieldNm);
                } catch (ClassNotFoundException e) {
                    return null;
                }
            } else {
                return getSplitByName(type, subFieldNm);
            }
            
        } else {
            return getAllFieldsByName(cls, fieldName);
        }
    }
    
    public static <T> List<T> convertVos(Collection<?> sources, Class<T> targetCls) {
        List<T> ts = new ArrayList<>();
        for (Object o : sources) {
            T t = convertVo(o, targetCls);
            ts.add(t);
        }
        return ts;
    }
    
    public static <T> T convertVo(Object source, Class<T> targetCls) {
        T t = null;
        try {
            String s = JSON.toJSONString(source);
            t = JSON.parseObject(s, targetCls);
        } catch (Exception e) {
            log.error("", e);
        }
        return t;
    }
    
    /**
     * 获取字段注解
     */
    public static <T extends Annotation> T getAnnotation(String name,
            Class<?> beanCls,
            Class<T> annotationClass) {
        Field field = FIELDS_LIU_CACHE.get(beanCls.getName() + name);
        if (field == null) {
            List<Field> allFields = getAllFields(beanCls);
            for (Field allField : allFields) {
                FIELDS_LIU_CACHE.put(beanCls.getName() + allField.getName(), allField);
                if (allField.getName().equals(name)) {
                    field = allField;
                }
            }
        }
        if (field == null) {
            return null;
        }
        return field.getAnnotation(annotationClass);
    }
    
    public static <T extends Annotation> T getAnnotation(Field field, Class<T> queryFieldClass) {
        Annotation annotation = ANNOTATION_LRU_CACHE.computeIfAbsent(
                field.getDeclaringClass().getName() + field.getName(),
                k -> field.getAnnotation(queryFieldClass)
        );
        return (T) annotation;
    }
    
    /**
     * 获取所有字段 包括父类
     */
    public static List<Field> getAllFields(Class<?> cls) {
        List<Field> fields = classFields.get(cls.getName());
        if (fields == null) {
            fields = new ArrayList<>();
        } else {
            return fields;
        }
        Class<?> tempClass = cls;
        while (tempClass != null && !tempClass.getName().equals("java.lang.object")) {
            //当父类为null
            // 的时候说明到达了最上层的父类(Object类).
            fields.addAll(Arrays.asList(tempClass.getDeclaredFields()));
            tempClass = tempClass.getSuperclass(); //得到父类,然后赋给自己
        }
        classFields.put(cls.getName(), fields);
        return fields;
    }
}
