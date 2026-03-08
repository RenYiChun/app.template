# Dataforge 实体关联最优方案

> **相关文档**：[实施注意事项](entity-association-implementation-notes.md)、[API 接口规范](entity-association-api-specification.md)

## 设计原则

1. **存储内关联**：只支持同一存储类型内的实体关联，不支持跨存储关联
2. **元数据驱动**：通过注解统一声明，各存储类型按自身特点实现
3. **性能优先**：充分利用各存储类型的原生能力（JPA JOIN、MongoDB $lookup）
4. **简单实用**：避免过度设计，专注于 80% 的常见场景

## 一、核心架构

### 1.1 关联字段元数据

在 `@DataforgeField` 中声明关联关系（已实现）：

```java
@DataforgeField(
    foreignKey = true,              // 标记为关联字段
    referencedEntity = "Department", // 关联实体名称
    displayField = "name",           // 显示字段（用于下拉框和表格显示）
    valueField = "id",               // 值字段（通常是 id）
    component = FormComponent.SELECT // 表单组件类型
)
```

### 1.2 存储类型路由

框架通过 `EntityCrudServiceRouter` 自动路由到对应的存储实现：

```
请求 → EntityCrudServiceRouter →
    ├─ JpaEntityCrudService (storage = "jpa")
    ├─ MongoEntityCrudService (storage = "mongo")
    └─ 自定义存储实现 (storage = "custom")
```

## 二、JPA 存储实现（推荐方案）

### 2.1 实体定义

**方式 1：外键 ID（推荐，性能最优）**

```java
@Entity
@Table(name = "users")
@DataforgeEntity(pathSegment = "users", displayName = "用户", storage = StorageTypes.JPA)
public class User extends BaseEntity<Long> {

    @Column(nullable = false, length = 64)
    @DataforgeField(label = "用户名", searchable = true)
    private String username;

    @Column(name = "department_id")
    @DataforgeField(
        label = "部门",
        foreignKey = true,
        referencedEntity = "Department",
        displayField = "name",
        component = FormComponent.SELECT,
        searchable = true,
        searchComponent = SearchComponent.SELECT
    )
    private Long departmentId;
}

@Entity
@Table(name = "sys_department")
@DataforgeEntity(pathSegment = "departments", displayName = "部门", storage = StorageTypes.JPA)
public class Department extends BaseEntity<Long> {

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "parent_id")
    @DataforgeField(
        label = "上级部门",
        foreignKey = true,
        referencedEntity = "Department",  // 自关联
        displayField = "name",
        component = FormComponent.TREE_SELECT
    )
    private Long parentId;
}
```

**方式 2：JPA 关联（类型安全，适合复杂场景）**

```java
@Entity
@Table(name = "users")
@DataforgeEntity(pathSegment = "users", displayName = "用户", storage = StorageTypes.JPA)
public class User extends BaseEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    @DataforgeField(
        label = "部门",
        foreignKey = true,
        referencedEntity = "Department",
        displayField = "name",
        component = FormComponent.SELECT
    )
    private Department department;

    // 如果需要在 DTO 中返回 departmentId，可以添加：
    @Transient
    @DataforgeField(label = "部门ID", hidden = true)
    public Long getDepartmentId() {
        return department != null ? department.getId() : null;
    }
}
```

### 2.2 后端实现

#### 2.2.1 关联实体选项接口

在 `CrudController` 中添加：

```java
/**
 * 获取关联实体的选项列表（用于下拉框）
 * GET /api/dataforge/{entity}/options?query=研发&page=0&size=20
 */
@GetMapping("/{entity}/options")
public Result<Page<EntityOption>> getOptions(
    @PathVariable String entity,
    @RequestParam(required = false) String query,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size
) {
    EntityMeta meta = entityMetaRegistry.getByEntityName(entity);
    if (meta == null) {
        throw new IllegalArgumentException("实体不存在: " + entity);
    }

    // 获取 displayField（默认为 "name"）
    String displayField = getDisplayFieldFromMeta(meta);

    // 构建搜索条件
    ListCriteria criteria = ListCriteria.empty();
    if (query != null && !query.isBlank()) {
        criteria.addFilter(new FilterCondition(displayField, FilterOp.LIKE, query));
    }

    // 查询数据
    Pageable pageable = PageRequest.of(page, size);
    Page<Object> result = entityCrudService.list(meta, pageable, criteria);

    // 转换为选项格式
    Page<EntityOption> options = result.map(obj -> new EntityOption(
        extractId(obj),
        extractField(obj, displayField),
        obj  // 可选：返回完整对象供前端使用
    ));

    return Result.success(options);
}

// 选项 DTO
public record EntityOption(
    Object id,
    String label,
    Object data  // 可选：完整数据
) {}
```

#### 2.2.2 列表查询时自动关联（JPA 优化）

在 `JpaEntityCrudService` 中增强：

```java
@Override
public Page<Object> list(EntityMeta entityMeta, Pageable pageable, ListCriteria criteria) {
    // 检测是否有关联字段
    List<FieldMeta> foreignKeyFields = entityMeta.getFields().stream()
        .filter(FieldMeta::isForeignKey)
        .toList();

    if (foreignKeyFields.isEmpty()) {
        // 无关联字段，普通查询
        return listNormal(entityMeta, pageable, criteria);
    }

    // 有关联字段，使用 JOIN 查询
    return listWithJoin(entityMeta, pageable, criteria, foreignKeyFields);
}

private Page<Object> listWithJoin(EntityMeta entityMeta, Pageable pageable,
                                   ListCriteria criteria, List<FieldMeta> foreignKeyFields) {
    // 方案 A：使用 EntityGraph（推荐）
    EntityGraph<?> graph = entityManager.createEntityGraph(entityMeta.getEntityClass());
    for (FieldMeta field : foreignKeyFields) {
        if (isJpaAssociation(field)) {
            graph.addAttributeNodes(field.getName());
        }
    }

    // 方案 B：使用 JOIN FETCH（适合简单场景）
    // SELECT u FROM User u LEFT JOIN FETCH u.department WHERE ...

    // 执行查询
    TypedQuery<?> query = entityManager.createQuery(jpql, entityMeta.getEntityClass());
    query.setHint("javax.persistence.fetchgraph", graph);

    // ... 应用分页和条件

    return new PageImpl<>(query.getResultList(), pageable, count);
}
```

