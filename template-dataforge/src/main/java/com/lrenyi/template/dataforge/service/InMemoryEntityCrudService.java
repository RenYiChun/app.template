package com.lrenyi.template.dataforge.service;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrenyi.template.dataforge.meta.EntityMeta;
import com.lrenyi.template.dataforge.support.FilterCondition;
import com.lrenyi.template.dataforge.support.ListCriteria;
import com.lrenyi.template.dataforge.support.Op;
import com.lrenyi.template.dataforge.support.SortOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 内存版 CRUD 实现，用于演示与测试。不持久化。支持 Long、String、UUID 主键。
 */
public class InMemoryEntityCrudService implements EntityCrudService {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Map<Object, Object>> store = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> longIdGen = new ConcurrentHashMap<>();
    
    private static Field findField(Class<?> clazz, String fieldName) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                // continue
            }
        }
        return null;
    }
    
    @Override
    public Page<Object> list(EntityMeta entityMeta, Pageable pageable, ListCriteria criteria) {
        String path = entityMeta.getPathSegment();
        Map<Object, Object> map = store.get(path);
        if (map == null) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        List<Object> list = new ArrayList<>(map.values());
        ListCriteria c = criteria != null ? criteria : ListCriteria.empty();
        list = filterList(list, c.getFilters(), entityMeta);
        list = sortList(list, c.getSortOrders(), pageable.getSort(), entityMeta);
        long total = list.size();
        int from = (int) pageable.getOffset();
        int to = Math.min(from + pageable.getPageSize(), list.size());
        List<Object> content = from >= list.size() ? List.of() : list.subList(from, to);
        return new PageImpl<>(new ArrayList<>(content), pageable, total);
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
        setEntityId(entity, id, entityMeta);
        store.computeIfAbsent(entityMeta.getPathSegment(), k -> new ConcurrentHashMap<>()).put(id, entity);
        return entity;
    }
    
    @Override
    public Object update(EntityMeta entityMeta, Object id, Object body) {
        Map<Object, Object> map = store.get(entityMeta.getPathSegment());
        if (map == null) {
            return null;
        }
        Object entity = objectMapper.convertValue(body, entityMeta.getEntityClass());
        setEntityId(entity, id, entityMeta);
        return map.computeIfPresent(id, (k, v) -> entity);
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
    public List<Object> updateBatch(EntityMeta entityMeta, List<Object> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        Map<Object, Object> map = store.get(entityMeta.getPathSegment());
        if (map == null) {
            return List.of();
        }
        List<Object> result = new ArrayList<>(entities.size());
        for (Object entity : entities) {
            Object id = getEntityId(entity, entityMeta);
            if (id != null) {
                Object updated = map.computeIfPresent(id, (k, v) -> {
                                                          setEntityId(entity, id, entityMeta);
                                                          return entity;
                                                      }
                );
                if (updated != null) {
                    result.add(entity);
                }
            }
        }
        return result;
    }
    
    private Object getEntityId(Object entity, EntityMeta meta) {
        if (meta != null && meta.getAccessor() != null) {
            return meta.getAccessor().get(entity, "id");
        }
        return getValueOfObject(entity, findIdField(entity.getClass()));
    }
    
    public static Object getValueOfObject(Object entity, Field idField2) {
        try {
            if (idField2 == null) {
                return null;
            }
            idField2.setAccessible(true); //NOSONAR
            return idField2.get(entity);
        } catch (IllegalAccessException e) {
            return null;
        }
    }
    
    private Object nextId(String pathSegment, Class<?> primaryKeyType) {
        if (primaryKeyType == null || primaryKeyType == void.class || primaryKeyType == Long.class
                || primaryKeyType == long.class) {
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
    
    private void setEntityId(Object entity, Object id, EntityMeta meta) {
        if (meta != null && meta.getAccessor() != null) {
            meta.getAccessor().set(entity, "id", id);
            return;
        }
        setValueOfObject(entity, id, findIdField(entity.getClass()));
    }
    
    public static void setValueOfObject(Object entity, Object id, Field idField2) {
        if (entity == null || id == null) {
            return;
        }
        try {
            if (idField2 != null) {
                idField2.setAccessible(true); //NOSONAR
                idField2.set(entity, id);   //NOSONAR
            }
        } catch (IllegalAccessException e) {
            // ignore
        }
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
    
    private List<Object> filterList(List<Object> list, List<FilterCondition> filters, EntityMeta meta) {
        if (filters == null || filters.isEmpty()) {
            return list;
        }
        return list.stream().filter(e -> matchesAll(e, filters, meta)).toList();
    }
    
    private boolean matchesAll(Object entity, List<FilterCondition> filters, EntityMeta meta) {
        for (FilterCondition fc : filters) {
            if (fc == null || fc.op() == null) {
                continue;
            }
            Object fieldVal = getFieldValue(entity, fc.field(), meta);
            if (!matches(fc.op(), fieldVal, fc.value())) {
                return false;
            }
        }
        return true;
    }
    
    private boolean matches(Op op, Object fieldVal, Object expected) {
        if (op == Op.IN) {
            if (!(expected instanceof List<?> inList)) {
                return false;
            }
            return inList.contains(fieldVal);
        }
        int cmp = compareForOp(fieldVal, expected);
        return switch (op) {
            case EQ -> cmp == 0;
            case NE -> cmp != 0;
            case GT -> cmp > 0;
            case GTE -> cmp >= 0;
            case LT -> cmp < 0;
            case LTE -> cmp <= 0;
            case LIKE -> fieldVal != null && expected instanceof String pattern && fieldVal.toString()
                                                                                           .toLowerCase()
                                                                                           .contains(pattern.toLowerCase());
            default -> false;
        };
    }
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    private int compareForOp(Object a, Object b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        if (a instanceof Comparable c) {
            return c.compareTo(b);
        }
        return a.toString().compareTo(b.toString());
    }
    
    private Object getFieldValue(Object entity, String fieldName, EntityMeta meta) {
        if (entity == null || fieldName == null) {
            return null;
        }
        if (meta != null && meta.getAccessor() != null) {
            try {
                return meta.getAccessor().get(entity, fieldName);
            } catch (Exception e) {
                // ignore
                return null;
            }
        }
        try {
            Field f = findField(entity.getClass(), fieldName);
            if (f == null) {
                return null;
            }
            f.setAccessible(true); //NOSONAR
            return f.get(entity);
        } catch (IllegalAccessException e) {
            return null;
        }
    }
    
    private List<Object> sortList(List<Object> list,
            List<SortOrder> sortOrders,
            org.springframework.data.domain.Sort pageableSort,
            EntityMeta meta) {
        if (sortOrders != null && !sortOrders.isEmpty()) {
            Comparator<Object> comp = getObjectComparator(sortOrders, meta);
            if (comp != null) {
                return list.stream().sorted(comp).toList();
            }
        }
        if (pageableSort != null && pageableSort.isSorted()) {
            Comparator<Object> comp = getComparator(pageableSort, meta);
            if (comp != null) {
                return list.stream().sorted(comp).toList();
            }
        }
        return list;
    }
    
    private Comparator<Object> getComparator(Sort pageableSort, EntityMeta meta) {
        Comparator<Object> comp = null;
        for (Sort.Order o : pageableSort) {
            boolean desc = o.isDescending();
            Comparator<Object> c = Comparator.comparing(e -> getFieldValue(e, o.getProperty(), meta), (a, b) -> {
                                                            int r = compareForOp(a, b);
                                                            return desc ? -r : r;
                                                        }
            );
            comp = comp == null ? c : comp.thenComparing(c);
        }
        return comp;
    }
    
    private Comparator<Object> getObjectComparator(List<SortOrder> sortOrders, EntityMeta meta) {
        Comparator<Object> comp = null;
        for (SortOrder so : sortOrders) {
            if (so == null || so.field() == null) {
                continue;
            }
            boolean desc = "desc".equalsIgnoreCase(so.dir());
            Comparator<Object> c = Comparator.comparing(e -> getFieldValue(e, so.field(), meta), (a, b) -> {
                                                            int r = compareForOp(a, b);
                                                            return desc ? -r : r;
                                                        }
            );
            comp = comp == null ? c : comp.thenComparing(c);
        }
        return comp;
    }
}
