package com.lrenyi.template.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrenyi.template.platform.meta.EntityMeta;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.data.domain.Pageable;

/**
 * 内存版 CRUD 实现，用于演示与测试。不持久化。支持 Long、String、UUID 主键。
 */
public class InMemoryEntityCrudService implements EntityCrudService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Map<Object, Object>> store = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> longIdGen = new ConcurrentHashMap<>();

    @Override
    public List<?> list(EntityMeta entityMeta, Pageable pageable) {
        String path = entityMeta.getPathSegment();
        Map<Object, Object> map = store.get(path);
        if (map == null) {
            return List.of();
        }
        List<Object> list = new ArrayList<>(map.values());
        int from = (int) pageable.getOffset();
        int to = Math.min(from + pageable.getPageSize(), list.size());
        return from >= list.size() ? List.of() : list.subList(from, to);
    }

    @Override
    public Object get(EntityMeta entityMeta, Object id) {
        Map<Object, Object> map = store.get(entityMeta.getPathSegment());
        return map == null ? null : map.get(id);
    }

    @Override
    public Object create(EntityMeta entityMeta, Object body) {
        if (entityMeta.getEntityClass() == null) {
            throw new IllegalStateException("Entity class not set for " + entityMeta.getEntityName());
        }
        Object entity = objectMapper.convertValue(body, entityMeta.getEntityClass());
        Object id = nextId(entityMeta.getPathSegment(), entityMeta.getPrimaryKeyType());
        setEntityId(entity, id);
        store.computeIfAbsent(entityMeta.getPathSegment(), k -> new ConcurrentHashMap<>()).put(id, entity);
        return entity;
    }

    @Override
    public Object update(EntityMeta entityMeta, Object id, Object body) {
        Map<Object, Object> map = store.get(entityMeta.getPathSegment());
        if (map == null || !map.containsKey(id)) {
            return null;
        }
        Object entity = objectMapper.convertValue(body, entityMeta.getEntityClass());
        setEntityId(entity, id);
        map.put(id, entity);
        return entity;
    }

    @Override
    public void delete(EntityMeta entityMeta, Object id) {
        Map<Object, Object> map = store.get(entityMeta.getPathSegment());
        if (map != null) {
            map.remove(id);
        }
    }

    @Override
    public void deleteBatch(EntityMeta entityMeta, List<?> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Map<Object, Object> map = store.get(entityMeta.getPathSegment());
        if (map != null) {
            for (Object id : ids) {
                map.remove(id);
            }
        }
    }

    @Override
    public List<?> updateBatch(EntityMeta entityMeta, List<Object> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        Map<Object, Object> map = store.get(entityMeta.getPathSegment());
        if (map == null) {
            return List.of();
        }
        List<Object> result = new ArrayList<>(entities.size());
        for (Object entity : entities) {
            Object id = getEntityId(entity);
            if (id != null && map.containsKey(id)) {
                setEntityId(entity, id);
                map.put(id, entity);
                result.add(entity);
            }
        }
        return result;
    }

    private Object getEntityId(Object entity) {
        try {
            Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            return idField.get(entity);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return null;
        }
    }

    private Object nextId(String pathSegment, Class<?> primaryKeyType) {
        if (primaryKeyType == null || primaryKeyType == void.class || primaryKeyType == Long.class || primaryKeyType == long.class) {
            return longIdGen.computeIfAbsent(pathSegment, k -> new AtomicLong(0)).incrementAndGet();
        }
        if (primaryKeyType == UUID.class) {
            return UUID.randomUUID();
        }
        if (primaryKeyType == String.class) {
            return "id-" + longIdGen.computeIfAbsent(pathSegment, k -> new AtomicLong(0)).incrementAndGet();
        }
        return longIdGen.computeIfAbsent(pathSegment, k -> new AtomicLong(0)).incrementAndGet();
    }

    private void setEntityId(Object entity, Object id) {
        if (entity == null || id == null) {
            return;
        }
        try {
            Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // ignore
        }
    }
}