#### 2.2.3 DTO 转换时包含关联数据

```java
public class EntityDtoConverter {

    public Map<String, Object> toDto(Object entity, EntityMeta meta, DtoType dtoType) {
        Map<String, Object> dto = new HashMap<>();

        for (FieldMeta field : meta.getFields()) {
            if (!field.isIncludedInDto(dtoType)) continue;

            Object value = extractField(entity, field.getName());

            if (field.isForeignKey() && value != null) {
                // 关联字段特殊处理
                dto.put(field.getName(), value);  // 原始 ID

                // 如果是 JPA 关联对象，提取显示字段
                if (value instanceof BaseEntity) {
                    String displayValue = extractField(value, field.getDisplayField());
                    dto.put(field.getName() + "_display", displayValue);
                }
            } else {
                dto.put(field.getName(), value);
            }
        }

        return dto;
    }
}
```

### 2.3 前端实现

#### 2.3.1 EntityForm.vue - 关联选择器

```typescript
<template>
  <el-form-item :label="field.label" v-if="field.foreignKey">
    <!-- 普通下拉框 -->
    <el-select
      v-if="field.component === 'SELECT'"
      v-model="formData[field.name]"
      :placeholder="`请选择${field.label}`"
      filterable
      remote
      :remote-method="(query) => loadOptions(field, query)"
      :loading="loading[field.name]"
      clearable
    >
      <el-option
        v-for="item in options[field.name]"
        :key="item.id"
        :label="item.label"
        :value="item.id"
      />
    </el-select>

    <!-- 树形选择器（用于自关联） -->
    <el-tree-select
      v-else-if="field.component === 'TREE_SELECT'"
      v-model="formData[field.name]"
      :data="treeOptions[field.name]"
      :props="{ label: 'label', value: 'id', children: 'children' }"
      :placeholder="`请选择${field.label}`"
      clearable
    />
  </el-form-item>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '@/api'

const props = defineProps<{
  field: FieldMeta
  formData: Record<string, any>
}>()

const options = ref<Record<string, EntityOption[]>>({})
const treeOptions = ref<Record<string, TreeNode[]>>({})
const loading = ref<Record<string, boolean>>({})

// 加载关联实体选项
const loadOptions = async (field: FieldMeta, query?: string) => {
  loading.value[field.name] = true
  try {
    const response = await api.get(`/api/dataforge/${field.referencedEntity}/options`, {
      params: { query, size: 50 }
    })
    options.value[field.name] = response.data.content
  } finally {
    loading.value[field.name] = false
  }
}

// 加载树形选项（用于自关联）
const loadTreeOptions = async (field: FieldMeta) => {
  const response = await api.get(`/api/dataforge/${field.referencedEntity}/tree`)
  treeOptions.value[field.name] = response.data
}

onMounted(() => {
  if (props.field.foreignKey) {
    if (props.field.component === 'TREE_SELECT') {
      loadTreeOptions(props.field)
    } else {
      loadOptions(props.field)
    }
  }
})
</script>
```

#### 2.3.2 EntityTable.vue - 显示关联名称

```typescript
<template>
  <el-table-column
    v-if="field.foreignKey"
    :prop="field.name"
    :label="field.label"
    :width="field.columnWidth"
  >
    <template #default="{ row }">
      {{ getDisplayValue(row, field) }}
    </template>
  </el-table-column>
</template>

<script setup lang="ts">
// 方案 A：后端已返回 displayField（推荐）
const getDisplayValue = (row: any, field: FieldMeta) => {
  // 后端在 DTO 中已包含 {field}_display
  return row[`${field.name}_display`] || row[field.name]
}

// 方案 B：前端批量查询（适合后端未实现的情况）
const associationCache = ref<Record<string, Record<any, string>>>({})

const loadAssociations = async (rows: any[], field: FieldMeta) => {
  const ids = [...new Set(rows.map(row => row[field.name]).filter(Boolean))]
  if (ids.length === 0) return

  const response = await api.get(`/api/dataforge/${field.referencedEntity}/batch-lookup`, {
    params: { ids: ids.join(',') }
  })

  associationCache.value[field.name] = response.data
}

const getDisplayValue = (row: any, field: FieldMeta) => {
  const id = row[field.name]
  return associationCache.value[field.name]?.[id] || id
}
</script>
```

#### 2.3.3 EntitySearchBar.vue - 关联搜索

```typescript
<template>
  <el-form-item :label="field.label" v-if="field.searchable && field.foreignKey">
    <el-select
      v-model="searchForm[field.name]"
      :placeholder="`请选择${field.label}`"
      filterable
      remote
      :remote-method="(query) => loadOptions(field, query)"
      clearable
    >
      <el-option
        v-for="item in options[field.name]"
        :key="item.id"
        :label="item.label"
        :value="item.id"
      />
    </el-select>
  </el-form-item>
</template>
```

## 三、MongoDB 存储实现

### 3.1 实体定义

