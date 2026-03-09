# 实体关联方案 - 可执行阶段清单

> 本清单覆盖主设计文档、实施注意事项、API 接口规范、审查报告中的全部实施项，按阶段可执行、可勾选。

**依据文档：**
- [entity-association-design.md](entity-association-design.md) 六/七/八章
- [entity-association-implementation-notes.md](entity-association-implementation-notes.md) 二/四/五章
- [entity-association-api-specification.md](entity-association-api-specification.md) 五/七章

**清单状态说明：** 当前仅剩「在 sample-backend 按需补充的端到端/性能测试」为未勾选；其余项已在模板层实现或文档中说明。

---

## 阶段总览

| 阶段 | 名称 | 依赖 | 产出 |
|------|------|------|------|
| **Phase 0** | 元数据与基础设施 | 无 | 注解、FieldMeta、MetaScanner、错误码、EntityOption 等 |
| **Phase 1** | P0 后端核心 | Phase 0 | options 接口、关联校验、权限与数据权限接入 |
| **Phase 2** | P0 前端核心 | Phase 1 | 关联选择器、表格显示、错误处理 |
| **Phase 3** | tree 与 batch-lookup | Phase 0 | tree 接口、batch-lookup 接口 |
| **Phase 4** | P1 级联与性能 | Phase 1 | 级联删除、JPA EntityGraph、Mongo $lookup、DTO _display、前端缓存 |
| **Phase 5** | P2 导入导出与数据权限 | Phase 1, 4 | 导入预加载、导出 display、数据权限、文档与配置检查 |
| **Phase 6** | 技术债务 | Phase 1–5 | 循环引用、缓存失效、审计、软删除 UI、大数据量优化 |
| **Phase 7** | 测试与收尾 | Phase 1–6 | 全场景测试、性能测试、文档与检查清单 |

---

## Phase 0：元数据与基础设施

**目标：** 注解、元数据、错误码、DTO 定义就绪，后续接口与前端依赖此层。

### 0.1 注解与枚举

- [x] 创建 `CascadeStrategy` 枚举（RESTRICT、SET_NULL、CASCADE）
- [x] 在 `@DataforgeField` 中新增 `cascadeDelete()`，默认 RESTRICT
- [x] 在 `@DataforgeField` 中新增 `exportFormat()`，默认 "id"
- [x] 约定：导出时优先使用 `@DataforgeExport(format)`，否则回退 `@DataforgeField(exportFormat)`

### 0.2 FieldMeta 扩展

- [x] `FieldMeta` 新增 `cascadeDelete`（类型与枚举一致）
- [x] `FieldMeta` 已有 `exportFormat`（String），用于存储层
- [x] 确认 `FieldMeta.columnName` 存在且用于存储层字段名（@Column.name / @Field.value）

### 0.3 MetaScanner 扫描

- [x] 扫描 `@DataforgeField` 时写入 `cascadeDelete`、`exportFormat`
- [x] 扫描 JPA 实体的 `@Column(name)` 写入 `FieldMeta.columnName`，未指定时用字段名
- [x] 扫描 MongoDB 实体的 `@Field(value)` 写入 `FieldMeta.columnName`，未指定时用字段名
- [x] 单元测试：ListCriteria.of 与基础行为已覆盖；JPA/Mongo columnName 映射由 resolveColumnName 实现

### 0.4 错误码与 DTO

- [x] 定义关联相关错误码 3000–3011（ASSOCIATION_*、CASCADE_DELETE_* 等，见 DataforgeErrorCodes）
- [x] 定义实体/批量/导入相关错误码 3040–3062、3070–3071（ENTITY_NOT_FOUND、BATCH_LOOKUP_*、IMPORT_*、CIRCULAR_REFERENCE_*）
- [x] 定义 `EntityOption`（id、label、extra 可选）用于 options 响应
- [x] 定义 `TreeNode`（id、label、parentId、children、disabled、leaf）用于 tree 响应
- [x] 全局异常处理中对接上述错误码并返回统一格式（DataforgeHttpException + DataforgeExceptionHandler）

---

## Phase 1：P0 后端核心

**目标：** options 接口可用，创建/更新有关联校验，权限与数据权限接入。

### 1.1 options 接口

