# 实体关联设计方案 - API 接口规范

> **相关文档**：[主设计文档](entity-association-design.md)、[实施注意事项](entity-association-implementation-notes.md)

**API 路径说明：** 本文档示例使用 `/api/dataforge/` 前缀，实际路径由 `app.dataforge.api-prefix` 配置决定（默认 `/api`）。

## 一、API 接口规范

### 1.1 options 接口详细规范

#### 接口定义

```
GET /api/dataforge/{entity}/options
```

**路径参数：**
- `{entity}`: 实体名称（entityName），**不是** pathSegment

**查询参数：**
| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| query | String | 否 | - | 搜索关键词，按 displayField 模糊匹配 |
| page | Integer | 否 | 0 | 页码，从 0 开始 |
| size | Integer | 否 | 20 | 每页大小，最大 100 |
| sort | String | 否 | displayField,asc | 排序字段和方向 |

**响应格式：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "content": [
      {
        "id": 1,
        "label": "研发部",
        "extra": {  // 可选：额外信息
          "parentId": null,
          "status": "1"
        }
      }
    ],
    "totalElements": 100,
    "totalPages": 5,
    "size": 20,
    "number": 0
  }
}
```

#### entityName vs pathSegment

**关键区别：**

```java
@DataforgeEntity(
    pathSegment = "departments",  // URL 路径：/api/dataforge/departments
    displayName = "部门"
)
@Entity
@Table(name = "sys_department")
public class Department extends BaseEntity<Long> {
    // entityName = "Department" (类名)
}
```

**使用规则：**
- **CRUD 接口**：使用 `pathSegment`
  - `GET /api/dataforge/departments` - 列表
  - `POST /api/dataforge/departments` - 创建

- **options/tree 接口**：使用 `entityName`（类名）
  - `GET /api/dataforge/Department/options` - 获取选项
  - `GET /api/dataforge/Department/tree` - 获取树形数据

**原因：**
- `pathSegment` 是面向用户的 URL 路径（复数、小写）
- `entityName` 是内部标识符，用于关联配置（`referencedEntity = "Department"`）
- options 接口需要与 `referencedEntity` 保持一致

**实现建议：**

```java
@GetMapping("/{entity}/options")
public Result<Page<EntityOption>> getOptions(@PathVariable String entity, ...) {
    // 通过 entityName 查找元数据
    EntityMeta meta = entityMetaRegistry.getByEntityName(entity);
    if (meta == null) {
        throw new NotFoundException(
            ErrorCode.ENTITY_NOT_FOUND,
            String.format("实体 %s 不存在", entity)
        );
    }
    // ...
}
```

**前端调用：**

```typescript
// 正确：使用 entityName
const response = await api.get(`/api/dataforge/${field.referencedEntity}/options`)

// 错误：使用 pathSegment
const response = await api.get(`/api/dataforge/departments/options`)
```

### 1.2 tree 接口详细规范

#### 接口定义

```
GET /api/dataforge/{entity}/tree
```

**路径参数：**
- `{entity}`: 实体名称（entityName）

**查询参数：**
| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| parentId | Long/String | 否 | null | 父节点 ID，null 表示根节点 |
| maxDepth | Integer | 否 | 10 | 最大深度，防止无限递归 |
| includeDisabled | Boolean | 否 | false | 是否包含禁用节点 |

**响应格式：**

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": 1,
      "label": "总公司",
      "parentId": null,
      "children": [
        {
          "id": 2,
          "label": "研发部",
          "parentId": 1,
          "children": []
        }
      ],
      "disabled": false,
      "leaf": false
    }
  ]
}
```

#### 实现（含数据权限）