```java
@Document(collection = "users")
@DataforgeEntity(pathSegment = "users_mongo", displayName = "用户", storage = StorageTypes.MONGO)
public class UserMongo extends MongoBaseDocument<String> {

    private String username;

    @Field("department_id")
    @DataforgeField(
        label = "部门",
        foreignKey = true,
        referencedEntity = "DepartmentMongo",
        displayField = "name",
        component = FormComponent.SELECT
    )
    private String departmentId;  // MongoDB 使用 String 类型的 ObjectId
}

@Document(collection = "departments")
@DataforgeEntity(pathSegment = "departments_mongo", displayName = "部门", storage = StorageTypes.MONGO)
public class DepartmentMongo extends MongoBaseDocument<String> {
    private String name;
}
```

### 3.2 后端实现

#### 3.2.1 选项查询（与 JPA 相同）

MongoDB 的选项查询接口与 JPA 完全相同，通过 `EntityCrudServiceRouter` 自动路由。

#### 3.2.2 列表查询时关联（使用 $lookup）

```java
@Service
public class MongoEntityCrudService implements StorageTypeAwareCrudService {

    @Override
    public Page<Object> list(EntityMeta entityMeta, Pageable pageable, ListCriteria criteria) {
        List<FieldMeta> foreignKeyFields = entityMeta.getFields().stream()
            .filter(FieldMeta::isForeignKey)
            .toList();

        if (foreignKeyFields.isEmpty()) {
            return listNormal(entityMeta, pageable, criteria);
        }

        // 使用 $lookup 聚合查询
        return listWithLookup(entityMeta, pageable, criteria, foreignKeyFields);
    }

    private Page<Object> listWithLookup(EntityMeta entityMeta, Pageable pageable,
                                         ListCriteria criteria, List<FieldMeta> foreignKeyFields) {
        Aggregation aggregation = Aggregation.newAggregation(
            // 1. 匹配条件
            Aggregation.match(buildCriteria(criteria)),

            // 2. 关联查询（注意：使用 columnName 而非 name，MongoDB 中 @Field("department_id") 存储的字段名是 department_id）
            ...foreignKeyFields.stream()
                .map(field -> Aggregation.lookup(
                    getCollectionName(field.getReferencedEntity()),  // 关联集合
                    field.getColumnName() != null ? field.getColumnName() : field.getName(),  // 本地字段：优先用 columnName
                    "_id",                                            // 外部字段
                    field.getName() + "_obj"                          // 结果字段
                ))
                .toList(),

            // 3. 展开关联结果
            ...foreignKeyFields.stream()
                .map(field -> Aggregation.unwind(field.getName() + "_obj", true))
                .toList(),

            // 4. 投影（提取 displayField）
            Aggregation.project()
                .andInclude(getAllFields(entityMeta))
                .and(field -> field.getName() + "_obj." + field.getDisplayField())
                .as(field.getName() + "_display"),

            // 5. 分页
            Aggregation.skip(pageable.getOffset()),
            Aggregation.limit(pageable.getPageSize())
        );

        AggregationResults<Object> results = mongoTemplate.aggregate(
            aggregation,
            entityMeta.getTableName(),
            entityMeta.getEntityClass()
        );

        return new PageImpl<>(results.getMappedResults(), pageable, count);
    }
}
```

**注意：**
1. **字段名映射**：MongoDB 使用 `@Field("department_id")` 时，实际存储字段名为 `department_id`，而 `field.getName()` 返回 `departmentId`。$lookup 的 localField 应使用 `field.getColumnName()`（从 @Column/@Field 注解解析），详见 `entity-association-implementation-notes.md` 1.1 节。
2. **性能**：$lookup 性能较差，建议为关联字段创建索引、限制关联深度（不超过 2 层）、考虑数据冗余。

## 四、其他关联场景

### 4.1 一对多关联

```java
@Entity
@Table(name = "sys_department")
public class Department extends BaseEntity<Long> {

    private String name;

    // 一对多：部门的所有用户
    @OneToMany(mappedBy = "department", fetch = FetchType.LAZY)
    @DataforgeField(
        label = "部门成员",
        foreignKey = true,
        referencedEntity = "User",
        displayField = "username",
        component = FormComponent.TABLE,  // 使用表格组件
        readonly = true  // 只读，不允许在部门表单中直接编辑用户
    )
    private List<User> users;
}
```

**前端处理：**
- 在部门详情页显示用户列表（只读）
- 在用户管理页面分配部门（可编辑）

### 4.2 多对多关联（通过中间表）

```java
// 用户-角色多对多（推荐使用中间表实体）
@Entity
@Table(name = "sys_user_role")
@DataforgeEntity(pathSegment = "user_roles", displayName = "用户角色")
public class UserRole extends BaseEntity<Long> {

    @Column(nullable = false)
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    @DataforgeField(
        label = "角色",
        foreignKey = true,
        referencedEntity = "Role",
        displayField = "name"
    )
    private Role role;
}
```

### 4.3 自关联（树形结构）

```java
@Entity
@Table(name = "sys_department")
@DataforgeEntity(
    pathSegment = "departments",
    displayName = "部门",
    treeEntity = true,           // 标记为树形实体
    treeParentField = "parentId",
    treeNameField = "name"
)
public class Department extends BaseEntity<Long> {

    private String name;

    @Column(name = "parent_id")
    @DataforgeField(
        label = "上级部门",
        foreignKey = true,
        referencedEntity = "Department",  // 自关联
        displayField = "name",
        component = FormComponent.TREE_SELECT
    )
    private Long parentId;
}
```

**后端需要提供树形接口：**

