package com.lrenyi.template.entityplatform.service;

import com.lrenyi.template.entityplatform.meta.EntityMeta;
import java.util.List;
import org.springframework.data.domain.Pageable;

/**
 * 委托型 CRUD 服务：将请求转发给默认实现。业务方可继承此类，仅重写需要特殊逻辑的实体或方法，
 * 其余调用 {@code defaultService}。
 * <p>
 * 使用方式：实现类加 {@code @Primary} 并注入 {@code @Qualifier("defaultEntityCrudService") EntityCrudService}，
 * 在需要自定义的逻辑里编写业务，否则调用 {@code defaultService.list/create/...}。
 */
public class DelegatingEntityCrudService implements EntityCrudService {

    protected final EntityCrudService defaultService;

    public DelegatingEntityCrudService(EntityCrudService defaultService) {
        this.defaultService = defaultService;
    }

    @Override
    public List<?> list(EntityMeta entityMeta, Pageable pageable) {
        return defaultService.list(entityMeta, pageable);
    }

    @Override
    public Object get(EntityMeta entityMeta, Long id) {
        return defaultService.get(entityMeta, id);
    }

    @Override
    public Object create(EntityMeta entityMeta, Object body) {
        return defaultService.create(entityMeta, body);
    }

    @Override
    public Object update(EntityMeta entityMeta, Long id, Object body) {
        return defaultService.update(entityMeta, id, body);
    }

    @Override
    public void delete(EntityMeta entityMeta, Long id) {
        defaultService.delete(entityMeta, id);
    }

    @Override
    public void deleteBatch(EntityMeta entityMeta, List<Long> ids) {
        defaultService.deleteBatch(entityMeta, ids);
    }

    @Override
    public List<?> updateBatch(EntityMeta entityMeta, List<Object> entities) {
        return defaultService.updateBatch(entityMeta, entities);
    }
}
