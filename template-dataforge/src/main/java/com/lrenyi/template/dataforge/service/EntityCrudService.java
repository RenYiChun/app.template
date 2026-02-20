package com.lrenyi.template.dataforge.service;

import com.lrenyi.template.dataforge.meta.EntityMeta;
import com.lrenyi.template.dataforge.support.ListCriteria;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 实体 CRUD 服务接口，可由应用提供实现（如基于 Mapper/Repository）或使用默认内存实现。
 */
public interface EntityCrudService {

    /**
     * 分页查询，返回包含 content、totalElements、totalPages 等元数据的 Page。
     *
     * @param criteria 过滤与排序条件，可为 ListCriteria.empty()
     */
    Page<?> list(EntityMeta entityMeta, Pageable pageable, ListCriteria criteria);

    Object get(EntityMeta entityMeta, Object id);

    Object create(EntityMeta entityMeta, Object body);

    Object update(EntityMeta entityMeta, Object id, Object body);

    void delete(EntityMeta entityMeta, Object id);

    /**
     * 删除：按主键 ID 列表删除多条记录。
     */
    void deleteBatch(EntityMeta entityMeta, List<?> ids);

    /**
     * 更新：按传入的实体列表（每条需带 id）执行更新，返回更新后的实体列表。
     */
    List<?> updateBatch(EntityMeta entityMeta, List<Object> entities);
}
