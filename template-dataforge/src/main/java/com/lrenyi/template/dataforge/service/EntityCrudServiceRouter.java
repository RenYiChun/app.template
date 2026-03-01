package com.lrenyi.template.dataforge.service;

import java.util.List;
import java.util.Map;
import com.lrenyi.template.dataforge.meta.EntityMeta;
import com.lrenyi.template.dataforge.support.ListCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * 按 entity pathSegment 或 storageType 将 CRUD 请求路由到对应实现。
 * 路由优先级：pathSegment → storageType → defaultService。
 */
public class EntityCrudServiceRouter implements EntityCrudService {
    
    private final EntityCrudService defaultService;
    private final Map<String, EntityCrudService> pathSegmentToDelegate;
    private final Map<String, EntityCrudService> storageTypeToDelegate;
    
    public EntityCrudServiceRouter(EntityCrudService defaultService,
            Map<String, EntityCrudService> pathSegmentToDelegate,
            Map<String, EntityCrudService> storageTypeToDelegate) {
        this.defaultService = defaultService;
        this.pathSegmentToDelegate = pathSegmentToDelegate != null ? pathSegmentToDelegate : Map.of();
        this.storageTypeToDelegate = storageTypeToDelegate != null ? storageTypeToDelegate : Map.of();
    }
    
    @Override
    public Page<Object> list(EntityMeta entityMeta, Pageable pageable, ListCriteria criteria) {
        return target(entityMeta).list(entityMeta, pageable, criteria);
    }
    
    private EntityCrudService target(EntityMeta entityMeta) {
        String pathSegment = entityMeta != null ? entityMeta.getPathSegment() : null;
        if (pathSegment != null && pathSegmentToDelegate.containsKey(pathSegment)) {
            return pathSegmentToDelegate.get(pathSegment);
        }
        String storageType = entityMeta != null ? entityMeta.getStorageType() : null;
        if (storageType != null && !storageType.isBlank() && storageTypeToDelegate.containsKey(storageType)) {
            return storageTypeToDelegate.get(storageType);
        }
        return defaultService;
    }
    
    @Override
    public Object get(EntityMeta entityMeta, Object id) {
        return target(entityMeta).get(entityMeta, id);
    }
    
    @Override
    public Object create(EntityMeta entityMeta, Object body) {
        return target(entityMeta).create(entityMeta, body);
    }
    
    @Override
    public Object update(EntityMeta entityMeta, Object id, Object body) {
        return target(entityMeta).update(entityMeta, id, body);
    }
    
    @Override
    public void delete(EntityMeta entityMeta, Object id) {
        target(entityMeta).delete(entityMeta, id);
    }
    
    @Override
    public void deleteBatch(EntityMeta entityMeta, List<?> ids) {
        target(entityMeta).deleteBatch(entityMeta, ids);
    }
    
    @Override
    public List<Object> updateBatch(EntityMeta entityMeta, List<Object> entities) {
        return target(entityMeta).updateBatch(entityMeta, entities);
    }
}
