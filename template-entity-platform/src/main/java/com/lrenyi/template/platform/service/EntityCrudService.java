package com.lrenyi.template.platform.service;

import com.lrenyi.template.platform.meta.EntityMeta;
import java.util.List;
import org.springframework.data.domain.Pageable;

/**
 * 实体 CRUD 服务接口，可由应用提供实现（如基于 Mapper/Repository）或使用默认内存实现。
 */
public interface EntityCrudService {

    List<?> list(EntityMeta entityMeta, Pageable pageable);

    Object get(EntityMeta entityMeta, Long id);

    Object create(EntityMeta entityMeta, Object body);

    Object update(EntityMeta entityMeta, Long id, Object body);

    void delete(EntityMeta entityMeta, Long id);

    /**
     * 批量删除：按主键 ID 列表删除多条记录。
     */
    void deleteBatch(EntityMeta entityMeta, List<Long> ids);

    /**
     * 批量更新：按传入的实体列表（每条需带 id）执行更新，返回更新后的实体列表。
     */
    List<?> updateBatch(EntityMeta entityMeta, List<Object> entities);
}