```java
@GetMapping("/{entity}/tree")
public Result<List<TreeNode>> getTree(@PathVariable String entity) {
    EntityMeta meta = entityMetaRegistry.getByEntityName(entity);
    if (!meta.isTreeEntity()) {
        throw new IllegalArgumentException("实体不是树形结构: " + entity);
    }

    // 查询所有节点
    List<Object> allNodes = entityCrudService.list(meta, Pageable.unpaged(), null).getContent();

    // 构建树形结构
    return Result.success(buildTree(allNodes, meta));
}
```

## 五、性能优化策略

### 5.1 JPA 优化

```java
// 1. 使用 EntityGraph 避免 N+1
@EntityGraph(attributePaths = {"department", "roles"})
List<User> findAll();

// 2. 使用 JOIN FETCH
@Query("SELECT u FROM User u LEFT JOIN FETCH u.department WHERE u.status = :status")
List<User> findActiveUsersWithDepartment(@Param("status") String status);

// 3. 使用 DTO 投影（只查询需要的字段）
@Query("SELECT new com.example.UserDto(u.id, u.username, d.name) " +
       "FROM User u LEFT JOIN u.department d")
List<UserDto> findAllWithDepartmentName();
```

### 5.2 MongoDB 优化

```java
// 1. 为关联字段创建索引
@Indexed
@Field("department_id")
private String departmentId;

// 2. 数据冗余（空间换时间）
@Document(collection = "users")
public class UserMongo extends MongoBaseDocument<String> {
    @Field("department_id")
    private String departmentId;

    @Field("department_name")  // 冗余存储部门名称
    private String departmentName;
}

// 3. 应用层批量查询（避免 $lookup）
public Page<UserDto> listUsersWithDepartment(Pageable pageable) {
    // 查询用户
    Page<User> users = userRepository.findAll(pageable);

    // 收集部门 ID
    Set<String> deptIds = users.stream()
        .map(User::getDepartmentId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());

    // 批量查询部门
    Map<String, Department> deptMap = departmentRepository.findAllById(deptIds)
        .stream()
        .collect(Collectors.toMap(Department::getId, d -> d));

    // 组装 DTO
    return users.map(user -> new UserDto(
        user.getId(),
        user.getUsername(),
        deptMap.get(user.getDepartmentId())
    ));
}
```

### 5.3 前端缓存

```typescript
// 关联数据缓存（避免重复请求）
class AssociationCache {
  private cache = new Map<string, Map<any, EntityOption>>()
  private ttl = 5 * 60 * 1000  // 5 分钟过期

  async get(entity: string, id: any): Promise<EntityOption | null> {
    const entityCache = this.cache.get(entity)
    return entityCache?.get(id) || null
  }

  async batchGet(entity: string, ids: any[]): Promise<Map<any, EntityOption>> {
    const result = new Map<any, EntityOption>()
    const missingIds: any[] = []

    // 从缓存中获取
    const entityCache = this.cache.get(entity)
    for (const id of ids) {
      const cached = entityCache?.get(id)
      if (cached) {
        result.set(id, cached)
      } else {
        missingIds.push(id)
      }
    }

    // 批量查询缺失的数据
    if (missingIds.length > 0) {
      const response = await api.get(`/api/dataforge/${entity}/batch-lookup`, {
        params: { ids: missingIds.join(',') }
      })

      for (const [id, option] of Object.entries(response.data)) {
        result.set(id, option)
        this.set(entity, id, option)
      }
    }

    return result
  }

  set(entity: string, id: any, option: EntityOption) {
    if (!this.cache.has(entity)) {
      this.cache.set(entity, new Map())
    }
    this.cache.get(entity)!.set(id, option)
  }
}
```

## 六、实施清单

### 6.1 后端任务

- [ ] 在 `CrudController` 中添加 `/api/{entity}/options` 接口
- [ ] 在 `CrudController` 中添加 `/api/{entity}/batch-lookup` 接口
- [ ] 在 `CrudController` 中添加 `/api/{entity}/tree` 接口（树形实体）
- [ ] 在 `JpaEntityCrudService` 中实现 EntityGraph 优化
- [ ] 在 `MongoEntityCrudService` 中实现 $lookup 聚合查询
- [ ] 在 `EntityDtoConverter` 中添加关联字段的 `_display` 后缀处理
- [ ] 为关联字段添加数据验证（确保关联的 ID 存在）

### 6.2 前端任务

- [ ] 在 `EntityForm.vue` 中实现关联选择器（SELECT、TREE_SELECT）
- [ ] 在 `EntityTable.vue` 中实现关联字段显示（显示名称而非 ID）
- [ ] 在 `EntitySearchBar.vue` 中实现关联字段搜索
- [ ] 实现关联数据缓存机制（AssociationCache）
- [ ] 实现批量加载优化（避免 N+1 问题）

### 6.3 测试任务

- [ ] 测试 JPA 外键 ID 关联
- [ ] 测试 JPA @ManyToOne 关联
- [ ] 测试 MongoDB 关联
- [ ] 测试自关联（树形结构）
- [ ] 测试一对多关联
- [ ] 测试多对多关联（中间表）
- [ ] 性能测试（N+1 问题、批量查询）

## 七、生产级增强

### 7.1 关联数据验证

#### 7.1.1 创建/更新时验证

