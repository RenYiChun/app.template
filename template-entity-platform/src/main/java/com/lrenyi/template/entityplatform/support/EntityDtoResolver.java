package com.lrenyi.template.entityplatform.support;

import com.lrenyi.template.entityplatform.meta.EntityMeta;

/**
 * 按约定解析实体对应的生成 DTO 类（CreateDTO、UpdateDTO、ResponseDTO）。
 * 约定：{entityPackage}.dto.{EntitySimpleName}CreateDTO / UpdateDTO / ResponseDTO。
 */
public final class EntityDtoResolver {

    private EntityDtoResolver() {
    }

    public static Class<?> resolveCreateDto(EntityMeta meta) {
        return resolve(meta, "CreateDTO");
    }

    public static Class<?> resolveUpdateDto(EntityMeta meta) {
        return resolve(meta, "UpdateDTO");
    }

    public static Class<?> resolveResponseDto(EntityMeta meta) {
        return resolve(meta, "ResponseDTO");
    }

    private static Class<?> resolve(EntityMeta meta, String suffix) {
        Class<?> entityClass = meta.getEntityClass();
        if (entityClass == null) {
            return null;
        }
        String pkg = entityClass.getPackageName();
        String simple = entityClass.getSimpleName();
        String className = pkg + ".dto." + simple + suffix;
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
