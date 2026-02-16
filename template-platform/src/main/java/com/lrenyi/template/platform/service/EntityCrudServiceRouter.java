package com.lrenyi.template.platform.service;

import com.lrenyi.template.platform.meta.EntityMeta;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 按 entity pathSegment 将 CRUD 请求路由到对应实现；未注册的实体走 defaultService。
 */
public class EntityCrudServiceRouter implements EntityCrudService {

    private final EntityCrudService defaultService;
    private final Map<String, EntityCrudService> pathSegmentToDelegate;

    public EntityCrudServiceRouter(EntityCrudService defaultService,
                                   Map<String, EntityCrudService> pathSegmentToDelegate) {
        this.defaultService = defaultService;
        this.pathSegmentToDelegate = pathSegmentToDelegate != null ? pathSegmentToDelegate : Map.of();
    }

    private EntityCrudService target(EntityMeta entityMeta) {
        String pathSegment = entityMeta != null ? entityMeta.getPathSegment() : null;
        if (pathSegment != null && pathSegmentToDelegate.containsKey(pathSegment)) {
            return pathSegmentToDelegate.get(pathSegment);
        }
        return defaultService;
    }

    @Override
    public Page<?> list(EntityMeta entityMeta, Pageable pageable) {
        return target(entityMeta).list(entityMeta, pageable);
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
    public List<?> updateBatch(EntityMeta entityMeta, List<Object> entities) {
        return target(entityMeta).updateBatch(entityMeta, entities);
    }
}