```java
@Service
public class AssociationValidator {

    /**
     * 验证关联字段的值是否有效
     */
    public void validateAssociations(EntityMeta entityMeta, Object entity) {
        List<FieldMeta> foreignKeyFields = entityMeta.getFields().stream()
            .filter(FieldMeta::isForeignKey)
            .toList();

        for (FieldMeta field : foreignKeyFields) {
            Object value = extractField(entity, field.getName());
            if (value == null) {
                // 允许为空（除非字段标记为 required）
                if (field.isRequired()) {
                    throw new ValidationException(
                        ErrorCode.ASSOCIATION_REQUIRED,
                        String.format("关联字段 %s 不能为空", field.getLabel())
                    );
                }
                continue;
            }

            // 验证关联实体是否存在
            validateAssociationExists(field, value);
        }
    }

    private void validateAssociationExists(FieldMeta field, Object id) {
        EntityMeta referencedMeta = entityMetaRegistry.getByEntityName(field.getReferencedEntity());
        if (referencedMeta == null) {
            throw new ConfigurationException(
                ErrorCode.ASSOCIATION_ENTITY_NOT_FOUND,
                String.format("关联实体 %s 不存在", field.getReferencedEntity())
            );
        }

        try {
            // 尝试获取关联实体（会自动过滤软删除）
            Object referenced = entityCrudService.get(referencedMeta, id);
            if (referenced == null) {
                throw new ValidationException(
                    ErrorCode.ASSOCIATION_TARGET_NOT_FOUND,
                    String.format("%s 不存在或已被删除", field.getLabel())
                );
            }
        } catch (Exception e) {
            throw new ValidationException(
                ErrorCode.ASSOCIATION_TARGET_NOT_FOUND,
                String.format("%s (ID: %s) 不存在或已被删除", field.getLabel(), id)
            );
        }
    }
}
```

#### 7.1.2 在 CrudService 中集成验证

```java
@Override
public Object create(EntityMeta entityMeta, Object body) {
    Object entity = convertToEntity(entityMeta, body);

    // 验证关联字段
    associationValidator.validateAssociations(entityMeta, entity);

    // 执行创建
    return doCreate(entityMeta, entity);
}

@Override
public Object update(EntityMeta entityMeta, Object id, Object body) {
    Object entity = convertToEntity(entityMeta, body);

    // 验证关联字段
    associationValidator.validateAssociations(entityMeta, entity);

    // 执行更新
    return doUpdate(entityMeta, id, entity);
}
```

### 7.2 性能优化

#### 7.2.1 options 接口优化

```java
@GetMapping("/{entity}/options")
@Cacheable(value = "entity-options", key = "#entity + ':' + #query + ':' + #page + ':' + #size")
public Result<Page<EntityOption>> getOptions(
    @PathVariable String entity,
    @RequestParam(required = false) String query,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size
) {
    EntityMeta meta = entityMetaRegistry.getByEntityName(entity);
    if (meta == null) {
        throw new IllegalArgumentException("实体不存在: " + entity);
    }

    // 限制最大页大小，防止一次查询过多数据
    size = Math.min(size, 100);

    // 获取 displayField
    String displayField = getDisplayFieldFromMeta(meta);

    // 构建查询条件
    ListCriteria criteria = ListCriteria.empty();
    if (query != null && !query.isBlank()) {
        criteria.addFilter(new FilterCondition(displayField, FilterOp.LIKE, query));
    }

    // 只查询必要的字段（id + displayField）
    criteria.setProjection(List.of("id", displayField));

    // 查询数据
    Pageable pageable = PageRequest.of(page, size, Sort.by(displayField));
    Page<Object> result = entityCrudService.list(meta, pageable, criteria);

    // 转换为选项格式
    Page<EntityOption> options = result.map(obj -> new EntityOption(
        extractId(obj),
        extractField(obj, displayField)
    ));

    return Result.success(options);
}
```

#### 7.2.2 数据库索引建议

```sql
-- 为关联字段创建索引
CREATE INDEX idx_users_department_id ON users(department_id);

-- 为 displayField 创建索引（用于 options 搜索）
CREATE INDEX idx_departments_name ON sys_department(name);

-- 复合索引（用于软删除 + 搜索）
CREATE INDEX idx_departments_deleted_name ON sys_department(deleted, name);
```

#### 7.2.3 前端缓存策略

```typescript
// 关联数据缓存配置
const CACHE_CONFIG = {
  // 小数据量实体（如部门、角色）：长期缓存
  smallEntity: {
    ttl: 30 * 60 * 1000,  // 30 分钟
    maxSize: 1000
  },
  // 大数据量实体（如用户）：短期缓存
  largeEntity: {
    ttl: 5 * 60 * 1000,   // 5 分钟
    maxSize: 100
  }
}

class AssociationCache {
  private cache = new Map<string, CacheEntry>()

  async getOptions(entity: string, query?: string): Promise<EntityOption[]> {
    const cacheKey = `${entity}:${query || ''}`
    const cached = this.cache.get(cacheKey)

    // 检查缓存是否有效
    if (cached && Date.now() - cached.timestamp < this.getTTL(entity)) {
      return cached.data
    }

    // 从服务器获取
    const response = await api.get(`/api/dataforge/${entity}/options`, {
      params: { query, size: 50 }
    })

    // 更新缓存
    this.cache.set(cacheKey, {
      data: response.data.content,
      timestamp: Date.now()
    })

    return response.data.content
  }

  private getTTL(entity: string): number {
    // 根据实体类型返回不同的 TTL
    const smallEntities = ['Department', 'Role', 'Permission', 'SysDict']
    return smallEntities.includes(entity)
      ? CACHE_CONFIG.smallEntity.ttl
      : CACHE_CONFIG.largeEntity.ttl
  }
}
```

### 7.3 权限控制

#### 7.3.1 options/tree 接口权限

```java
@GetMapping("/{entity}/options")
public Result<Page<EntityOption>> getOptions(@PathVariable String entity, ...) {
    EntityMeta meta = entityMetaRegistry.getByEntityName(entity);

    // 1. 检查读权限
    if (!permissionChecker.hasPermission(meta.getPermissionRead())) {
        throw new ForbiddenException(
            ErrorCode.PERMISSION_DENIED,
            String.format("无权限访问 %s", meta.getDisplayName())
        );
    }

    // 2. 应用数据权限
    ListCriteria criteria = ListCriteria.empty();
    if (meta.isEnableDataPermission()) {
        // 添加数据权限过滤条件
        dataPermissionService.applyDataPermission(criteria, meta);
    }

    // 3. 查询数据
    Page<Object> result = entityCrudService.list(meta, pageable, criteria);

    return Result.success(result.map(this::toOption));
}
```