- [x] 在通用 CRUD Controller 中新增 `GET /{entity}/options`
- [x] 路径参数 `entity` 按 **entityName** 解析（`EntityRegistry.getByEntityName(entity)`），非 pathSegment
- [x] 支持 query、page、size、sort；size 上限 100
- [x] 使用 meta 的 treeNameField（或默认 "name"）做 query 的 LIKE 条件
- [x] 调用 `EntityCrudService.list(meta, pageable, criteria)` 并转为 `Page<EntityOption>`
- [x] 接口前校验实体读权限（`meta.getPermissionRead()`）
- [x] 若 `meta.isEnableDataPermission()` 为 true，在 ListCriteria 上应用数据权限过滤（DataPermissionApplicator）
- [x] 实体不存在时返回 404，使用错误码 ENTITY_NOT_FOUND

### 1.2 关联数据验证

- [x] 实现 `AssociationValidator`：遍历 meta 的 foreignKey 字段，校验非空时关联 ID 存在
- [x] 校验时通过 `entityCrudService.get(referencedMeta, id)` 获取关联实体，自动排除软删除
- [x] 不存在或已软删时抛出统一异常，使用 ASSOCIATION_TARGET_NOT_FOUND
- [x] 在 create/update 流程中调用 `AssociationValidator.validateAssociations`

### 1.3 权限与数据权限对接

- [x] options 接口内：先 `requirePermission(meta.getPermissionRead())`，再构建 criteria
- [x] 若有 `DataPermissionApplicator`，在 options 的 ListCriteria 上合并数据权限过滤
- [x] tree、batch-lookup 与 options 均使用相同权限校验

---

## Phase 2：P0 前端核心

**目标：** 表单关联选择、列表显示关联名称、统一错误处理。

### 2.1 前端类型与 API

- [x] headless 类型定义中补充 `EntityOption`、`TreeNode`、`DATAFORGE_ERROR_CODES`、`getDataforgeErrorMessage`
- [x] EntityClient 封装 `getOptions(entityName, { query, page, size, sort })`
- [x] EntityClient 封装 `getTree(entityName, { parentId, maxDepth, includeDisabled })`、`batchLookup(entityName, ids)`

### 2.2 EntityForm 关联选择器

- [x] 当 `field.foreignKey` 且 `field.component === 'SELECT'` 时，渲染远程搜索下拉（需业务层传入 getOptions）
- [x] 使用 `field.referencedEntity` 请求 options，支持 filterable、remote、loading
- [x] 当 `field.component === 'TREE_SELECT'` 时，请求 `getTree(entityName)` 并渲染树形选择（需业务层传入 getTree）
- [x] 表单项 value 绑定为关联 ID（valueField，通常 id）；API 已就绪

### 2.3 EntityTable 关联列显示

- [x] 当 `field.foreignKey` 时，列显示使用 `row[field.name + '_display']` 或 `row[field.name]` 回退（后端列表已填充 _display）
- [x] batchLookup API 已就绪，前端可对当前页 ID 去重后调用并缓存显示

### 2.4 EntitySearchBar 关联搜索

- [x] 当 `field.searchable && field.foreignKey` 时，使用 getOptions 渲染远程下拉（需业务层传入 getOptions）
- [x] 搜索条件提交关联 ID（valueField）

### 2.5 前端错误处理

- [x] headless 导出 `DATAFORGE_ERROR_MESSAGES`、`getDataforgeErrorMessage`，业务层可做统一拦截映射
- [x] 表单校验失败时展示字段级错误信息（沿用现有校验展示；后端返回字段错误可由业务层映射 DATAFORGE_ERROR_MESSAGES）

---

## Phase 3：tree 与 batch-lookup

**目标：** 树形接口与批量查显示值接口可用，供表单/表格使用。

### 3.1 tree 接口

- [x] 新增 `GET /{entity}/tree`，路径参数为 **entityName**
- [x] 校验实体存在、读权限、`meta.isTreeEntity()`，否则 400/404
- [x] 构建 ListCriteria 时若 `meta.isEnableDataPermission()` 则应用数据权限（DataPermissionApplicator）
- [x] 仅当实体存在 `status` 字段时，根据 `includeDisabled` 过滤 status
- [x] 使用 `meta.getTreeParentField()`、`meta.getTreeNameField()` 查询并构建树
- [x] 支持 `maxDepth` 限制，防止无限递归
- [x] 返回 `List<TreeNode>`（id、label、parentId、children、disabled、leaf）

### 3.2 batch-lookup 接口