```java
@GetMapping("/{entity}/tree")
public Result<List<TreeNode>> getTree(
    @PathVariable String entity,
    @RequestParam(required = false) Object parentId,
    @RequestParam(defaultValue = "10") int maxDepth,
    @RequestParam(defaultValue = "false") boolean includeDisabled
) {
    EntityMeta meta = entityMetaRegistry.getByEntityName(entity);
    if (meta == null) {
        throw new NotFoundException(ErrorCode.ENTITY_NOT_FOUND, "实体不存在: " + entity);
    }

    // 1. 检查权限
    if (!permissionChecker.hasPermission(meta.getPermissionRead())) {
        throw new ForbiddenException(ErrorCode.PERMISSION_DENIED, "无权限访问");
    }

    // 2. 检查是否为树形实体
    if (!meta.isTreeEntity()) {
        throw new BadRequestException(ErrorCode.NOT_TREE_ENTITY, "该实体不是树形结构");
    }

    // 3. 构建查询条件
    ListCriteria criteria = ListCriteria.empty();

    // 应用数据权限（关键！）
    if (meta.isEnableDataPermission()) {
        dataPermissionService.applyDataPermission(criteria, meta);
    }

    // 过滤禁用节点（仅当实体有 status 字段时应用，避免无此字段时报错）
    boolean hasStatusField = meta.getFields().stream().anyMatch(f -> "status".equals(f.getName()));
    if (!includeDisabled && hasStatusField) {
        criteria.addFilter(new FilterCondition("status", FilterOp.EQ, "1"));
    }

    // 4. 查询所有节点
    List<Object> allNodes = entityCrudService
        .list(meta, Pageable.unpaged(), criteria)
        .getContent();

    // 5. 构建树形结构
    List<TreeNode> tree = buildTree(allNodes, meta, parentId, maxDepth);

    return Result.success(tree);
}

/**
 * 构建树形结构
 */
private List<TreeNode> buildTree(List<Object> allNodes, EntityMeta meta,
                                  Object parentId, int maxDepth) {
    if (maxDepth <= 0) {
        return Collections.emptyList();
    }

    String parentField = meta.getTreeParentField();
    String nameField = meta.getTreeNameField();

    return allNodes.stream()
        .filter(node -> {
            Object nodeParentId = extractField(node, parentField);
            return Objects.equals(nodeParentId, parentId);
        })
        .map(node -> {
            TreeNode treeNode = new TreeNode();
            treeNode.setId(extractField(node, "id"));
            treeNode.setLabel(String.valueOf(extractField(node, nameField)));
            treeNode.setParentId(extractField(node, parentField));
            treeNode.setDisabled(isDisabled(node));

            // 递归构建子节点
            List<TreeNode> children = buildTree(
                allNodes,
                meta,
                treeNode.getId(),
                maxDepth - 1
            );
            treeNode.setChildren(children);
            treeNode.setLeaf(children.isEmpty());

            return treeNode;
        })
        .collect(Collectors.toList());
}
```

### 1.3 batch-lookup 接口详细规范

#### 接口定义

```
GET /api/dataforge/{entity}/batch-lookup
```

**路径参数：**
- `{entity}`: 实体名称（entityName）

**查询参数：**
| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| ids | String | 是 | - | ID 列表，逗号分隔，如 "1,2,3" |
| fields | String | 否 | id,{displayField} | 返回的字段列表，逗号分隔 |

**约束：**
- 单次查询最多 1000 个 ID
- 超过限制返回 400 错误

**响应格式：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "1": {
      "id": 1,
      "label": "研发部",
      "extra": {
        "status": "1"
      }
    },
    "2": {
      "id": 2,
      "label": "市场部",
      "extra": {
        "status": "1"
      }
    }
  }
}
```

#### 实现

```java
@GetMapping("/{entity}/batch-lookup")
public Result<Map<Object, Map<String, Object>>> batchLookup(
    @PathVariable String entity,
    @RequestParam String ids,
    @RequestParam(required = false) String fields
) {
    EntityMeta meta = entityMetaRegistry.getByEntityName(entity);
    if (meta == null) {
        throw new NotFoundException(ErrorCode.ENTITY_NOT_FOUND, "实体不存在: " + entity);
    }

    // 1. 检查权限
    if (!permissionChecker.hasPermission(meta.getPermissionRead())) {
        throw new ForbiddenException(ErrorCode.PERMISSION_DENIED, "无权限访问");
    }

    // 2. 解析 ID 列表
    List<Object> idList = parseIds(ids, meta.getPrimaryKeyType());

    // 3. 检查数量限制
    if (idList.size() > 1000) {
        throw new BadRequestException(
            ErrorCode.BATCH_LOOKUP_LIMIT_EXCEEDED,
            "单次查询最多支持 1000 个 ID"
        );
    }

    // 4. 构建查询条件
    ListCriteria criteria = ListCriteria.empty();
    criteria.addFilter(new FilterCondition("id", FilterOp.IN, idList));

    // 应用数据权限
    if (meta.isEnableDataPermission()) {
        dataPermissionService.applyDataPermission(criteria, meta);
    }

    // 5. 查询数据
    List<Object> entities = entityCrudService
        .list(meta, Pageable.unpaged(), criteria)
        .getContent();

    // 6. 转换为 Map（注意：lambda 参数使用 obj 避免遮蔽路径参数 entity）
    Map<Object, Map<String, Object>> result = entities.stream()
        .collect(Collectors.toMap(
            obj -> extractField(obj, "id"),
            obj -> toMap(obj, meta, parseFields(fields, meta))
        ));

    return Result.success(result);
}

