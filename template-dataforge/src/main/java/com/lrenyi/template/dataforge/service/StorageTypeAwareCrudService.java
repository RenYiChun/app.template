package com.lrenyi.template.dataforge.service;

/**
 * 按存储类型提供 CRUD 的标记接口。实现此接口的 Bean 会被 {@link EntityCrudServiceRouter} 收集，
 * 用于路由 {@link com.lrenyi.template.dataforge.meta.EntityMeta#getStorageType()} 匹配的实体请求。
 * <p>
 * 路由优先级：pathSegment（PathSegmentAwareCrudService）→ storageType（本接口）→ defaultService。
 * 支持 SPI 式扩展：返回自定义类型名（如 {@code "redis"}），无需修改框架。
 */
public interface StorageTypeAwareCrudService extends EntityCrudService {
    
    /**
     * 该实现负责的存储类型标识，与 {@code @DataforgeEntity(storage="...")} 一致。
     * 内置：{@link com.lrenyi.template.dataforge.annotation.StorageTypes#JPA}、
     * {@link com.lrenyi.template.dataforge.annotation.StorageTypes#MONGO}。
     */
    String getStorageType();
}
