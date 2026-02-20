# 注解系统重构与优化方案 (最终修订版)

## 1. 设计思路
*   **极简主义**：减少注解类的数量，复用核心注解。
*   **兼容性**：保留现有核心属性名称，确保平滑迁移。
*   **逻辑闭环**：通过 `@DataforgeDto` 一个注解统一解决“字段归属”和“父类覆盖”问题。

## 2. 核心注解定义

### 2.1. 实体定义 `@DataforgeEntity`
保留现有属性，聚焦实体行为配置。

| 属性名 (保持不变) | 类型 | 说明 | 新增/迁移说明 |
| :--- | :--- | :--- | :--- |
| `table` | String | 数据库表名 | 保持不变 |
| `pathSegment` | String | URL 路径片段 | 保持不变 |
| `displayName` | String | 显示名称 | **新增** (原可能散落在其他处) |
| `description` | String | 描述 | **新增** |
| `crudEnabled` | boolean | CRUD 总开关 | 保持不变 |
| `enableList`... | boolean | 各接口开关 | 保持不变 (List, Get, Create, Update, Delete) |
| `permissionCreate`... | String | 权限标识 | 保持不变 |
| `generateDtos` | boolean | DTO 生成总开关 | 保持不变 |

### 2.2. 字段元数据 `@DataforgeField`
整合原 `@Searchable` 的元数据部分，定义字段的基础 UI 属性。

| 属性名 | 原对应属性 | 说明 |
| :--- | :--- | :--- |
| `name` | `@Searchable.label` | 字段显示名称 (UI Label) |
| `order` | `@Searchable.order` | 排序权重 (UI & Export Order) |
| `component` | - | **新增** UI 组件类型 (`TEXT`, `SELECT` 等) |
| `required` | - | **新增** UI 校验必填 (非 DB 约束) |
| `masked` | - | **新增** 脱敏显示 |
| `tips` | - | **新增** 字段填写提示/Tooltip |

### 2.3. DTO 控制 `@DataforgeDto` (核心重构)
**全能注解**：既可以标在**字段**上控制归属，也可以标在**类**上控制全局和**父类覆盖**。

**属性列表：**
| 属性名 | 类型 | 说明 | 适用范围 |
| :--- | :--- | :--- | :--- |
| `value` / `include` | `DtoType[]` | 包含在哪些 DTO (CREATE, UPDATE, QUERY, RESPONSE) | 字段/父类覆盖 |
| `exclude` | `DtoType[]` | 排除出哪些 DTO | 字段/父类覆盖 |
| `fieldName` | String | **父类字段名** (仅在 `parentOverrides` 中使用) | 类级别 |
| `parentOverrides` | `@DataforgeDto[]` | **复用自身**，配置父类字段的 DTO 策略 | 类级别 |
| `ignoreFields` | String[] | 全局忽略的字段名列表 | 类级别 |

**使用示例：**

```java
@DataforgeEntity(displayName = "用户")
@DataforgeDto(parentOverrides = {
    // 场景：让父类的 createTime 字段出现在列表响应中
    @DataforgeDto(fieldName = "createTime", include = DtoType.RESPONSE),
    // 场景：让父类的 id 字段出现在更新请求中
    @DataforgeDto(fieldName = "id", include = DtoType.UPDATE)
})
public class User extends BaseEntity {

    @DataforgeField(name = "用户名", order = 1)
    @DataforgeDto(include = {DtoType.CREATE, DtoType.QUERY}) // 既是创建参数，也是搜索条件
    private String username;
    
    // ...
}
```

### 2.4. 导入导出 `@DataforgeExport` / `@DataforgeImport`
整合原 `@ExportConverter`, `@ExportExclude`。

**`@DataforgeExport`**
| 属性名 | 原对应属性 | 说明 |
| :--- | :--- | :--- |
| `enabled` | `!@ExportExclude` | 是否导出 (默认 true) |
| `header` | - | 导出表头 (默认取 `@DataforgeField.name`) |
| `order` | - | 导出顺序 (默认取 `@DataforgeField.order`) |
| `converter` | `@ExportConverter.value` | 值转换器 |
| `format` | - | **新增** 格式化字符串 |
| `width` | - | **新增** 列宽 |

**`@DataforgeImport`**
| 属性名 | 说明 |
| :--- | :--- |
| `required` | 导入必填 |
| `sample` | 模板示例数据 |
| `converter` | 值转换器 |

## 3. 迁移映射总结

1.  **`@Searchable`** -> 拆分：
    *   逻辑部分 -> `@DataforgeDto(include = QUERY)`
    *   元数据部分 (`label`, `order`) -> `@DataforgeField`
2.  **`@ExportConverter`** -> `@DataforgeExport(converter = ...)`
3.  **`@ExportExclude`** -> `@DataforgeExport(enabled = false)`
4.  **`@AuditLog`** -> 保持不变 (属于行为审计，独立于元数据)
5.  **`@DtoExcludeFrom`** -> `@DataforgeDto(exclude = ...)`

此方案最大程度保留了您熟悉的代码习惯，同时通过 `@DataforgeDto` 的自递归引用解决了父类字段配置的复杂性问题。
