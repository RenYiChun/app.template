# 实体关联使用与配置

> 关联能力说明见 [实体关联设计](design/entity-association-design.md)，API 细节见 [实体关联 API 规范](design/entity-association-api-specification.md)。

## 1. 配置示例

- **referencedEntity**：使用 **entityName**（类名），不要用 pathSegment。
  - 正确：`referencedEntity = "Department"`
  - 错误：`referencedEntity = "departments"`（pathSegment 用于 CRUD URL，不用于关联解析）

- **cascadeDelete**：按业务明确配置。
  - `RESTRICT`：存在引用时禁止删除（默认）
  - `SET_NULL`：删除时把引用方外键置空
  - `CASCADE`：删除时级联删除引用方

- **exportFormat**：导出 Excel 时关联列写什么。
  - `id`：写关联 ID（默认）
  - `display`：写显示值（如名称），需批量查关联实体

## 2. entityName 与 pathSegment

- **CRUD 接口**（列表/详情/创建/更新/删除）：路径使用 **pathSegment**，如 `GET /api/departments`。
- **options / tree / batch-lookup**：路径使用 **entityName**，如 `GET /api/Department/options`。

详见 [API 规范 - entityName vs pathSegment](design/entity-association-api-specification.md)。

## 3. 导入与 displayField 唯一性

- 导入时关联列通常填「显示值」（如名称），后端用 `ImportAssociationResolver.preloadDisplayToId` 预加载「显示值 → ID」映射，行内用 `resolveId` 解析。
- **建议**：被引用实体的 displayField（如 name）在业务上尽量唯一，否则预加载重名时保留第一个，可能导致错误匹配。

## 4. 错误码（3000–3071）

| 错误码 | 常量 | 说明 |
|--------|------|------|
| 3000 | ENTITY_NOT_FOUND | 实体不存在（options/tree/batch-lookup 按 entityName 未找到） |
| 3001 | ASSOCIATION_TARGET_NOT_FOUND | 关联目标不存在或已软删 |
| 3010 | CASCADE_DELETE_RESTRICT | 存在关联数据，禁止删除 |
| 3020 | IMPORT_ASSOCIATION_NOT_FOUND | 导入时关联显示值找不到对应 ID（含行号/字段/值） |
| 3041 | BATCH_LOOKUP_IDS_OVERFLOW | 单次 batch-lookup ID 数量超限（如 1000） |
| 3070 | IMPORT_ASSOCIATION_PRELOAD_FAILED | 导入时关联预加载失败 |
| 3071 | CIRCULAR_REFERENCE | 树形实体形成循环引用 |

前端可使用 headless 的 `DATAFORGE_ERROR_CODES`、`getDataforgeErrorMessage` 做统一展示。

## 5. 前端调用示例

- **options**：`client.getOptions(entityName, { query, page, size, sort })`，用于关联下拉、远程搜索。
- **tree**：`client.getTree(entityName, { parentId, maxDepth, includeDisabled })`，用于树形选择。
- **batch-lookup**：`client.batchLookup(entityName, ids, { fields: 'id,name,code' })`，用于表格列显示值；可选 `fields` 控制返回字段。

**缓存与失效**：使用 `AssociationCache` 包装 client，按 entityName + 参数缓存；在创建/更新/删除成功后调用 `invalidateEntity(entityName)`（或 `invalidateOptions`/`invalidateTree`/`invalidateBatchLookup`）以保持数据一致。后端可选实现 `EntityChangeNotifier`，用于 WebSocket 推送等。

## 6. options / 树 / 搜索优化

- options：接口支持 `size`（最大 100），前端建议 remote 搜索 + 较小 size（如 50）避免一次加载过多。
- tree：接口支持 `maxDepth`，可按需限制深度。
- 列表关联列：后端已为 foreignKey 填充 `_display`（及软删时的 `_status`），表格优先显示 `_display`，软删显示「已删除」样式。
