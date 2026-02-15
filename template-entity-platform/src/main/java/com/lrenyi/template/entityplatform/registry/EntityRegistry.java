package com.lrenyi.template.entityplatform.registry;

import com.lrenyi.template.entityplatform.meta.EntityMeta;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体元数据注册表，按 pathSegment 与 entityName 查找。
 */
public class EntityRegistry {

    private final Map<String, EntityMeta> byPathSegment = new ConcurrentHashMap<>();
    private final Map<String, EntityMeta> byEntityName = new ConcurrentHashMap<>();

    public void register(EntityMeta meta) {
        if (meta == null || meta.getPathSegment() == null || meta.getEntityName() == null) {
            return;
        }
        byPathSegment.put(meta.getPathSegment(), meta);
        byEntityName.put(meta.getEntityName(), meta);
    }

    public EntityMeta getByPathSegment(String pathSegment) {
        return pathSegment == null ? null : byPathSegment.get(pathSegment);
    }

    public EntityMeta getByEntityName(String entityName) {
        return entityName == null ? null : byEntityName.get(entityName);
    }

    public List<EntityMeta> getAll() {
        return new ArrayList<>(byEntityName.values());
    }
}
