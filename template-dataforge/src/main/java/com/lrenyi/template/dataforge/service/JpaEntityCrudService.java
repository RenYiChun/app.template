package com.lrenyi.template.dataforge.service;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrenyi.template.dataforge.meta.EntityMeta;
import com.lrenyi.template.dataforge.support.FilterCondition;
import com.lrenyi.template.dataforge.support.ListCriteria;
import com.lrenyi.template.dataforge.support.SortOrder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import static com.lrenyi.template.dataforge.service.InMemoryEntityCrudService.getValueOfObject;

/**
 * 基于 JPA EntityManager 的通用 CRUD 实现。实体类需为 JPA @Entity，主键类型可为 Long、String、UUID 等。
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
    public Page<?> list(EntityMeta entityMeta, Pageable pageable, ListCriteria criteria) {
        Class<?> entityClass = entityMeta.getEntityClass();
        if (entityClass == null) {
            throw new IllegalStateException("Entity class not set for " + entityMeta.getEntityName());
        }
        String entityName = entityClass.getSimpleName();
        ListCriteria c = criteria != null ? criteria : ListCriteria.empty();
        StringBuilder whereClause = new StringBuilder();
        List<Object> params = new ArrayList<>();
        buildWhere(whereClause, params, c.getFilters(), "e", entityClass);
        String where = !whereClause.isEmpty() ? " WHERE " + whereClause : "";
        long total = countWithWhere(entityName, where, params);
        Sort sort = resolveSort(c.getSortOrders(), pageable.getSort());
        String orderBy = buildOrderBy(sort, "e", entityClass);
        String jpql = "SELECT e FROM " + entityName + " e" + where + orderBy;
        TypedQuery<Object> q = (TypedQuery<Object>) entityManager.createQuery(jpql, entityClass);
        for (int i = 0; i < params.size(); i++) {
            q.setParameter("p" + i, params.get(i));
        }
        q.setFirstResult((int) pageable.getOffset());
        q.setMaxResults(pageable.getPageSize());
        List<Object> content = q.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    private static java.util.Set<String> getEntityFieldNames(Class<?> entityClass) {
        java.util.Set<String> names = new java.util.HashSet<>();
        for (Class<?> c = entityClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                names.add(f.getName());
            }
        }
        return names;
    }

    private static boolean isValidFieldName(String field, java.util.Set<String> allowedFields) {
        return field != null && !field.isBlank() && allowedFields.contains(field);
    }

    private void buildWhere(StringBuilder sb, List<Object> params, List<FilterCondition> filters, String entityAlias,
            Class<?> entityClass) {
        java.util.Set<String> allowedFields = getEntityFieldNames(entityClass);
        for (FilterCondition fc : filters) {
            if (fc == null || fc.op() == null) {
                continue;
            }
            if (!isValidFieldName(fc.field(), allowedFields)) {
                throw new IllegalArgumentException("Invalid filter field: " + fc.field());
            }
            if (!sb.isEmpty()) {
                sb.append(" AND ");
            }
            String attr = fc.field();
            String paramName = "p" + params.size();
            switch (fc.op()) {
                case EQ -> {
                    sb.append(entityAlias).append(".").append(attr).append(" = :").append(paramName);
                    params.add(fc.value());
                }
                case NE -> {
                    sb.append(entityAlias).append(".").append(attr).append(" <> :").append(paramName);
                    params.add(fc.value());
                }
                case LIKE -> {
                    sb.append(entityAlias).append(".").append(attr).append(" LIKE :").append(paramName);
                    Object v = fc.value();
                    params.add(v instanceof String s ? "%" + s + "%" : v);
                }
                case GT -> {
                    sb.append(entityAlias).append(".").append(attr).append(" > :").append(paramName);
                    params.add(fc.value());
                }
                case GTE -> {
                    sb.append(entityAlias).append(".").append(attr).append(" >= :").append(paramName);
                    params.add(fc.value());
                }
                case LT -> {
                    sb.append(entityAlias).append(".").append(attr).append(" < :").append(paramName);
                    params.add(fc.value());
                }
                case LTE -> {
                    sb.append(entityAlias).append(".").append(attr).append(" <= :").append(paramName);
                    params.add(fc.value());
                }
                case IN -> {
                    sb.append(entityAlias).append(".").append(attr).append(" IN :").append(paramName);
                    params.add(fc.value());
                }
                default -> {
                    /* skip */
                }
            }
        }
    }

    private long countWithWhere(String entityName, String where, List<Object> params) {
        String jpql = "SELECT COUNT(e) FROM " + entityName + " e" + where;
        var q = entityManager.createQuery(jpql, Long.class);
        for (int i = 0; i < params.size(); i++) {
            q.setParameter("p" + i, params.get(i));
        }
        return q.getSingleResult();
    }

    private Sort resolveSort(List<SortOrder> sortOrders, Sort pageableSort) {
        if (sortOrders != null && !sortOrders.isEmpty()) {
            List<Sort.Order> orders = new ArrayList<>();
            for (SortOrder so : sortOrders) {
                if (so != null && so.field() != null && !so.field().isBlank()) {
                    orders.add("desc".equalsIgnoreCase(so.dir())
                            ? Sort.Order.desc(so.field())
                            : Sort.Order.asc(so.field()));
                }
            }
            return orders.isEmpty() ? pageableSort : Sort.by(orders);
        }
        return pageableSort;
    }

    private String buildOrderBy(Sort sort, String entityAlias, Class<?> entityClass) {
        if (sort == null || !sort.isSorted()) {
            return "";
        }
        java.util.Set<String> allowedFields = getEntityFieldNames(entityClass);
        List<String> parts = new ArrayList<>();
        for (Sort.Order o : sort) {
            if (!isValidFieldName(o.getProperty(), allowedFields)) {
                throw new IllegalArgumentException("Invalid sort field: " + o.getProperty());
            }
            parts.add(entityAlias + "." + o.getProperty() + " " + (o.isDescending() ? "DESC" : "ASC"));
        }
        return " ORDER BY " + String.join(", ", parts);
    }

    @Override
    @Transactional(readOnly = true)
    public Object get(EntityMeta entityMeta, Object id) {
        Class<?> entityClass = entityMeta.getEntityClass();
        if (entityClass == null) {
            throw new IllegalStateException("Entity class not set for " + entityMeta.getEntityName());
        }
        Object entity = entityManager.find(entityClass, id);
        if (entity == null) {
            throw new IllegalArgumentException("Entity not found with id: " + id);
        }
        return entity;
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
    public Object update(EntityMeta entityMeta, Object id, Object body) {
        Class<?> entityClass = entityMeta.getEntityClass();
        if (entityClass == null) {
            throw new IllegalStateException("Entity class not set for " + entityMeta.getEntityName());
        }
        Object entity = entityManager.find(entityClass, id);
        if (entity == null) {
            throw new IllegalArgumentException("Entity not found with id: " + id);
        }
        entity = objectMapper.convertValue(body, entityClass);
        setEntityId(entity, id);
        return entityManager.merge(entity);
    }

    @Override
    @Transactional
    public void delete(EntityMeta entityMeta, Object id) {
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }
        Class<?> entityClass = entityMeta.getEntityClass();
        if (entityClass == null) {
            throw new IllegalStateException("Entity class not set for " + entityMeta.getEntityName());
        }
        Object entity = entityManager.find(entityClass, id);
        if (entity != null) {
            entityManager.remove(entity);
        }
    }

    @Override
    @Transactional
    public void deleteBatch(EntityMeta entityMeta, List<?> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("IDs cannot be null or empty");
        }
        for (Object id : ids) {
            if (id == null) {
                throw new IllegalArgumentException("批量删除项缺少 id");
            }
        }
        Class<?> entityClass = entityMeta.getEntityClass();
        if (entityClass == null) {
            throw new IllegalStateException("Entity class not set for " + entityMeta.getEntityName());
        }
        String entityName = entityClass.getSimpleName();
        entityManager.createQuery("DELETE FROM " + entityName + " e WHERE e.id IN :ids")
                .setParameter("ids", ids)
                .executeUpdate();
    }

    @Override
    @Transactional
    public List<?> updateBatch(EntityMeta entityMeta, List<Object> entities) {
        if (entities == null || entities.isEmpty()) {
            throw new IllegalArgumentException("Entities cannot be null or empty");
        }
        for (Object entity : entities) {
            if (getEntityId(entity) == null) {
                throw new IllegalArgumentException("批量更新项缺少 id");
            }
        }
        Class<?> entityClass = entityMeta.getEntityClass();
        if (entityClass == null) {
            throw new IllegalStateException("Entity class not set for " + entityMeta.getEntityName());
        }
        List<Object> result = new java.util.ArrayList<>(entities.size());
        int i = 0;
        for (Object entity : entities) {
            Object id = getEntityId(entity);
            if (id != null && entityManager.find(entityClass, id) != null) {
                result.add(entityManager.merge(entity));
            }
            if (++i % 50 == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
        return result;
    }

    private Object getEntityId(Object entity) {
        return getValueOfObject(entity, findIdField(entity.getClass()));
    }

    private void setEntityId(Object entity, Object id) {
        InMemoryEntityCrudService.setValueOfObject(entity, id, findIdField(entity.getClass()));
    }

    private static Field findIdField(Class<?> clazz) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField("id");
            } catch (NoSuchFieldException ignored) {
                // continue
            }
        }
        return null;
    }
}
