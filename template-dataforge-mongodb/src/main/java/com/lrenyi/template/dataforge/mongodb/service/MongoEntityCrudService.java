package com.lrenyi.template.dataforge.mongodb.service;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrenyi.template.dataforge.annotation.StorageTypes;
import com.lrenyi.template.dataforge.domain.DataforgePersistable;
import com.lrenyi.template.dataforge.meta.EntityMeta;
import com.lrenyi.template.dataforge.meta.FieldMeta;
import com.lrenyi.template.dataforge.registry.EntityRegistry;
import com.lrenyi.template.dataforge.service.InMemoryEntityCrudService;
import com.lrenyi.template.dataforge.service.StorageTypeAwareCrudService;
import com.lrenyi.template.dataforge.support.FilterCondition;
import com.lrenyi.template.dataforge.support.ListCriteria;
import com.lrenyi.template.dataforge.support.SortOrder;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.LookupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.bson.Document;

/**
 * 基于 MongoTemplate 的通用 CRUD 实现。实体需继承 MongoBaseDocument，主键类型可为 String、Long、ObjectId。
 * 支持软删除、乐观锁、事务。当 classpath 存在 spring-boot-starter-data-mongodb 时自动注册为 StorageTypeAwareCrudService。
 */
public class MongoEntityCrudService implements StorageTypeAwareCrudService {
    
    private static final String FIELD_UPDATE_TIME = "updateTime";
    private static final String FIELD_VERSION = "version";
    private static final String FIELD_DELETED = "deleted";
    private static final String FIELD_CREATE_TIME = "createTime";
    private static final String FIELD_CREATE_BY = "createBy";
    private static final String FIELD_ID = "id";
    private static final String FIELD_DELETE_TIME = "deleteTime";
    private static final String MONGO_ID = "_id";
    
    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;
    private final EntityRegistry entityRegistry;

    public MongoEntityCrudService(MongoTemplate mongoTemplate, ObjectMapper objectMapper) {
        this(mongoTemplate, objectMapper, null);
    }

    public MongoEntityCrudService(MongoTemplate mongoTemplate, ObjectMapper objectMapper, EntityRegistry entityRegistry) {
        this.mongoTemplate = mongoTemplate;
        this.objectMapper = objectMapper;
        this.entityRegistry = entityRegistry;
    }
    