#### 7.3.2 数据权限示例

```java
@Service
public class DataPermissionService {

    /**
     * 应用数据权限过滤
     */
    public void applyDataPermission(ListCriteria criteria, EntityMeta meta) {
        String dataPermissionType = meta.getDataPermissionType();

        if ("DEPT".equals(dataPermissionType)) {
            // 部门数据权限：只能看到自己部门及子部门的数据
            Long currentUserDeptId = getCurrentUserDeptId();
            List<Long> deptIds = getDeptIdsWithChildren(currentUserDeptId);
            criteria.addFilter(new FilterCondition("departmentId", FilterOp.IN, deptIds));
        } else if ("SELF".equals(dataPermissionType)) {
            // 个人数据权限：只能看到自己创建的数据
            String currentUserId = getCurrentUserId();
            criteria.addFilter(new FilterCondition("createBy", FilterOp.EQ, currentUserId));
        }
        // 其他数据权限类型...
    }
}
```

### 7.4 导入导出支持

#### 7.4.1 导出配置

```java
@Column(name = "department_id")
@DataforgeField(
    label = "部门",
    foreignKey = true,
    referencedEntity = "Department",
    displayField = "name"
)
@DataforgeExport(
    exportFormat = "display",  // 导出时显示名称而非 ID
    header = "部门名称"
)
private Long departmentId;
```

#### 7.4.2 导出实现

```java
@Service
public class ExcelExportService {

    public void exportToExcel(EntityMeta meta, List<Object> data, OutputStream out) {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(meta.getDisplayName());

        // 创建表头
        Row headerRow = sheet.createRow(0);
        List<FieldMeta> exportFields = getExportFields(meta);
        for (int i = 0; i < exportFields.size(); i++) {
            FieldMeta field = exportFields.get(i);
            headerRow.createCell(i).setCellValue(field.getExportHeader());
        }

        // 批量加载关联数据（避免 N+1）
        Map<String, Map<Object, String>> associationCache = loadAssociations(meta, data);

        // 填充数据
        for (int rowIdx = 0; rowIdx < data.size(); rowIdx++) {
            Object entity = data.get(rowIdx);
            Row row = sheet.createRow(rowIdx + 1);

            for (int colIdx = 0; colIdx < exportFields.size(); colIdx++) {
                FieldMeta field = exportFields.get(colIdx);
                Object value = extractField(entity, field.getName());

                // 关联字段特殊处理
                if (field.isForeignKey() && "display".equals(field.getExportFormat())) {
                    String displayValue = associationCache
                        .get(field.getName())
                        .get(value);
                    row.createCell(colIdx).setCellValue(displayValue);
                } else {
                    row.createCell(colIdx).setCellValue(formatValue(value, field));
                }
            }
        }

        workbook.write(out);
    }

    /**
     * 批量加载关联数据（避免 N+1 问题）
     */
    private Map<String, Map<Object, String>> loadAssociations(EntityMeta meta, List<Object> data) {
        Map<String, Map<Object, String>> result = new HashMap<>();

        List<FieldMeta> foreignKeyFields = meta.getFields().stream()
            .filter(FieldMeta::isForeignKey)
            .filter(f -> "display".equals(f.getExportFormat()))
            .toList();

        for (FieldMeta field : foreignKeyFields) {
            // 收集所有 ID
            Set<Object> ids = data.stream()
                .map(entity -> extractField(entity, field.getName()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

            if (ids.isEmpty()) continue;

            // 批量查询关联实体
            EntityMeta referencedMeta = entityMetaRegistry.getByEntityName(field.getReferencedEntity());
            ListCriteria criteria = ListCriteria.empty();
            criteria.addFilter(new FilterCondition("id", FilterOp.IN, ids));

            List<Object> referencedEntities = entityCrudService
                .list(referencedMeta, Pageable.unpaged(), criteria)
                .getContent();

            // 构建 ID -> displayValue 映射
            Map<Object, String> mapping = referencedEntities.stream()
                .collect(Collectors.toMap(
                    entity -> extractField(entity, "id"),
                    entity -> String.valueOf(extractField(entity, field.getDisplayField()))
                ));

            result.put(field.getName(), mapping);
        }

        return result;
    }
}
```

#### 7.4.3 导入实现

```java
@Service
public class ExcelImportService {

    public List<Object> importFromExcel(EntityMeta meta, InputStream in) {
        Workbook workbook = new XSSFWorkbook(in);
        Sheet sheet = workbook.getSheetAt(0);

        List<FieldMeta> importFields = getImportFields(meta);
        List<Object> entities = new ArrayList<>();

        // 跳过表头，从第二行开始
        for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;

            Map<String, Object> data = new HashMap<>();

            for (int colIdx = 0; colIdx < importFields.size(); colIdx++) {
                FieldMeta field = importFields.get(colIdx);
                Cell cell = row.getCell(colIdx);
                Object value = getCellValue(cell);

                // 关联字段特殊处理：通过 displayField 查找 ID
                if (field.isForeignKey() && value != null) {
                    Object id = findAssociationId(field, value.toString());
                    if (id == null) {
                        throw new ImportException(
                            ErrorCode.IMPORT_ASSOCIATION_NOT_FOUND,
                            String.format("第 %d 行：%s '%s' 不存在", rowIdx + 1, field.getLabel(), value)
                        );
                    }
                    data.put(field.getName(), id);
                } else {
                    data.put(field.getName(), value);
                }
            }

            entities.add(convertToEntity(meta, data));
        }

        return entities;
    }

    /**
     * 通过 displayField 查找关联实体的 ID
     */
    private Object findAssociationId(FieldMeta field, String displayValue) {
        EntityMeta referencedMeta = entityMetaRegistry.getByEntityName(field.getReferencedEntity());

        ListCriteria criteria = ListCriteria.empty();
        criteria.addFilter(new FilterCondition(
            field.getDisplayField(),
            FilterOp.EQ,
            displayValue
        ));

        List<Object> results = entityCrudService
            .list(referencedMeta, PageRequest.of(0, 1), criteria)
            .getContent();

        return results.isEmpty() ? null : extractField(results.get(0), "id");
    }
}
```