/**
 * 解析 ID 列表
 */
private List<Object> parseIds(String ids, Class<?> primaryKeyType) {
    return Arrays.stream(ids.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(s -> convertId(s, primaryKeyType))
        .collect(Collectors.toList());
}

/**
 * 转换 ID 类型
 */
private Object convertId(String id, Class<?> primaryKeyType) {
    if (primaryKeyType == Long.class) {
        return Long.parseLong(id);
    } else if (primaryKeyType == Integer.class) {
        return Integer.parseInt(id);
    } else if (primaryKeyType == String.class) {
        return id;
    }
    throw new IllegalArgumentException("不支持的主键类型: " + primaryKeyType);
}
```

## 二、导入优化

### 2.1 导入时的 displayField 查找优化

**问题：** 原设计中每行都调用 `findAssociationId`，导致大量数据库查询。

**优化方案：** 预加载所有关联数据到内存

```java
@Service
public class ExcelImportService {

    public List<Object> importFromExcel(EntityMeta meta, InputStream in) {
        Workbook workbook = new XSSFWorkbook(in);
        Sheet sheet = workbook.getSheetAt(0);

        List<FieldMeta> importFields = getImportFields(meta);

        // 1. 预加载所有关联数据（关键优化！）
        Map<String, Map<String, Object>> associationCache = preloadAssociations(meta, importFields);

        List<Object> entities = new ArrayList<>();

        // 2. 遍历行
        for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;

            Map<String, Object> data = new HashMap<>();

            for (int colIdx = 0; colIdx < importFields.size(); colIdx++) {
                FieldMeta field = importFields.get(colIdx);
                Cell cell = row.getCell(colIdx);
                Object value = getCellValue(cell);

                // 关联字段：从缓存中查找 ID
                if (field.isForeignKey() && value != null) {
                    String displayValue = value.toString().trim();

                    // 从缓存中查找（O(1) 复杂度）
                    Object id = associationCache
                        .get(field.getName())
                        .get(displayValue);

                    if (id == null) {
                        throw new ImportException(
                            ErrorCode.IMPORT_ASSOCIATION_NOT_FOUND,
                            String.format("第 %d 行：%s '%s' 不存在",
                                rowIdx + 1, field.getLabel(), displayValue)
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
     * 预加载所有关联数据到内存
     */
    private Map<String, Map<String, Object>> preloadAssociations(
            EntityMeta meta,
            List<FieldMeta> importFields) {

        Map<String, Map<String, Object>> cache = new HashMap<>();

        List<FieldMeta> foreignKeyFields = importFields.stream()
            .filter(FieldMeta::isForeignKey)
            .toList();

        for (FieldMeta field : foreignKeyFields) {
            EntityMeta referencedMeta = entityMetaRegistry
                .getByEntityName(field.getReferencedEntity());

            // 查询所有关联实体（应用数据权限）
            ListCriteria criteria = ListCriteria.empty();
            if (referencedMeta.isEnableDataPermission()) {
                dataPermissionService.applyDataPermission(criteria, referencedMeta);
            }

            List<Object> allEntities = entityCrudService
                .list(referencedMeta, Pageable.unpaged(), criteria)
                .getContent();

            // 构建 displayValue -> id 映射
            Map<String, Object> mapping = allEntities.stream()
                .collect(Collectors.toMap(
                    entity -> String.valueOf(extractField(entity, field.getDisplayField())).trim(),
                    entity -> extractField(entity, "id"),
                    (existing, replacement) -> existing  // 重名时保留第一个
                ));

            cache.put(field.getName(), mapping);
        }

        return cache;
    }
}
```

**性能对比：**

| 方案 | 导入 1000 行 | 导入 10000 行 |
|------|-------------|--------------|
| 原方案（每行查询） | ~1000 次查询 | ~10000 次查询 |
| 优化方案（预加载） | ~1 次查询 | ~1 次查询 |

**预加载时的重名处理：** 映射使用 `(existing, replacement) -> existing`，displayField 重名时保留第一个。若有歧义风险，建议在导入前校验 displayField 唯一性，或采用 2.2 节方案 C（多结果时报错）。

### 2.2 displayField 重名处理

**问题：** 如果 displayField 不唯一（如多个部门叫"研发部"），导入时无法确定应该关联哪个。

**解决方案：**

#### 方案 A：要求 displayField 唯一（推荐）

在设计阶段就要求 displayField 必须唯一：

```java
@Column(nullable = false, length = 64, unique = true)  // 添加唯一约束
@DataforgeField(label = "部门名称")
private String name;
```

**优点：**
- 简单明确
- 避免歧义
- 性能最优

**缺点：**
- 对数据模型有要求
- 可能需要调整现有数据

#### 方案 B：使用复合字段（适合无法保证唯一的场景）

```java
@DataforgeField(
    label = "部门",
    foreignKey = true,
    referencedEntity = "Department",
    displayField = "name",
    displayFieldComposite = {"code", "name"}  // 新增：复合显示字段
)
private Long departmentId;
```

导出格式：`[研发部-RD]`（code + name）

导入时解析：
```java
// 解析复合字段
String[] parts = displayValue.split("-");
String code = parts[0];
String name = parts[1];

// 按复合条件查询
criteria.addFilter(new FilterCondition("code", FilterOp.EQ, code));
criteria.addFilter(new FilterCondition("name", FilterOp.EQ, name));
```

#### 方案 C：多结果时报错（最安全）

```java
private Object findAssociationId(FieldMeta field, String displayValue) {
    EntityMeta referencedMeta = entityMetaRegistry
        .getByEntityName(field.getReferencedEntity());

    ListCriteria criteria = ListCriteria.empty();
    criteria.addFilter(new FilterCondition(
        field.getDisplayField(),
        FilterOp.EQ,
        displayValue
    ));

    List<Object> results = entityCrudService
        .list(referencedMeta, PageRequest.of(0, 2), criteria)  // 最多查 2 条
        .getContent();

    if (results.isEmpty()) {
        return null;
    }

    if (results.size() > 1) {
        throw new ImportException(
            ErrorCode.IMPORT_ASSOCIATION_AMBIGUOUS,
            String.format("%s '%s' 存在多条记录，无法确定应该关联哪一条。" +
                "请使用唯一标识或联系管理员。",
                field.getLabel(), displayValue)
        );
    }

    return extractField(results.get(0), "id");
}
```

**推荐策略：**
1. **优先使用方案 A**：要求 displayField 唯一
2. **如果无法保证唯一**：使用方案 C，多结果时报错，提示用户
3. **特殊场景**：使用方案 B，复合字段

## 三、错误码补充

### 3.1 新增错误码

```java
public enum ErrorCode {
    // ... 现有错误码 ...

    // 实体相关 (3040-3049)
    ENTITY_NOT_FOUND(3040, "实体不存在"),
    NOT_TREE_ENTITY(3041, "该实体不是树形结构"),

    // 批量查询相关 (3050-3059)
    BATCH_LOOKUP_LIMIT_EXCEEDED(3050, "批量查询数量超过限制"),
    BATCH_LOOKUP_INVALID_IDS(3051, "批量查询 ID 格式错误"),

    // 导入相关补充 (3060-3069)
    IMPORT_ASSOCIATION_AMBIGUOUS(3060, "导入失败：关联数据存在多条记录"),
    IMPORT_FILE_TOO_LARGE(3061, "导入文件过大"),
    IMPORT_INVALID_FORMAT(3062, "导入文件格式错误");
}
```

## 四、配置示例补充

### 4.1 完整的关联字段配置

```java
@Entity
@Table(name = "users")
@DataforgeEntity(
    pathSegment = "users",           // CRUD 接口路径
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
        description = "用户所属部门，影响数据权限范围",
        group = "组织信息",
        groupOrder = 2,
        formOrder = 5,
        columnOrder = 6,
        required = true,

        // 关联配置（关键！）
        foreignKey = true,
        referencedEntity = "Department",  // 使用 entityName，不是 pathSegment
        displayField = "name",            // 显示字段，建议唯一
        valueField = "id",
        component = FormComponent.SELECT,

        // 搜索配置
        searchable = true,
        searchComponent = SearchComponent.SELECT,
        searchPlaceholder = "请选择部门",

        // 表单配置
        placeholder = "请选择所属部门",
        tips = "选择用户所属的部门，影响数据权限",

        // 级联策略
        cascadeDelete = CascadeStrategy.RESTRICT,

        // 导出配置
        exportFormat = "display"  // 导出部门名称而非 ID
    )
    @DataforgeExport(
        header = "部门名称",
        exportOrder = 6
    )
    @DataforgeDto(include = {
        DtoType.CREATE,
        DtoType.UPDATE,
        DtoType.PAGE_RESPONSE,
        DtoType.RESPONSE
    })
    private Long departmentId;
}

@Entity
@Table(name = "sys_department")
@DataforgeEntity(
    pathSegment = "departments",     // CRUD 接口路径
    displayName = "部门",
    storage = StorageTypes.JPA,
    treeEntity = true,               // 标记为树形实体
    treeParentField = "parentId",
    treeNameField = "name"
)
public class Department extends BaseEntity<Long> {

    @Column(nullable = false, length = 64, unique = true)  // 唯一约束
    @DataforgeField(label = "部门名称", required = true)
    private String name;

    @Column(name = "parent_id")
    @DataforgeField(
        label = "上级部门",
        foreignKey = true,
        referencedEntity = "Department",  // 自关联
        displayField = "name",
        component = FormComponent.TREE_SELECT,
        cascadeDelete = CascadeStrategy.RESTRICT
    )
    private Long parentId;
}
```

### 4.2 前端调用示例

```typescript
// 1. 获取关联实体选项（用于下拉框）
const loadDepartmentOptions = async (query?: string) => {
  const response = await api.get('/api/dataforge/Department/options', {
    params: { query, size: 50 }
  })
  return response.data.content
}

// 2. 获取树形数据（用于树形选择器）
const loadDepartmentTree = async () => {
  const response = await api.get('/api/dataforge/Department/tree')
  return response.data
}

// 3. 批量查询关联数据（用于表格显示）
const batchLookupDepartments = async (ids: number[]) => {
  const response = await api.get('/api/dataforge/Department/batch-lookup', {
    params: { ids: ids.join(',') }
  })
  return response.data
}
```

## 五、实施检查清单

### 5.1 接口实现检查

- [ ] options 接口使用 entityName 而非 pathSegment
- [ ] options 接口应用数据权限过滤
- [ ] options 接口限制最大页大小（100）
- [ ] tree 接口应用数据权限过滤
- [ ] tree 接口限制最大深度（防止无限递归）
- [ ] batch-lookup 接口限制最大 ID 数量（1000）
- [ ] batch-lookup 接口应用数据权限过滤

### 5.2 导入优化检查

- [ ] 导入时预加载所有关联数据到内存
- [ ] 使用 Map 缓存 displayValue -> id 映射
- [ ] displayField 重名时的处理策略已定义
- [ ] 导入错误提示清晰（包含行号、字段名、值）

### 5.3 配置检查

- [ ] displayField 字段添加唯一约束（推荐）
- [ ] referencedEntity 使用 entityName（类名）
- [ ] cascadeDelete 策略已明确定义
- [ ] exportFormat 已配置（id 或 display）

### 5.4 文档检查

- [ ] API 文档中明确 entityName vs pathSegment 的区别
- [ ] 导入文档中说明 displayField 唯一性要求
- [ ] 错误码文档已更新
- [ ] 前端调用示例已提供

## 六、总结

这些补充说明解决了设计文档中遗漏的关键细节：

1. **API 规范**：明确了 entityName vs pathSegment 的使用场景
2. **数据权限**：确保 options/tree/batch-lookup 都应用数据权限
3. **导入优化**：预加载关联数据，避免 N+1 问题
4. **重名处理**：提供了三种方案，推荐使用唯一约束
5. **错误码**：补充了新的错误场景
6. **配置示例**：提供了完整的配置和调用示例

这些细节确保了方案的完整性和可实施性。

## 七、技术债务详细清单

### 7.1 循环引用检测（自关联场景）

**场景：** 部门树中可能出现循环引用（A → B → C → A）

**实现方案：**

```java
@Service
public class CircularReferenceDetector {

    /**
     * 检测树形结构中的循环引用
     */
    public void detectCircularReference(EntityMeta meta, Object id, Object newParentId) {
        if (!meta.isTreeEntity()) {
            return;
        }

        if (id == null || newParentId == null) {
            return;
        }

        // 检测是否将节点设置为自己的子节点
        if (Objects.equals(id, newParentId)) {
            throw new ValidationException(
                ErrorCode.CIRCULAR_REFERENCE_DETECTED,
                "不能将节点设置为自己的父节点"
            );
        }

        // 检测是否形成环路
        Set<Object> visited = new HashSet<>();
        Object currentId = newParentId;

        while (currentId != null) {
            if (visited.contains(currentId)) {
                throw new ValidationException(
                    ErrorCode.CIRCULAR_REFERENCE_DETECTED,
                    "检测到循环引用，无法设置该父节点"
                );
            }

            if (Objects.equals(currentId, id)) {
                throw new ValidationException(
                    ErrorCode.CIRCULAR_REFERENCE_DETECTED,
                    "不能将节点设置为自己的子节点的父节点"
                );
            }

            visited.add(currentId);

            // 查询父节点
            Object parent = entityCrudService.get(meta, currentId);
            currentId = extractField(parent, meta.getTreeParentField());

            // 防止无限循环
            if (visited.size() > meta.getTreeMaxDepth()) {
                throw new ValidationException(
                    ErrorCode.TREE_DEPTH_EXCEEDED,
                    "树形结构深度超过限制"
                );
            }
        }
    }
}

// 在更新操作中集成
@Override
public Object update(EntityMeta entityMeta, Object id, Object body) {
    Object entity = convertToEntity(entityMeta, body);

    // 检测循环引用
    if (entityMeta.isTreeEntity()) {
        Object newParentId = extractField(entity, entityMeta.getTreeParentField());
        circularReferenceDetector.detectCircularReference(entityMeta, id, newParentId);
    }

    // 执行更新
    return doUpdate(entityMeta, id, entity);
}
```

**错误码：**
```java
CIRCULAR_REFERENCE_DETECTED(3070, "检测到循环引用"),
TREE_DEPTH_EXCEEDED(3071, "树形结构深度超过限制")
```

### 7.2 关联数据缓存失效

**场景：** 部门名称从"研发部"改为"技术部"后，options 缓存仍返回旧数据

**实现方案：**

```java
@Service
public class AssociationCacheManager {

    @Autowired
    private CacheManager cacheManager;

    /**
     * 实体更新后，失效相关的关联缓存
     */
    public void invalidateAssociationCache(EntityMeta entityMeta, Object id) {
        // 1. 失效 options 缓存
        Cache optionsCache = cacheManager.getCache("entity-options");
        if (optionsCache != null) {
            // 失效该实体的所有 options 缓存
            optionsCache.evict(entityMeta.getEntityName());
        }

        // 2. 失效 batch-lookup 缓存
        Cache lookupCache = cacheManager.getCache("batch-lookup");
        if (lookupCache != null) {
            lookupCache.evict(entityMeta.getEntityName() + ":" + id);
        }

        // 3. 失效 tree 缓存
        if (entityMeta.isTreeEntity()) {
            Cache treeCache = cacheManager.getCache("entity-tree");
            if (treeCache != null) {
                treeCache.evict(entityMeta.getEntityName());
            }
        }

        // 4. 通知前端刷新（通过 WebSocket）
        notifyFrontendCacheInvalidation(entityMeta.getEntityName(), id);
    }

    /**
     * 通过 WebSocket 通知前端刷新缓存
     */
    private void notifyFrontendCacheInvalidation(String entityName, Object id) {
        CacheInvalidationMessage message = new CacheInvalidationMessage(
            entityName,
            id,
            System.currentTimeMillis()
        );
        messagingTemplate.convertAndSend("/topic/cache-invalidation", message);
    }
}

// 在更新/删除操作中集成
@Override
public Object update(EntityMeta entityMeta, Object id, Object body) {
    Object result = doUpdate(entityMeta, id, body);

    // 失效关联缓存
    associationCacheManager.invalidateAssociationCache(entityMeta, id);

    return result;
}
```

**前端处理：**

```typescript
// 监听缓存失效消息
const stompClient = new StompClient('/ws')

stompClient.subscribe('/topic/cache-invalidation', (message) => {
  const { entityName, id, timestamp } = JSON.parse(message.body)

  // 清除本地缓存
  associationCache.invalidate(entityName, id)

  // 如果当前页面正在显示该实体，刷新数据
  if (currentEntity === entityName) {
    refreshCurrentPage()
  }
})
```

### 7.3 大数据量优化

**场景：** options 接口返回 10 万条部门数据，前端卡顿

**实现方案：**

#### 方案 A：虚拟滚动（推荐）

```typescript
// 前端使用虚拟滚动组件
<el-select
  v-model="formData.departmentId"
  filterable
  remote
  :remote-method="searchDepartments"
  :loading="loading"
>
  <el-option
    v-for="item in visibleOptions"
    :key="item.id"
    :label="item.label"
    :value="item.id"
  />
</el-select>

// 虚拟滚动逻辑
const searchDepartments = async (query: string) => {
  loading.value = true
  try {
    // 只加载前 50 条
    const response = await api.get('/api/dataforge/Department/options', {
      params: { query, size: 50 }
    })
    visibleOptions.value = response.data.content
  } finally {
    loading.value = false
  }
}
```

#### 方案 B：分级加载（树形结构）

```java
@GetMapping("/{entity}/tree")
public Result<List<TreeNode>> getTree(
    @PathVariable String entity,
    @RequestParam(required = false) Object parentId,  // 按需加载子节点
    @RequestParam(defaultValue = "1") int depth       // 只加载一层
) {
    // 只查询指定父节点的直接子节点
    ListCriteria criteria = ListCriteria.empty();
    criteria.addFilter(new FilterCondition(
        meta.getTreeParentField(),
        FilterOp.EQ,
        parentId
    ));

    List<Object> nodes = entityCrudService.list(meta, Pageable.unpaged(), criteria).getContent();

    return Result.success(nodes.stream()
        .map(node -> toTreeNode(node, meta, false))  // 不递归加载子节点
        .toList());
}
```

#### 方案 C：搜索优化（全文索引）

```java
// 为 displayField 创建全文索引
@Entity
@Table(name = "sys_department", indexes = {
    @Index(name = "idx_dept_name_fulltext", columnList = "name")
})
public class Department extends BaseEntity<Long> {

    @Column(nullable = false, length = 64)
    @DataforgeField(label = "部门名称")
    private String name;
}

// 使用全文搜索
@Query("SELECT d FROM Department d WHERE LOWER(d.name) LIKE LOWER(CONCAT('%', :query, '%'))")
List<Department> searchByName(@Param("query") String query, Pageable pageable);
```

### 7.4 审计日志（记录关联字段变更）

**场景：** 用户的部门从"研发部"改为"市场部"，需要记录可读的审计日志

**实现方案：**

```java
@Service
public class AssociationAuditLogger {

    /**
     * 记录关联字段变更
     */
    public void logAssociationChange(EntityMeta meta, Object id, Object oldEntity, Object newEntity) {
        List<FieldMeta> foreignKeyFields = meta.getFields().stream()
            .filter(FieldMeta::isForeignKey)
            .toList();

        for (FieldMeta field : foreignKeyFields) {
            Object oldValue = extractField(oldEntity, field.getName());
            Object newValue = extractField(newEntity, field.getName());

            if (!Objects.equals(oldValue, newValue)) {
                // 查询关联实体的显示值
                String oldDisplayValue = getDisplayValue(field, oldValue);
                String newDisplayValue = getDisplayValue(field, newValue);

                // 记录审计日志
                AuditLog log = new AuditLog();
                log.setEntityName(meta.getEntityName());
                log.setEntityId(id);
                log.setFieldName(field.getName());
                log.setFieldLabel(field.getLabel());
                log.setOldValue(oldValue);
                log.setOldDisplayValue(oldDisplayValue);
                log.setNewValue(newValue);
                log.setNewDisplayValue(newDisplayValue);
                log.setOperationType("UPDATE");
                log.setOperator(getCurrentUser());
                log.setOperateTime(LocalDateTime.now());

                auditLogRepository.save(log);
            }
        }
    }

    /**
     * 获取关联实体的显示值
     */
    private String getDisplayValue(FieldMeta field, Object id) {
        if (id == null) {
            return null;
        }

        try {
            EntityMeta referencedMeta = entityMetaRegistry.getByEntityName(field.getReferencedEntity());
            Object entity = entityCrudService.get(referencedMeta, id);
            return String.valueOf(extractField(entity, field.getDisplayField()));
        } catch (Exception e) {
            return id.toString();  // 降级：返回 ID
        }
    }
}

// 审计日志实体
@Entity
@Table(name = "sys_audit_log")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String entityName;
    private Object entityId;
    private String fieldName;
    private String fieldLabel;

    private Object oldValue;
    private String oldDisplayValue;  // 关键：记录可读的显示值
    private Object newValue;
    private String newDisplayValue;  // 关键：记录可读的显示值

    private String operationType;
    private String operator;
    private LocalDateTime operateTime;
}
```

**审计日志示例：**
```
用户 [张三] 于 2026-03-08 13:59:07 修改了用户 [李四] 的信息：
- 部门：研发部 → 市场部
- 角色：开发工程师 → 产品经理
```

### 7.5 软删除 UI 标记

**场景：** 用户的部门已被软删除，但用户记录仍保留 departmentId，需要在 UI 上特殊标记

**实现方案：**

#### 后端实现

```java
@Service
public class AssociationStatusChecker {

    /**
     * 检查关联实体的状态（是否已删除）
     */
    public Map<String, Map<Object, AssociationStatus>> checkAssociationStatus(
            EntityMeta meta,
            List<Object> entities) {

        Map<String, Map<Object, AssociationStatus>> result = new HashMap<>();

        List<FieldMeta> foreignKeyFields = meta.getFields().stream()
            .filter(FieldMeta::isForeignKey)
            .toList();

        for (FieldMeta field : foreignKeyFields) {
            // 收集所有关联 ID
            Set<Object> ids = entities.stream()
                .map(entity -> extractField(entity, field.getName()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

            if (ids.isEmpty()) continue;

            // 查询关联实体（包括已软删除的）
            EntityMeta referencedMeta = entityMetaRegistry.getByEntityName(field.getReferencedEntity());
            Map<Object, AssociationStatus> statusMap = checkStatus(referencedMeta, ids);

            result.put(field.getName(), statusMap);
        }

        return result;
    }

    private Map<Object, AssociationStatus> checkStatus(EntityMeta meta, Set<Object> ids) {
        // 查询所有实体（包括软删除的）
        String jpql = String.format("SELECT e FROM %s e WHERE e.id IN :ids", meta.getEntityName());
        List<Object> entities = entityManager.createQuery(jpql)
            .setParameter("ids", ids)
            .getResultList();

        return entities.stream()
            .collect(Collectors.toMap(
                entity -> extractField(entity, "id"),
                entity -> {
                    boolean deleted = isDeleted(entity);
                    String displayValue = String.valueOf(extractField(entity, getDisplayField(meta)));
                    return new AssociationStatus(deleted, displayValue);
                }
            ));
    }
}

// 状态 DTO
public record AssociationStatus(
    boolean deleted,
    String displayValue
) {}

// 在列表查询时返回状态
@GetMapping("/{pathSegment}")
public Result<Page<Map<String, Object>>> list(...) {
    Page<Object> entities = entityCrudService.list(meta, pageable, criteria);

    // 检查关联实体状态
    Map<String, Map<Object, AssociationStatus>> statusMap =
        associationStatusChecker.checkAssociationStatus(meta, entities.getContent());

    // 在 DTO 中包含状态信息
    Page<Map<String, Object>> result = entities.map(entity -> {
        Map<String, Object> dto = toDto(entity, meta);

        // 添加关联状态
        for (FieldMeta field : meta.getFields()) {
            if (field.isForeignKey()) {
                Object id = dto.get(field.getName());
                AssociationStatus status = statusMap.get(field.getName()).get(id);
                dto.put(field.getName() + "_status", status);
            }
        }

        return dto;
    });

    return Result.success(result);
}
```

#### 前端实现

```vue
<template>
  <el-table-column label="部门">
    <template #default="{ row }">
      <span v-if="row.departmentId_status?.deleted" class="deleted-association">
        {{ row.departmentId_status.displayValue }}
        <el-tag type="danger" size="small">已删除</el-tag>
      </span>
      <span v-else>
        {{ row.departmentId_display }}
      </span>
    </template>
  </el-table-column>
</template>

<style scoped>
.deleted-association {
  color: #999;
  text-decoration: line-through;
}
</style>
```

### 7.6 技术债务优先级

| 优先级 | 编号 | 功能 | 影响范围 | 实施难度 |
|--------|------|------|----------|----------|
| P0 | 13 | 循环引用检测 | 树形结构 | 中 |
| P0 | 14 | 缓存失效 | 所有关联 | 中 |
| P1 | 15 | 审计日志 | 合规要求 | 低 |
| P1 | 16 | 软删除标记 | 用户体验 | 中 |
| P2 | - | 大数据量优化 | 性能 | 高 |

### 7.7 实施建议

1. **循环引用检测**：在树形实体的更新操作中强制执行
2. **缓存失效**：使用 Spring Cache + WebSocket 实现
3. **审计日志**：使用 AOP 拦截更新操作，异步记录
4. **软删除标记**：在 DTO 转换时附加状态信息
5. **大数据量优化**：根据实际数据量选择合适的方案