    @Override
    public String getStorageType() {
        return StorageTypes.MONGO;
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<Object> list(EntityMeta entityMeta, Pageable pageable, ListCriteria criteria) {
        Class<Object> entityClass = (Class<Object>) entityMeta.getEntityClass();
        if (entityClass == null) {
            throw new IllegalStateException("Entity class not set for " + entityMeta.getEntityName());
        }
        ListCriteria c = criteria != null ? criteria : ListCriteria.empty();
        List<FieldMeta> foreignKeyFields = entityMeta.getFields() == null ? List.of() : entityMeta.getFields().stream()
                .filter(FieldMeta::isForeignKey)
                .filter(f -> StringUtils.hasText(f.getReferencedEntity()) && entityRegistry != null
                        && entityRegistry.getByEntityName(f.getReferencedEntity().trim()) != null)
                .toList();
        if (foreignKeyFields.isEmpty()) {
            return listSimple(entityMeta, entityClass, pageable, c);
        }
        return listWithLookup(entityMeta, entityClass, pageable, c, foreignKeyFields);
    }

    private Page<Object> listSimple(EntityMeta entityMeta, Class<Object> entityClass, Pageable pageable, ListCriteria c) {
        String collection = entityMeta.getTableName();
        Query query = buildQuery(entityClass, c.getFilters());
        addSoftDeleteCriteria(query);
        long total = mongoTemplate.count(query, entityClass, collection);
        Sort sort = resolveSort(c.getSortOrders(), pageable.getSort());
        query.with(sort);
        if (pageable.isPaged()) {
            query.skip(pageable.getOffset());
            query.limit(pageable.getPageSize());
        }
        List<Object> content = mongoTemplate.find(query, entityClass, collection);
        return new PageImpl<>(content, pageable.isPaged() ? pageable : Pageable.unpaged(), total);
    }

    private Page<Object> listWithLookup(EntityMeta entityMeta, Class<Object> entityClass, Pageable pageable,
            ListCriteria c, List<FieldMeta> foreignKeyFields) {
        String collection = entityMeta.getTableName();
        Set<String> allowedFields = getEntityFieldNames(entityClass);
        List<Criteria> matchCriteriaList = new ArrayList<>();
        matchCriteriaList.add(Criteria.where(FIELD_DELETED).ne(true));
        for (FilterCondition fc : c.getFilters()) {
            if (isUsableFilter(fc, allowedFields)) {
                matchCriteriaList.add(toCriteria(fc));
            }
        }
        MatchOperation match = Aggregation.match(new Criteria().andOperator(matchCriteriaList.toArray(Criteria[]::new)));
        Query countQuery = buildQuery(entityClass, c.getFilters());
        addSoftDeleteCriteria(countQuery);
        long total = mongoTemplate.count(countQuery, entityClass, collection);

        List<org.springframework.data.mongodb.core.aggregation.AggregationOperation> ops = new ArrayList<>();
        ops.add(match);
        for (FieldMeta field : foreignKeyFields) {
            EntityMeta refMeta = entityRegistry.getByEntityName(field.getReferencedEntity().trim());
            if (refMeta == null) {
                continue;
            }
            String refCollection = refMeta.getTableName();
            String localField = StringUtils.hasText(field.getColumnName()) ? field.getColumnName() : field.getName();
            String asField = field.getName() + "_obj";
            ops.add(LookupOperation.newLookup().from(refCollection).localField(localField).foreignField("_id").as(asField));
            ops.add(Aggregation.unwind(asField, true));
        }
        Set<String> includeFields = getEntityFieldNames(entityClass);
        ProjectionOperation project = Aggregation.project(includeFields.toArray(new String[0]));
        for (FieldMeta fk : foreignKeyFields) {
            String disp = StringUtils.hasText(fk.getDisplayField()) ? fk.getDisplayField() : "name";
            project = project.and(fk.getName() + "_obj." + disp).as(fk.getName() + "_display");
        }
        ops.add(project);
        Sort sort = resolveSort(c.getSortOrders(), pageable.getSort());
        if (sort.isSorted()) {
            ops.add(Aggregation.sort(org.springframework.data.domain.Sort.by(sort.stream()
                    .map(o -> new org.springframework.data.domain.Sort.Order(
                            o.isAscending() ? org.springframework.data.domain.Sort.Direction.ASC
                                    : org.springframework.data.domain.Sort.Direction.DESC,
                            o.getProperty()))
                    .toList())));
        }
        if (pageable.isPaged()) {
            ops.add(Aggregation.skip(pageable.getOffset()));
            ops.add(Aggregation.limit(pageable.getPageSize()));
        }
        Aggregation aggregation = Aggregation.newAggregation(ops);
        List<Object> content = mongoTemplate.aggregate(aggregation, collection, Document.class).getMappedResults()
                .stream().map(doc -> (Object) new java.util.LinkedHashMap<String, Object>(doc)).toList();
        return new PageImpl<>(content, pageable.isPaged() ? pageable : Pageable.unpaged(), total);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Object get(EntityMeta entityMeta, Object id) {
        Class<Object> entityClass = (Class<Object>) entityMeta.getEntityClass();
        if (entityClass == null) {
            throw new IllegalStateException("Entity class not set for " + entityMeta.getEntityName());
        }
        Object resolvedId = resolveId(id, entityMeta.getPrimaryKeyType());
        Query query = new Query(Criteria.where(MONGO_ID).is(resolvedId));
        addSoftDeleteCriteria(query);
        return mongoTemplate.findOne(query, entityClass, entityMeta.getTableName());
    }
    
    @Override
    @Transactional
    public Object create(EntityMeta entityMeta, Object body) {
        Class<?> entityClass = entityMeta.getEntityClass();
        if (entityClass == null) {
            throw new IllegalStateException("Entity class not set for " + entityMeta.getEntityName());
        }
        Object entity = objectMapper.convertValue(body, entityClass);
        LocalDateTime now = LocalDateTime.now();
        Object id = InMemoryEntityCrudService.getValueOfObject(entity, findIdField(entityClass));
        if (id == null && entityMeta.getPrimaryKeyType() == String.class) {
            setIfPersistable(entity, FIELD_ID, new ObjectId().toHexString());
        } else if (id == null && entityMeta.getPrimaryKeyType() == ObjectId.class) {
            setIfPersistable(entity, FIELD_ID, new ObjectId());
        }
        setIfPersistable(entity, FIELD_CREATE_TIME, now);
        setIfPersistable(entity, FIELD_UPDATE_TIME, now);
        setIfPersistable(entity, FIELD_VERSION, 0L);
        setIfPersistable(entity, FIELD_DELETED, false);
        mongoTemplate.insert(entity, entityMeta.getTableName());
        return entity;
    }
    
    @Override
    @Transactional
    public Object update(EntityMeta entityMeta, Object id, Object body) {
        Class<?> entityClass = entityMeta.getEntityClass();
        if (entityClass == null) {
            throw new IllegalStateException("Entity class not set for " + entityMeta.getEntityName());
        }
        Object resolvedId = resolveId(id, entityMeta.getPrimaryKeyType());
        Query query = new Query(Criteria.where(MONGO_ID).is(resolvedId));
        addSoftDeleteCriteria(query);
        Object existing = mongoTemplate.findOne(query, entityClass, entityMeta.getTableName());
        if (existing == null) {
            return null;
        }
        Long existingVersion = getVersion(existing);
        Object entity = objectMapper.convertValue(body, entityClass);
        Long requestVersion = getVersion(entity);
        mergeNonNullFields(entity, existing, entityClass);
        setIfPersistable(existing, FIELD_ID, resolvedId);
        setIfPersistable(existing, FIELD_UPDATE_TIME, LocalDateTime.now());
        boolean lockEnabled = entityMeta.isEnableVersionControl() && existingVersion != null;
        if (lockEnabled && (requestVersion == null || !requestVersion.equals(existingVersion))) {
            throw new IllegalStateException("Optimistic lock conflict: version mismatch for id " + id);
        }
        if (lockEnabled) {
            setIfPersistable(existing, FIELD_VERSION, existingVersion + 1);
        }
        setIfPersistable(existing, FIELD_DELETED, getDeleted(existing));
        setIfPersistable(existing, FIELD_CREATE_TIME, getCreateTime(existing));
        setIfPersistable(existing, FIELD_CREATE_BY, getCreateBy(existing));
        mongoTemplate.save(existing, entityMeta.getTableName());
        return existing;
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
        Object resolvedId = resolveId(id, entityMeta.getPrimaryKeyType());
        String collection = entityMeta.getTableName();
        if (entityMeta.isSoftDelete()) {
            Query query = new Query(Criteria.where(MONGO_ID).is(resolvedId));
            Update update = new Update().set(FIELD_DELETED, true)
                                        .set(FIELD_UPDATE_TIME, LocalDateTime.now())
                                        .set(FIELD_DELETE_TIME, LocalDateTime.now());
            if (entityMeta.getUpdateUserField() != null && !entityMeta.getUpdateUserField().isBlank()) {
                update.set(entityMeta.getUpdateUserField(), getCurrentUser());
            }
            mongoTemplate.updateFirst(query, update, entityClass, collection);
        } else {
            mongoTemplate.remove(new Query(Criteria.where(MONGO_ID).is(resolvedId)), entityClass, collection);
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
        List<Object> resolvedIds = ids.stream().map(id -> resolveId(id, entityMeta.getPrimaryKeyType())).toList();
        String collection = entityMeta.getTableName();
        if (entityMeta.isSoftDelete()) {
            Query query = new Query(Criteria.where(MONGO_ID).in(resolvedIds));
            Update update = new Update().set(FIELD_DELETED, true)
                                        .set(FIELD_UPDATE_TIME, LocalDateTime.now())
                                        .set(FIELD_DELETE_TIME, LocalDateTime.now());
            if (entityMeta.getUpdateUserField() != null && !entityMeta.getUpdateUserField().isBlank()) {
                update.set(entityMeta.getUpdateUserField(), getCurrentUser());
            }
            mongoTemplate.updateMulti(query, update, entityClass, collection);
        } else {
            mongoTemplate.remove(new Query(Criteria.where(MONGO_ID).in(resolvedIds)), entityClass, collection);
        }
    }
    
    @Override
    @Transactional
    public List<Object> updateBatch(EntityMeta entityMeta, List<Object> entities) {
        if (entities == null || entities.isEmpty()) {
            throw new IllegalArgumentException("Entities cannot be null or empty");
        }
        for (Object entity : entities) {
            if (InMemoryEntityCrudService.getValueOfObject(entity, findIdField(entity.getClass())) == null) {
                throw new IllegalArgumentException("批量更新项缺少 id");
            }
        }
        Class<?> entityClass = entityMeta.getEntityClass();
        if (entityClass == null) {
            throw new IllegalStateException("Entity class not set for " + entityMeta.getEntityName());
        }
        String collection = entityMeta.getTableName();
        List<Object> result = new ArrayList<>(entities.size());
        LocalDateTime now = LocalDateTime.now();
        for (Object entity : entities) {
            Object id = InMemoryEntityCrudService.getValueOfObject(entity, findIdField(entity.getClass()));
            Object resolvedId = resolveId(id, entityMeta.getPrimaryKeyType());
            Query query = new Query(Criteria.where(MONGO_ID).is(resolvedId));
            addSoftDeleteCriteria(query);
            Object existing = mongoTemplate.findOne(query, entityClass, collection);
            if (existing == null) {
                continue;
            }
            Object patch = entity;
            Long requestVersion = getVersion(patch);
            mergeNonNullFields(patch, existing, entityClass);
            applyBatchUpdateFields(entityMeta, existing, existing, now, id, requestVersion);
            mongoTemplate.save(existing, collection);
            result.add(existing);
        }
        return result;
    }

    private void applyBatchUpdateFields(EntityMeta entityMeta,
            Object entity,
            Object existing,
            LocalDateTime now,
            Object id,
            Long requestVersion) {
        if (entityMeta.isEnableVersionControl()) {
            Long existingVersion = getVersion(existing);
            if (existingVersion != null && (requestVersion == null || !requestVersion.equals(existingVersion))) {
                throw new IllegalStateException("Optimistic lock conflict: version mismatch for id " + id);
            }
            setIfPersistable(entity, FIELD_VERSION, existingVersion != null ? existingVersion + 1 : 1L);
        }
        setIfPersistable(entity, FIELD_UPDATE_TIME, now);
        setIfPersistable(entity, FIELD_DELETED, getDeleted(existing));
        setIfPersistable(entity, FIELD_CREATE_TIME, getCreateTime(existing));
        setIfPersistable(entity, FIELD_CREATE_BY, getCreateBy(existing));
    }
    
    private String getCurrentUser() {
        try {
            var ctx = org.springframework.security.core.context.SecurityContextHolder.getContext();
            var auth = ctx != null ? ctx.getAuthentication() : null;
            return auth != null && auth.isAuthenticated() ? auth.getName() : null;
        } catch (Exception ignored) {
            return null;
        }
    }
    
    private Long getVersion(Object entity) {
        if (entity instanceof DataforgePersistable<?> p) {
            return p.getVersion();
        }
        return null;
    }
    
    private Boolean getDeleted(Object entity) {
        if (entity instanceof DataforgePersistable<?> p) {
            return p.getDeleted();
        }
        return false;
    }
    
    private LocalDateTime getCreateTime(Object entity) {
        if (entity instanceof DataforgePersistable<?> p) {
            return p.getCreateTime();
        }
        return null;
    }
    
    private String getCreateBy(Object entity) {
        if (entity instanceof DataforgePersistable<?> p) {
            return p.getCreateBy();
        }
        return null;
    }
    
    private static Field findIdField(Class<?> clazz) {
        return findField(clazz, FIELD_ID);
    }

    private static void mergeNonNullFields(Object source, Object target, Class<?> entityClass) {
        for (Class<?> c = entityClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (FIELD_ID.equals(f.getName()) || java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                try {
                    f.setAccessible(true); // NOSONAR
                    Object value = f.get(source);
                    if (value != null) {
                        f.set(target, value); // NOSONAR
                    }
                } catch (IllegalAccessException ignored) {
                    // skip
                }
            }
        }
    }
    
    private void setIfPersistable(Object entity, String fieldName, Object value) {
        Field f = findField(entity.getClass(), fieldName);
        if (f != null) {
            InMemoryEntityCrudService.setValueOfObject(entity, value, f);
        }
    }
    
    private static Field findField(Class<?> clazz, String name) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // continue
            }
        }
        return null;
    }
    