### 7.5 级联删除策略

#### 7.5.1 配置级联策略

```java
@Column(name = "department_id")
@DataforgeField(
    label = "部门",
    foreignKey = true,
    referencedEntity = "Department",
    displayField = "name",
    // 级联删除策略
    cascadeDelete = CascadeStrategy.RESTRICT  // 默认：限制删除
)
private Long departmentId;

// 级联策略枚举
public enum CascadeStrategy {
    RESTRICT,   // 限制删除：如果有关联数据，禁止删除
    SET_NULL,   // 置空：删除时将关联字段设为 NULL
    CASCADE     // 级联删除：删除时同时删除关联数据（危险，需谨慎使用）
}
```

#### 7.5.2 实现级联删除检查

```java
@Service
public class CascadeDeleteService {

    /**
     * 删除前检查级联约束
     */
    public void checkCascadeConstraints(EntityMeta entityMeta, Object id) {
        // 查找所有引用此实体的关联字段
        List<AssociationReference> references = findReferences(entityMeta);

        for (AssociationReference ref : references) {
            FieldMeta field = ref.getField();
            CascadeStrategy strategy = field.getCascadeDelete();

            // 检查是否有关联数据
            long count = countReferencingEntities(ref.getEntityMeta(), field, id);

            if (count > 0) {
                switch (strategy) {
                    case RESTRICT:
                        throw new CascadeConstraintException(
                            ErrorCode.CASCADE_DELETE_RESTRICTED,
                            String.format(
                                "无法删除：存在 %d 条关联的 %s 数据",
                                count,
                                ref.getEntityMeta().getDisplayName()
                            )
                        );
                    case SET_NULL:
                        // 允许删除，后续会将关联字段置空
                        break;
                    case CASCADE:
                        // 允许删除，后续会级联删除关联数据
                        // 警告：这是危险操作，需要记录日志
                        log.warn("级联删除：将删除 {} 条关联的 {} 数据",
                            count, ref.getEntityMeta().getDisplayName());
                        break;
                }
            }
        }
    }

    /**
     * 执行级联删除操作
     */
    public void executeCascadeDelete(EntityMeta entityMeta, Object id) {
        List<AssociationReference> references = findReferences(entityMeta);

        for (AssociationReference ref : references) {
            FieldMeta field = ref.getField();
            CascadeStrategy strategy = field.getCascadeDelete();

            if (strategy == CascadeStrategy.SET_NULL) {
                // 将关联字段置空
                setReferencingFieldsToNull(ref.getEntityMeta(), field, id);
            } else if (strategy == CascadeStrategy.CASCADE) {
                // 级联删除关联数据
                deleteReferencingEntities(ref.getEntityMeta(), field, id);
            }
        }
    }

    /**
     * 查找所有引用此实体的关联字段
     */
    private List<AssociationReference> findReferences(EntityMeta entityMeta) {
        List<AssociationReference> references = new ArrayList<>();

        for (EntityMeta meta : entityMetaRegistry.getAllEntities()) {
            for (FieldMeta field : meta.getFields()) {
                if (field.isForeignKey() &&
                    entityMeta.getEntityName().equals(field.getReferencedEntity())) {
                    references.add(new AssociationReference(meta, field));
                }
            }
        }

        return references;
    }
}
```

#### 7.5.3 在删除操作中集成

```java
@Override
public void delete(EntityMeta entityMeta, Object id) {
    // 1. 检查级联约束
    cascadeDeleteService.checkCascadeConstraints(entityMeta, id);

    // 2. 执行级联操作（SET_NULL 或 CASCADE）
    cascadeDeleteService.executeCascadeDelete(entityMeta, id);

    // 3. 删除实体本身
    doDelete(entityMeta, id);
}
```

### 7.6 统一错误码

#### 7.6.1 定义错误码

```java
public enum ErrorCode {
    // 关联相关错误码 (3000-3099)
    ASSOCIATION_REQUIRED(3000, "关联字段不能为空"),
    ASSOCIATION_ENTITY_NOT_FOUND(3001, "关联实体配置不存在"),
    ASSOCIATION_TARGET_NOT_FOUND(3002, "关联的数据不存在或已被删除"),
    ASSOCIATION_TARGET_DELETED(3003, "关联的数据已被删除"),
    ASSOCIATION_PERMISSION_DENIED(3004, "无权限访问关联数据"),

    // 级联删除相关 (3010-3019)
    CASCADE_DELETE_RESTRICTED(3010, "存在关联数据，无法删除"),
    CASCADE_DELETE_FAILED(3011, "级联删除失败"),

    // 导入导出相关 (3020-3029)
    IMPORT_ASSOCIATION_NOT_FOUND(3020, "导入失败：关联数据不存在"),
    EXPORT_ASSOCIATION_FAILED(3021, "导出失败：无法加载关联数据"),

    // 验证相关 (3030-3039)
    VALIDATION_ASSOCIATION_INVALID(3030, "关联字段值无效"),
    VALIDATION_ASSOCIATION_CIRCULAR(3031, "检测到循环引用");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
```

