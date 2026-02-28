package com.lrenyi.template.dataforge.jpa.service;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrenyi.template.dataforge.jpa.support.FilterConditionSpecification;
import com.lrenyi.template.dataforge.meta.EntityMeta;
import com.lrenyi.template.dataforge.service.EntityCrudService;
import com.lrenyi.template.dataforge.service.InMemoryEntityCrudService;
import com.lrenyi.template.dataforge.support.ListCriteria;
import com.lrenyi.template.dataforge.support.SortOrder;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于 JpaRepository + JpaSpecificationExecutor 的通用 CRUD 实现。
 * 使用 SimpleJpaRepository 动态创建仓库，FilterCondition 转为 Specification 构建动态查询。
 * 实体类需为 JPA @Entity，主键类型可为 Long、String、UUID 等。
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
        ListCriteria c = criteria != null ? criteria : ListCriteria.empty();
        Set<String> allowedFields = getEntityFieldNames(entityClass);
        Specification<Object> spec = FilterConditionSpecification.from(c.getFilters(), allowedFields);
        Sort sort = resolveSort(c.getSortOrders(), pageable.getSort());
        Pageable resolvedPageable =
                sort.isSorted() ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort) : pageable;
        SimpleJpaRepository<Object, Object> repo = repositoryFor(entityClass);
        return repo.findAll(spec, resolvedPageable);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Object get(EntityMeta entityMeta, Object id) {
        Class<?> entityClass = entityMeta.getEntityClass();
        if (entityClass == null) {
            throw new IllegalStateException("Entity class not set for " + entityMeta.getEntityName());
        }
        Object entity = repositoryFor(entityClass).findById(id).orElse(null);
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
        return repositoryFor(entityClass).save(entity);
    }
    
    @Override
    @Transactional
    public Object update(EntityMeta entityMeta, Object id, Object body) {
        Class<?> entityClass = entityMeta.getEntityClass();
        if (entityClass == null) {
            throw new IllegalStateException("Entity class not set for " + entityMeta.getEntityName());
        }
        SimpleJpaRepository<Object, Object> repo = repositoryFor(entityClass);
        Object existing = repo.findById(id).orElse(null);
        if (existing == null) {
            throw new IllegalArgumentException("Entity not found with id: " + id);
        }
        Object entity = objectMapper.convertValue(body, entityClass);
        setEntityId(entity, id);
        return repo.save(entity);
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
        SimpleJpaRepository<Object, Object> repo = repositoryFor(entityClass);
        repo.deleteById(id);
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
        SimpleJpaRepository<Object, Object> repo = repositoryFor(entityClass);
        repo.deleteAllById(ids);
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
        SimpleJpaRepository<Object, Object> repo = repositoryFor(entityClass);
        List<Object> toSave = new ArrayList<>();
        for (Object entity : entities) {
            Object id = getEntityId(entity);
            if (id != null && repo.existsById(id)) {
                toSave.add(entity);
            }
        }
        return new ArrayList<>(repo.saveAll(toSave));
    }
    
    private Object getEntityId(Object entity) {
        return InMemoryEntityCrudService.getValueOfObject(entity, findIdField(entity.getClass()));
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
    
    private static Set<String> getEntityFieldNames(Class<?> entityClass) {
        Set<String> names = new HashSet<>();
        for (Class<?> c = entityClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                names.add(f.getName());
            }
        }
        return names;
    }
    
    private Sort resolveSort(List<SortOrder> sortOrders, Sort pageableSort) {
        if (sortOrders != null && !sortOrders.isEmpty()) {
            List<Sort.Order> orders = new ArrayList<>();
            for (SortOrder so : sortOrders) {
                if (so != null && so.field() != null && !so.field().isBlank()) {
                    orders.add("desc".equalsIgnoreCase(so.dir()) ? Sort.Order.desc(so.field()) :
                                       Sort.Order.asc(so.field()));
                }
            }
            return orders.isEmpty() ? pageableSort : Sort.by(orders);
        }
        return pageableSort;
    }
    
    @SuppressWarnings("unchecked")
    private SimpleJpaRepository<Object, Object> repositoryFor(Class<?> entityClass) {
        return new SimpleJpaRepository<>((Class<Object>) entityClass, entityManager);
    }
}