    private Object resolveId(Object id, Class<?> primaryKeyType) {
        if (id == null) {
            return null;
        }
        if (primaryKeyType == ObjectId.class && id instanceof String s && ObjectId.isValid(s)) {
            return new ObjectId(s);
        }
        return id;
    }
    
    private Query buildQuery(Class<?> entityClass, List<FilterCondition> filters) {
        Query query = new Query();
        Set<String> allowedFields = getEntityFieldNames(entityClass);
        if (filters == null || filters.isEmpty()) {
            return query;
        }
        List<Criteria> criteriaList = new ArrayList<>(filters.size());
        for (FilterCondition fc : filters) {
            if (!isUsableFilter(fc, allowedFields)) {
                continue;
            }
            criteriaList.add(toCriteria(fc));
        }
        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(Criteria[]::new)));
        }
        return query;
    }
    
    /** MongoBaseDocument 实体默认带 deleted 字段，list/get 时过滤已软删记录。 */
    private void addSoftDeleteCriteria(Query query) {
        query.addCriteria(Criteria.where(FIELD_DELETED).ne(true));
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
    
    private static Set<String> getEntityFieldNames(Class<?> entityClass) {
        Set<String> names = new java.util.HashSet<>();
        for (Class<?> c = entityClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                names.add(f.getName());
            }
        }
        return names;
    }
    
    private static boolean isUsableFilter(FilterCondition fc, Set<String> allowedFields) {
        return fc != null && fc.op() != null && isValidFieldName(fc.field(), allowedFields);
    }
    
    private static Criteria toCriteria(FilterCondition fc) {
        String field = fc.field();
        Object value = fc.value();
        return switch (fc.op()) {
            case EQ -> Criteria.where(field).is(value);
            case NE -> Criteria.where(field).ne(value);
            case LIKE -> Criteria.where(field)
                                 .regex(value instanceof String s ? ".*" + escapeRegex(s) + ".*" :
                                                String.valueOf(value), "i"
                                 );
            case GT -> Criteria.where(field).gt(value);
            case GTE -> Criteria.where(field).gte(value);
            case LT -> Criteria.where(field).lt(value);
            case LTE -> Criteria.where(field).lte(value);
            case IN -> Criteria.where(field).in(value instanceof List<?> l ? l : List.of(value));
        };
    }
    
    private static boolean isValidFieldName(String field, Set<String> allowedFields) {
        return field != null && !field.isBlank() && allowedFields.contains(field);
    }
    
    private static String escapeRegex(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace(".", "\\.")
                .replace("?", "\\?")
                .replace("+", "\\+")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("|", "\\|");
    }
}