#### 7.6.2 统一异常处理

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    public Result<Void> handleValidationException(ValidationException e) {
        log.warn("验证失败: {}", e.getMessage());
        return Result.error(e.getErrorCode().getCode(), e.getMessage());
    }

    @ExceptionHandler(CascadeConstraintException.class)
    public Result<Void> handleCascadeConstraintException(CascadeConstraintException e) {
        log.warn("级联约束失败: {}", e.getMessage());
        return Result.error(e.getErrorCode().getCode(), e.getMessage());
    }

    @ExceptionHandler(ImportException.class)
    public Result<Void> handleImportException(ImportException e) {
        log.error("导入失败: {}", e.getMessage());
        return Result.error(e.getErrorCode().getCode(), e.getMessage());
    }
}
```

#### 7.6.3 前端错误处理

```typescript
// 错误码映射
const ERROR_MESSAGES: Record<number, string> = {
  3000: '请选择{field}',
  3002: '{field}不存在或已被删除，请重新选择',
  3010: '该数据存在关联，无法删除。请先解除关联或联系管理员。',
  3020: '导入失败：第{row}行的{field}不存在',
}

// 统一错误处理
api.interceptors.response.use(
  response => response,
  error => {
    const { code, message } = error.response.data

    // 使用自定义错误消息
    const customMessage = ERROR_MESSAGES[code]
    if (customMessage) {
      ElMessage.error(customMessage.replace(/{(\w+)}/g, (_, key) => {
        return error.response.data.params?.[key] || key
      }))
    } else {
      ElMessage.error(message || '操作失败')
    }

    return Promise.reject(error)
  }
)
```

### 7.7 配置示例总结

```java
@Entity
@Table(name = "users")
@DataforgeEntity(
    pathSegment = "users",
    displayName = "用户",
    storage = StorageTypes.JPA,
    enableDataPermission = true,
    dataPermissionType = "DEPT"
)
public class User extends BaseEntity<Long> {

    @Column(name = "department_id")
    @DataforgeField(
        // 基础配置
        label = "部门",
        group = "组织信息",
        formOrder = 5,
        columnOrder = 6,
        required = true,  // 必填

        // 关联配置
        foreignKey = true,
        referencedEntity = "Department",
        displayField = "name",
        valueField = "id",
        component = FormComponent.SELECT,

        // 搜索配置
        searchable = true,
        searchComponent = SearchComponent.SELECT,

        // 级联策略
        cascadeDelete = CascadeStrategy.RESTRICT
    )
    @DataforgeExport(
        exportFormat = "display",  // 导出时显示名称
        header = "部门名称"
    )
    @DataforgeDto(include = {DtoType.CREATE, DtoType.UPDATE, DtoType.PAGE_RESPONSE, DtoType.RESPONSE})
    private Long departmentId;
}
```

## 八、总结

### 8.0 不支持的场景

1. ❌ **跨存储类型关联**：User（JPA）关联 Department（MongoDB）
2. ❌ **深层级联**：超过 2 层的关联（性能差）
3. ❌ **双向关联的自动维护**：需要应用层手动维护

这些场景应该通过微服务 API 调用或重新设计数据模型来解决。

### 8.1 核心特性

1. **存储内关联**：只支持同一存储类型内的关联，架构清晰
2. **元数据驱动**：通过注解统一声明，各存储按需实现
3. **性能优化**：JPA 用 EntityGraph，MongoDB 用 $lookup 或批量查询
4. **前端统一**：无论后端存储类型，前端使用统一的 API 和组件
5. **生产级增强**：验证、权限、导入导出、级联删除、错误码

### 8.2 生产级特性清单

| 特性 | 状态 | 说明 |
|------|------|------|
| ✅ 关联验证 | 必须实现 | 创建/更新时验证关联 ID 是否存在 |
| ✅ 性能优化 | 必须实现 | options 分页、缓存、索引 |
| ✅ 权限控制 | 必须实现 | options/tree 复用实体读权限，支持数据权限 |
| ✅ 导入导出 | 必须实现 | 支持 display/id 格式，导入时自动转换 |
| ✅ 级联策略 | 必须实现 | RESTRICT/SET_NULL/CASCADE 三种策略 |
| ✅ 错误码 | 必须实现 | 统一的错误码和提示文案 |

### 8.3 实施优先级

#### P0（必须实现）
- [ ] 关联数据验证（创建/更新时）
- [ ] options 接口（支持搜索、分页）
- [ ] 前端关联选择器（EntityForm.vue）
- [ ] 前端关联显示（EntityTable.vue）
- [ ] 统一错误码和异常处理

#### P1（重要）
- [ ] 级联删除策略（RESTRICT/SET_NULL/CASCADE）
- [ ] 权限控制（options 接口复用实体权限）
- [ ] 性能优化（JPA EntityGraph、MongoDB 批量查询）
- [ ] 前端缓存机制

#### P2（增强）
- [ ] 导入导出支持（display 格式）
- [ ] 数据权限支持（options 接口）
- [ ] 树形接口（自关联场景）
- [ ] 批量查询接口（batch-lookup）

### 8.4 技术债务

1. **循环引用检测**：自关联场景需要检测循环引用（如部门树）
2. **关联数据缓存失效**：关联实体更新时，需要失效相关缓存
3. **大数据量优化**：options 接口在数据量大时需要虚拟滚动或搜索优化
4. **审计日志**：关联字段变更需要记录审计日志
5. **软删除处理**：关联实体软删除后，需要在 UI 上特殊标记