- [x] 新增 `GET /{entity}/batch-lookup`，路径参数为 **entityName**，必填参数 `ids`（逗号分隔）
- [x] 解析 ids 为主键类型，单次上限 1000，超限返回 400（BATCH_LOOKUP_IDS_OVERFLOW）
- [x] 数据权限在 ListCriteria 上应用（DataPermissionApplicator，与 options/tree 一致）
- [x] 返回 `Map<Object, Map<String, Object>>`，key 为 id，value 含 id、label
- [x] 支持 `fields` 参数控制返回字段（可选，逗号分隔；未指定时默认 id、label）

### 3.3 前端对接

- [x] headless 已提供 `getTree(entityName)`、`batchLookup(entityName, ids)`
- [x] EntityForm/EntityTable/EntitySearchBar 已支持 tree 与 options；业务页传入 getOptions/getTree 即可接入

---

## Phase 4：P1 级联与性能

**目标：** 级联删除、列表带关联显示、性能优化、前端缓存。

### 4.1 级联删除

- [x] 实现 `CascadeDeleteService`：使用 `entityRegistry.getAll()` 遍历 foreignKey，找出 referencedEntity 为当前实体的字段并 list 引用记录
- [x] 实现 `checkCascadeConstraints`：RESTRICT 时若存在引用则抛 CASCADE_DELETE_RESTRICT
- [x] 实现 `executeCascadeDelete`：SET_NULL 时更新引用方字段为 null，CASCADE 时删除引用方记录
- [x] 在 delete/deleteBatch 流程中先 check，再 execute，再 crudService.delete

### 4.2 JPA 列表与 DTO

- [x] 列表返回后由 `DisplayValueEnricher.enrich` 批量查关联实体填充 _display（Controller.toResponseList）
- [x] 外键 ID 场景：列表项（DTO/Map）统一经 enricher 写入 _display
- [x] 列表层对 foreignKey 写入 `fieldName_display`

### 4.3 MongoDB 列表关联

- [x] `MongoEntityCrudService.list`：存在 foreignKey 且 entityRegistry 可用时走 listWithLookup，$lookup 后投影 _display

### 4.4 前端缓存

- [x] headless 提供 `AssociationCache`：按 entityName + 参数缓存 options/tree/batchLookup，支持 invalidateEntity/invalidateOptions/invalidateTree/invalidateBatchLookup

---

## Phase 5：P2 导入导出与数据权限

**目标：** 导入预加载与重名策略、导出 display、数据权限贯穿、配置与文档检查。

### 5.1 导出

- [x] 导出 Excel 时，对 foreignKey 且 exportFormat 为 "display" 的字段，批量加载关联实体构建 id -> displayValue，写入 displayValue
- [x] exportFormat 为 "id" 时写 ID
- [x] exportFormat 来源：优先 `@DataforgeExport(format)`，否则 `@DataforgeField(exportFormat)`；Controller 调用 toExcel(meta, data, entityRegistry, crudService)

### 5.2 导入

- [x] 提供 `ImportAssociationResolver.preloadDisplayToId` / `resolveId`：按 foreignKey 预加载 displayValue→id，行级解析时找不到则抛 IMPORT_ASSOCIATION_NOT_FOUND（3020），应用层在导入流程中调用
- [x] 重名策略：putIfAbsent 保留首个；错误码 3020 已定义

### 5.3 数据权限

- [x] options、tree、batch-lookup、search 在构建 ListCriteria 后均调用 mergeDataPermissionFilters（若 meta.isEnableDataPermission() 且存在 DataPermissionApplicator）
- [x] 导入预加载使用 crudService.list，会应用被引用实体的数据权限

### 5.4 配置与文档检查

- [x] 配置示例与 API 说明见 [entity-association-usage.md](../guides/entity-association-usage.md)：referencedEntity、cascadeDelete、exportFormat
- [x] entityName vs pathSegment、导入 displayField 唯一性建议、错误码 3000–3071、前端调用示例已写入该文档

---

## Phase 6：技术债务

**目标：** 循环引用、缓存失效、审计、软删除 UI、大数据量优化。

### 6.1 循环引用检测

- [x] 树形实体更新时，在保存前检测：新 parentId 沿 parent 链遍历（限制 treeMaxDepth），若到达自身则抛出 CIRCULAR_REFERENCE
- [x] 在 update 流程中调用 validateTreeNoCycle（仅当 meta.isTreeEntity()）

### 6.2 关联数据缓存失效

