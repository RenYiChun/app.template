# 注解系统迁移指南

## 概述

本文档提供了从旧注解系统迁移到新注解系统的完整指南。新注解系统将注解分为四大类，提供了更清晰、更强大的元数据定义能力。

## 新注解系统架构

### 四大注解分类

1. **`@DataforgeEntity`** (类级别) - 实体身份与全局行为配置
2. **`@DataforgeField`** (字段级别) - 字段元数据与UI展示配置
3. **`@DataforgeExport`** **/** **`@DataforgeImport`** (字段级别) - 导入导出配置
4. **`@DataforgeDto`** (字段/类级别) - DTO生成与父类字段控制

## 迁移映射表

### 1. 实体注解迁移

| 旧属性/注解                  | 新属性/注解                                  | 说明        |
| ----------------------- | --------------------------------------- | --------- |
| `@DataforgeEntity` 全部属性 | `@DataforgeEntity` 同名属性                 | 完全保留，向后兼容 |
| -                       | `@DataforgeEntity.description`          | 新增：实体描述   |
| -                       | `@DataforgeEntity.defaultSortField`     | 新增：默认排序字段 |
| -                       | `@DataforgeEntity.defaultSortDirection` | 新增：默认排序方向 |
| -                       | `@DataforgeEntity.treeEntity`           | 新增：树形实体支持 |
| -                       | `@DataforgeEntity.softDelete`           | 新增：软删除支持  |
| -                       | 等40+个生产级属性                              | 完整列表见注解定义 |

### 2. 字段注解迁移

| 旧注解/属性              | 新注解/属性                               | 迁移示例                                                                               |
| ------------------- | ------------------------------------ | ---------------------------------------------------------------------------------- |
| `@Searchable`       | `@DataforgeField(searchable = true)` | `@Searchable` → `@DataforgeField(searchable = true)`                               |
| `@Searchable.label` | `@DataforgeField.label`              | `@Searchable(label = "用户名")` → `@DataforgeField(label = "用户名", searchable = true)` |
| `@Searchable.order` | `@DataforgeField.order`              | `@Searchable(order = 1)` → `@DataforgeField(order = 1, searchable = true)`         |
| -                   | `@DataforgeField.columnSortable`     | 新增：表格列排序支持                                                                         |
| -                   | `@DataforgeField.component`          | 新增：表单组件类型                                                                          |
| -                   | `@DataforgeField.required`           | 新增：UI必填验证                                                                          |
| -                   | 等80+个字段属性                            | 完整列表见注解定义                                                                          |

### 3. 导出导入注解迁移

| 旧注解                | 新注解                                 | 迁移示例                                                                                      |
| ------------------ | ----------------------------------- | ----------------------------------------------------------------------------------------- |
| `@ExportExclude`   | `@DataforgeExport(enabled = false)` | `@ExportExclude` → `@DataforgeExport(enabled = false)`                                    |
| `@ExportConverter` | `@DataforgeExport(converter = ...)` | `@ExportConverter(MyConverter.class)` → `@DataforgeExport(converter = MyConverter.class)` |
| -                  | `@DataforgeImport`                  | 新增：导入配置注解                                                                                 |

### 4. DTO控制注解迁移

| 旧注解                                 | 新注解                                         | 迁移示例                                                                              |
| ----------------------------------- | ------------------------------------------- | --------------------------------------------------------------------------------- |
| `@DtoExcludeFrom(DtoType.RESPONSE)` | `@DataforgeDto(exclude = DtoType.RESPONSE)` | `@DtoExcludeFrom(DtoType.RESPONSE)` → `@DataforgeDto(exclude = DtoType.RESPONSE)` |
| -                                   | `@DataforgeDto(include = ...)`              | 新增：包含指定DTO类型                                                                      |
| -                                   | `@DataforgeDto(readOnly = true)`            | 新增：只读字段快捷方式                                                                       |
| -                                   | `@DataforgeDto.parentOverrides`             | 新增：父类字段覆盖配置                                                                       |

## 分阶段迁移策略

### 阶段一：注解定义 (已完成)

✅ 已完成以下工作：

1. 创建了新注解定义文件：

   * `DataforgeField.java` - 字段元数据注解

   * `DataforgeExport.java` - 导出配置注解

   * `DataforgeImport.java` - 导入配置注解

   * `DataforgeDto.java` - DTO控制注解
2. 创建了相关枚举：

   * `ColumnAlign.java`, `ColumnFixed.java` - 表格对齐与固定

   * `FormComponent.java` - 表单组件类型

   * `SearchType.java`, `SearchComponent.java` - 搜索类型与组件

   * `MaskType.java`, `SortDirection.java` - 脱敏类型与排序方向
3. 扩展了现有注解：

   * `DataforgeEntity.java` - 添加40+个生产级属性

   * `DtoType.java` - 扩展DTO类型枚举
4. 标记旧注解为废弃：

   * `@Searchable` - 使用 `@DataforgeField(searchable = true)`

   * `@ExportConverter` - 使用 `@DataforgeExport(converter = ...)`

   * `@ExportExclude` - 使用 `@DataforgeExport(enabled = false)`

   * `@DtoExcludeFrom` - 使用 `@DataforgeDto(exclude = ...)`

### 阶段二：元数据模型更新 (待进行)

需要更新以下元数据模型以支持新注解属性：

#### 1. EntityMeta 更新

```java
// 需要添加的属性示例
public class EntityMeta {
    private String description;
    private String defaultSortField;
    private SortDirection defaultSortDirection;
    private int defaultPageSize;
    private int[] pageSizeOptions;
    private boolean treeEntity;
    private String treeParentField;
    private String treeChildrenField;
    // ... 其他40+个属性
}
```

#### 2. FieldMeta 更新

```java
public class FieldMeta {
    private String label;
    private String description;
    private int order;
    private boolean columnVisible;
    private boolean columnSortable;  // 用户特别关注的属性
    private int columnWidth;
    private ColumnAlign columnAlign;
    private FormComponent component;
    private boolean required;
    private boolean searchable;
    private SearchType searchType;
    private String format;
    private MaskType maskType;
    // ... 其他80+个属性
}
```

#### 3. 处理器更新

* `MetaScanner.java` - 解析新注解，填充元数据模型

* `DtoGeneratorProcessor.java` - 支持新的DTO生成逻辑

### 阶段三：前端适配 (待进行)

前端需要适配新的元数据结构：

1. 表格组件：支持 `columnSortable`, `columnWidth`, `columnAlign` 等属性
2. 表单组件：支持 `component`, `required`, `placeholder` 等属性
3. 搜索组件：支持 `searchType`, `searchComponent` 等属性

### 阶段四：渐进迁移

1. **新实体使用新注解**：新开发的实体直接使用新注解系统
2. **旧实体逐步迁移**：分批次将旧实体迁移到新注解系统
3. **双向兼容**：在一段时间内支持新旧注解同时存在

## 完整迁移示例

### 迁移前 (旧注解系统)

```java
@DataforgeEntity(
    pathSegment = "users",
    displayName = "用户管理",
    description = "系统用户管理",
    defaultSortField = "createTime",
    defaultSortDirection = SortDirection.DESC,
    defaultPageSize = 20,
    softDelete = true
)
@DataforgeDto(
    parentOverrides = {
        @DataforgeDto(parentFieldName = "createTime", include = DtoType.RESPONSE),
        @DataforgeDto(parentFieldName = "id", include = {DtoType.UPDATE, DtoType.RESPONSE})
    },
    ignoreFields = {"password"}
)
public class User extends BaseEntity<Long> {
    
    @DataforgeField(
        label = "用户名",
        order = 1,
        columnSortable = true,
        columnWidth = 150,
        required = true,
        component = FormComponent.TEXT,
        searchable = true,
        searchType = SearchType.LIKE,
        minLength = 3,
        maxLength = 50
    )
    @DataforgeDto(include = {DtoType.CREATE, DtoType.UPDATE, DtoType.QUERY})
    private String username;
    
    @DataforgeField(
        label = "昵称", 
        order = 2,
        columnSortable = true,
        searchable = true
    )
    private String nickname;
    
    @DataforgeField(
        label = "密码",
        order = 3,
        component = FormComponent.PASSWORD,
        sensitive = true,
        masked = true
    )
    @DataforgeDto(exclude = DtoType.RESPONSE)
    @DataforgeExport(enabled = false)
    private String password;
    
    @DataforgeField(
        label = "邮箱",
        order = 4,
        component = FormComponent.EMAIL,
        searchable = true,
        searchType = SearchType.LIKE
    )
    private String email;
    
    @DataforgeField(
        label = "手机号",
        order = 5,
        component = FormComponent.TEXT,
        searchable = true,
        maskType = MaskType.PHONE
    )
    private String phone;
    
    @DataforgeField(
        label = "部门",
        order = 6,
        component = FormComponent.SELECT,
        foreignKey = true,
        referencedEntity = "Department",
        displayField = "name",
        valueField = "id"
    )
    private Long departmentId;
    
    @DataforgeField(
        label = "状态",
        order = 7,
        component = FormComponent.SELECT,
        dictCode = "USER_STATUS",
        columnSortable = true,
        searchable = true,
        searchType = SearchType.EQUALS,
        searchComponent = SearchComponent.SELECT
    )
    @DataforgeExport(converter = StatusConverter.class, width = 100)
    private String status;
    
    @DataforgeField(
        label = "头像",
        order = 8,
        component = FormComponent.UPLOAD
    )
    private String avatar;
}
```

### 迁移后 (新注解系统)

```java
@DataforgeEntity(
    pathSegment = "users",
    displayName = "用户管理",
    description = "系统用户管理",
    defaultSortField = "createTime",
    defaultSortDirection = SortDirection.DESC,
    defaultPageSize = 20,
    softDelete = true
)
@DataforgeDto(
    parentOverrides = {
        @DataforgeDto(parentFieldName = "createTime", include = DtoType.RESPONSE),
        @DataforgeDto(parentFieldName = "id", include = {DtoType.UPDATE, DtoType.RESPONSE})
    },
    ignoreFields = {"password"}
)
public class User extends BaseEntity<Long> {
    
    @DataforgeField(
        label = "用户名",
        order = 1,
        columnSortable = true,
        columnWidth = 150,
        required = true,
        component = FormComponent.TEXT,
        searchable = true,
        searchType = SearchType.LIKE,
        minLength = 3,
        maxLength = 50
    )
    @DataforgeDto(include = {DtoType.CREATE, DtoType.UPDATE, DtoType.QUERY})
    private String username;
    
    @DataforgeField(
        label = "昵称", 
        order = 2,
        columnSortable = true,
        searchable = true
    )
    private String nickname;
    
    @DataforgeField(
        label = "密码",
        order = 3,
        component = FormComponent.PASSWORD,
        sensitive = true,
        masked = true
    )
    @DataforgeDto(exclude = DtoType.RESPONSE)
    @DataforgeExport(enabled = false)
    private String password;
    
    @DataforgeField(
        label = "邮箱",
        order = 4,
        component = FormComponent.EMAIL,
        searchable = true,
        searchType = SearchType.LIKE
    )
    private String email;
    
    @DataforgeField(
        label = "手机号",
        order = 5,
        component = FormComponent.TEXT,
        searchable = true,
        maskType = MaskType.PHONE
    )
    private String phone;
    
    @DataforgeField(
        label = "部门",
        order = 6,
        component = FormComponent.SELECT,
        foreignKey = true,
        referencedEntity = "Department",
        displayField = "name",
        valueField = "id"
    )
    private Long departmentId;
    
    @DataforgeField(
        label = "状态",
        order = 7,
        component = FormComponent.SELECT,
        dictCode = "USER_STATUS",
        columnSortable = true,
        searchable = true,
        searchType = SearchType.EQUALS,
        searchComponent = SearchComponent.SELECT
    )
    @DataforgeExport(converter = StatusConverter.class, width = 100)
    private String status;
    
    @DataforgeField(
        label = "头像",
        order = 8,
        component = FormComponent.UPLOAD
    )
    private String avatar;
}
```

## 关键优势

### 1. 逻辑清晰

* 四大注解分类，职责分离

* 避免功能耦合，易于理解和维护

### 2. 生产级功能

* 表格列排序 (`columnSortable`)

* 表单验证 (`required`, `regex`, `minLength`等)

* 脱敏显示 (`maskType`)

* 数据字典支持 (`dictCode`)

* 树形实体支持 (`treeEntity`)

* 软删除支持 (`softDelete`)

### 3. 易于扩展

* 枚举驱动的设计，易于添加新的组件类型、搜索类型等

* 注解属性分组清晰，便于后续添加新属性

### 4. 平滑迁移

* 旧注解标记为废弃但继续工作

* 提供完整的迁移映射表

* 支持渐进式迁移

## 下一步工作

1. **更新元数据模型**：EntityMeta, FieldMeta等类的更新
2. **更新处理器**：MetaScanner, DtoGeneratorProcessor等
3. **前端适配**：更新UI组件以支持新元数据
4. **文档完善**：API文档和用户指南更新
5. **示例项目**：更新template-dataforge-sample项目

## 注意事项

1. 新注解系统需要Java 8+支持
2. 迁移期间建议同时支持新旧注解系统
3. 建议先在新实体上试用，再迁移旧实体
4. 生产环境迁移前充分测试

