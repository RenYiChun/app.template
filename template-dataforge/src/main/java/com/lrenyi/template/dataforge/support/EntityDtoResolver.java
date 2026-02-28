package com.lrenyi.template.dataforge.support;

import com.lrenyi.template.dataforge.meta.EntityMeta;
import lombok.extern.slf4j.Slf4j;

/**
 * 按约定解析实体对应的生成 DTO 类（CreateDTO、UpdateDTO、ResponseDTO、PageResponseDTO）。
 * 约定：{entityPackage}.dto.{EntitySimpleName}CreateDTO / UpdateDTO / ResponseDTO / PageResponseDTO。
 * RESPONSE 用于单条详情；PAGE_RESPONSE 用于分页列表项，二者独立。
 * 必须使用实体类所在 ClassLoader 加载，否则在 Spring Boot 可执行 JAR 或部分部署下会找不到生成的 DTO。
 */
@Slf4j
public final class EntityDtoResolver {
    
    private EntityDtoResolver() {
    }
    
    public static Class<?> resolveCreateDto(EntityMeta meta) {
        return resolve(meta, "CreateDTO");
    }
    
    private static Class<?> resolve(EntityMeta meta, String suffix) {
        Class<?> entityClass = meta.getEntityClass();
        if (entityClass == null) {
            log.debug("[EntityDtoResolver] entityClass is null for pathSegment={}, cannot resolve {}",
                      meta.getPathSegment(),
                      suffix
            );
            return null;
        }
        String pkg = entityClass.getPackageName();
        String simple = entityClass.getSimpleName();
        String className = pkg + ".dto." + simple + suffix;
        ClassLoader loader = entityClass.getClassLoader();
        try {
            return Class.forName(className, true, loader != null ? loader : ClassLoader.getSystemClassLoader());
        } catch (ClassNotFoundException e) {
            log.warn("[EntityDtoResolver] DTO class not found: {} (entity={}, pathSegment={}). "
                             + "Ensure the annotation processor ran and the module was compiled (e.g. mvn clean "
                             + "compile).", className, simple, meta.getPathSegment()
            );
            return null;
        }
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
}