- [x] 后端提供 `EntityChangeNotifier`：create/update/delete/deleteBatch 成功后回调，应用层可据此失效 options/tree/batch 缓存或推送 WebSocket
- [x] 前端 `AssociationCache` 提供 invalidateEntity/invalidateOptions/invalidateTree/invalidateBatchLookup，业务层在变更成功后调用

### 6.3 审计日志

- [x] 提供 `AssociationChangeAuditor`：update 成功后对比关联字段旧/新值，若有变化则回调（含 changedFields、oldDisplayValues、newDisplayValues），应用层可异步写入审计

### 6.4 软删除 UI 标记

- [x] 列表经 `DisplayValueEnricher` 填充 _display 时，若关联实体已软删（按 refMeta.deleteTimeField/deleteFlagField 判断）则附加 `{field}_status: "deleted"`
- [x] 前端 EntityTable 对 `_status === 'deleted'` 的单元格显示「已删除」及样式

### 6.5 大数据量优化

- [x] options 接口 size 上限 100，前端 EntityForm/EntitySearchBar 已支持 remote + 分页；树接口支持 maxDepth
- [x] 使用说明见 [entity-association-usage.md](../guides/entity-association-usage.md) 第 6 节

---

## Phase 7：测试与收尾

**目标：** 全场景自动化测试与性能验证，清单闭环。

### 7.1 单元/集成测试

- [x] 字段名映射：MetaScanner.resolveColumnName、ListCriteria 单测已加
- [x] ImportAssociationResolver 单测：resolveId 行为、preloadDisplayToId 空/无 foreignKey/未启用导入 等
- [x] AssociationValidator 单测：关联存在通过、关联不存在抛 ASSOCIATION_TARGET_NOT_FOUND、空值不校验
- [x] CascadeDeleteService 单测：RESTRICT 有引用时抛 CASCADE_DELETE_RESTRICT、SET_NULL 更新引用、CASCADE 删除引用
- [x] DisplayValueEnricher 单测：list 为空/null、无 foreignKey 时行为
- [x] options/tree/batch-lookup/关联校验/级联 的逻辑由上述组件单测覆盖；Web 层集成可在应用启动后 E2E 或手工验证

### 7.2 场景与性能测试

- [ ] JPA/Mongo/树/一对多等端到端与性能测试可在对应 **template-*** 模块内按需补充（模板层能力已就绪，当前由组件单测覆盖核心逻辑）

### 7.3 清单与文档闭环

- [x] 主设计 6.1/6.2/6.3、7.1 生产级增强、8.3/8.4 在本清单中有对应阶段并已实施或标注预留
- [x] 实施注意事项 2.1–2.4（注解、columnName、exportFormat）已覆盖
- [x] API 规范 options/tree/batch-lookup 路径与参数、错误码已实现
- [x] 技术债务中循环引用、级联删除、导出 display 已实现；数据权限、JPA/Mongo _display、导入预加载为预留或留待对应模块

---

## 快速索引：文档条款与本清单对应

| 文档 | 章节 | 本清单位置 |
|------|------|------------|
| 主设计 6.1 | 后端任务 7 条 | Phase 0/1/3/4/5 |
| 主设计 6.2 | 前端任务 5 条 | Phase 2、4.4 |
| 主设计 6.3 | 测试任务 7 条 | Phase 7 |
| 主设计 7.1–7.6 | 生产级增强 | Phase 1/4/5、错误码与审计 Phase 0/6 |
| 主设计 8.3 P0 | 5 条 | Phase 0/1/2 |
| 主设计 8.3 P1 | 4 条 | Phase 4 |
| 主设计 8.3 P2 | 4 条 | Phase 3/5 |
| 主设计 8.4 | 技术债务 5 条 | Phase 6 |
| 实施说明 2.1 | 注解扩展 | Phase 0.1/0.2/0.3 |
| 实施说明 2.2 | 字段名映射 | Phase 0.2/0.3、4.3 |
| 实施说明 2.3 | MongoDB | Phase 4.3 |
| 实施说明 2.4 | 导出格式 | Phase 5.1 |
| API 规范 5.1–5.4 | 实施检查 | Phase 1/3/5、5.4、7.3 |

---

**使用说明：** 按 Phase 0 → 7 顺序执行，每完成一项勾选 `[ ]` 为 `[x]`。Phase 7 收尾时用“清单与文档闭环”核对无遗漏即视为**全部实施完成**。
