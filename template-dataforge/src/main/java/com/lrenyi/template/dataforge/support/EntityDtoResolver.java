package com.lrenyi.template.dataforge.support;

import com.lrenyi.template.dataforge.meta.EntityMeta;

/**
 * 按约定解析实体对应的生成 DTO 类（CreateDTO、UpdateDTO、ResponseDTO、PageResponseDTO）。
 * 约定：{entityPackage}.dto.{EntitySimpleName}CreateDTO / UpdateDTO / ResponseDTO / PageResponseDTO。
 * RESPONSE 用于单条详情；PAGE_RESPONSE 用于分页列表项，二者独立。
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

    /** 分页列表项 DTO，仅包含 @DataforgeDto(include = DtoType.PAGE_RESPONSE) 的字段。 */
    public static Class<?> resolvePageResponseDto(EntityMeta meta) {
        return resolve(meta, "PageResponseDTO");
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
