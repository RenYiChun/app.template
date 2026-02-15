package com.lrenyi.template.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrenyi.template.platform.meta.EntityMeta;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.lang.reflect.Field;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于 JPA EntityManager 的通用 CRUD 实现。实体类需为 JPA @Entity，主键为 Long。
 * 当 classpath 存在 spring-boot-starter-data-jpa 且未自定义 EntityCrudService 时自动启用。
 */
public class JpaEntityCrudService implements EntityCrudService {

    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    public JpaEntityCrudService(EntityManager entityManager, ObjectMapper objectMapper) {
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<?> list(EntityMeta entityMeta, Pageable pageable) {
        Class<?> entityClass = entityMeta.getEntityClass();
        if (entityClass == null) {
            throw new IllegalStateException("Entity class not set for " + entityMeta.getEntityName());
        }
        String jpql = "SELECT e FROM " + entityClass.getSimpleName() + " e";
        TypedQuery<Object> q = (TypedQuery<Object>) entityManager.createQuery(jpql, entityClass);
        q.setFirstResult((int) pageable.getOffset());
        q.setMaxResults(pageable.getPageSize());
        return q.getResultList();
    }

    @Override
    @Transactional(readOnly = true)
    public Object get(EntityMeta entityMeta, Long id) {
        Class<?> entityClass = entityMeta.getEntityClass();
        if (entityClass == null) {
            return null;
        }
        return entityManager.find(entityClass, id);
    }

    @Override
    @Transactional
    public Object create(EntityMeta entityMeta, Object body) {
        Class<?> entityClass = entityMeta.getEntityClass();
        if (entityClass == null) {
            throw new IllegalStateException("Entity class not set for " + entityMeta.getEntityName());
        }
        Object entity = objectMapper.convertValue(body, entityClass);
        entityManager.persist(entity);
        return entity;
    }

    @Override
    @Transactional
    public Object update(EntityMeta entityMeta, Long id, Object body) {
        Class<?> entityClass = entityMeta.getEntityClass();
        if (entityClass == null) {
            return null;
        }
        if (entityManager.find(entityClass, id) == null) {
            return null;
        }
        Object entity = objectMapper.convertValue(body, entityClass);
        setEntityId(entity, id);
        return entityManager.merge(entity);
    }

    @Override
    @Transactional
    public void delete(EntityMeta entityMeta, Long id) {
        Class<?> entityClass = entityMeta.getEntityClass();
        if (entityClass == null) {
            return;
        }
        Object entity = entityManager.find(entityClass, id);
        if (entity != null) {
            entityManager.remove(entity);
        }
    }

    @Override
    @Transactional
    public void deleteBatch(EntityMeta entityMeta, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Class<?> entityClass = entityMeta.getEntityClass();
        if (entityClass == null) {
            return;
        }
        for (Long id : ids) {
            Object entity = entityManager.find(entityClass, id);
            if (entity != null) {
                entityManager.remove(entity);
            }
        }
    }

    @Override
    @Transactional
    public List<?> updateBatch(EntityMeta entityMeta, List<Object> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        Class<?> entityClass = entityMeta.getEntityClass();
        if (entityClass == null) {
            throw new IllegalStateException("Entity class not set for " + entityMeta.getEntityName());
        }
        List<Object> result = new java.util.ArrayList<>(entities.size());
        for (Object entity : entities) {
            Long id = getEntityId(entity);
            if (id != null && entityManager.find(entityClass, id) != null) {
                result.add(entityManager.merge(entity));
            }
        }
        return result;
    }

    private Long getEntityId(Object entity) {
        try {
            Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            Object v = idField.get(entity);
            return v instanceof Long l ? l : (v instanceof Number n ? n.longValue() : null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return null;
        }
    }

    private void setEntityId(Object entity, Long id) {
        try {
            Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // ignore
        }
    }
}
