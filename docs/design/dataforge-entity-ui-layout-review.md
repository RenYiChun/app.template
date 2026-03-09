# Dataforge 实体 UI 布局元数据设计 — 评审意见

> 对 [dataforge-entity-ui-layout.md](./dataforge-entity-ui-layout.md) 的评审，从一致性、可实施性、边界与扩展性等角度给出结论与建议。  
> **文档已按评审意见刷新**，以下“三、需要关注的问题”中多数已在设计文档中补充，仅剩一项可选约定见 3.1。

## 一、总体评价

设计目标清晰（统一元数据、左树右表、配置在主实体），非目标与边界写得很清楚，和现有 Dataforge 的 `EntityMeta`、`@DataforgeEntity` 扩展方式一致。**整体可采纳**。设计文档刷新后已补充“树形实体区分、元数据校验、树数据来源、rootSelectionMode 行为”等约定，实施路径清晰。

---

## 二、做得好的地方

- **配置归属明确**：布局挂在“主列表实体”（如 users）上，前端只读一份元数据即可决策，避免多实体推断。
- **结构化而非布尔**：用 `uiLayout` + `mode` + `MasterDetailTreeMeta` 表达树实体、父子/展示/排序字段、关联字段、根节点与子孙策略，可扩展且语义完整。
- **默认兼容**：未配置 `uiLayout` 时等价于现有表格行为，不破坏历史实体与页面。
- **首版范围收敛**：`includeDescendants` 用前端计算 `IN` 实现，不引入 `TREE_DESCENDANT_OF` 等新操作符，改动面可控。
- **风险与边界**：关联字段缺失、树实体非树、大树性能、数据权限等都有单独小节，便于实施时做校验与后续迭代。

---

## 三、需要关注的问题（与设计文档对照）

### 1. 与现有“树形实体”命名的统一 — ✅ 已采纳

设计文档已新增 **5.3 与现有“树形实体”元数据的关系**：明确区分“实体自身是树”与“列表页左侧挂树”，约定保留 `treeLabelField` 命名、可与 `treeNameField` 在注释中说明语义等价、并提醒前端勿混用两套配置。

---

### 2. `treeEntity` 的解析与校验时机 — ✅ 已采纳

设计文档已新增 **7.1 元数据校验规则**：在元数据构建阶段校验 `masterDetailTree` 必填、`treeEntity` 可解析、`relationField` 存在于主实体、树实体具备 `treeIdField`/`treeParentField`/`treeLabelField`（及可选 `treeSortField`），失败时抛异常并包含 pathSegment、treeEntity、字段名等信息。14.2 已与 7.1 对齐。

---

### 3. `rootSelectionMode = none` 的语义 — ✅ 已采纳

设计文档已新增 **8.5 `rootSelectionMode` 的行为约定**：`all` 与 `none` 的语义、右表是否首屏请求、以及“未来 URL/路由指定默认节点优先于 rootSelectionMode”的预留说明均已写明。

---

### 4. 前端“树数据”接口与现有 CRUD 的复用 — ✅ 已采纳

设计文档已新增 **8.3 树数据来源约定**：树数据使用现有 `GET /{treeEntity}`，首版全量拉取不分页、按 `treeSortField` 排序、前端平铺转树，与“不新增树专用接口”一致。

---

### 5. 序列化与空值的约定 — 可选补充（3.1）

**现状**：7.1 已约定 `mode = masterDetailTree` 时 `masterDetailTree` 必填并在校验阶段报错，等价于“树表模式下不可为空”。

**可选**：若希望前端实现更稳妥，可在文档（例如 8.2 或 十一）中加一句：前端以 `mode === 'masterDetailTree'` 判断布局后，再校验 `masterDetailTree` 非空再读取其字段，避免极端情况下空引用。属实现层防御性约定，非必须。

---

## 四、可选增强（不影响首版）

- **13.2 主从扩展**：将 `masterDetailTree` 抽象为 `masterDetail` 并在其下用 `type: 'tree'` 挂树专属字段，文档已预留，首版可保持当前结构，后续再演进。
- **树懒加载 / 节点搜索**：文档已在 13.4 说明为后续能力，首版“前端全量树 + 前端计算子孙”合理。
- **数据权限**：14.4 已说明左树与右表需同一权限边界，建议在权限设计时把“树表布局下树与表的数据源”纳入同一权限模型，不在本设计里展开实现细节。

---

## 五、与现有代码的对接点（简要核对）

| 项目 | 说明 |
|------|------|
| `DataforgeEntity.java` | 需新增 `EntityUiLayout uiLayout() default ...`，与文档一致。 |
| `EntityMeta.java` | 需新增 `EntityUiLayoutMeta uiLayout`（或等价 DTO），与文档一致。 |
| `EntityMeta` 已有 `treeNameField` 等 | 仅用于“本实体是树”的场景，与 `uiLayout.masterDetailTree` 的树字段语义不同，建议在文档/注释中区分。 |
| `MetaScanner` | 从 `@DataforgeEntity.uiLayout()` 及 `@MasterDetailTree` 填充 `EntityMeta.uiLayout`，并建议在此阶段做 3.2 的校验。 |
| User / Department 示例 | User 已有 `departmentId`，Department 已有 `parentId`、`name`、`sortOrder`，与 10.1、10.2 配置示例匹配。 |

---

## 六、结论与建议

- **结论**：设计可采纳，能支撑“左部门树右用户表”且为后续布局与主从扩展留好入口。设计文档刷新后，评审中提出的“树形实体区分、元数据校验规则、树数据来源、rootSelectionMode 行为”等均已补充，**可直接按“十五、实施建议”进入开发**。
- **可选**：若希望前端更稳妥，可补充一句“以 mode 判断布局后再校验 masterDetailTree 非空再读字段”（见 3.1）。其余按设计文档落地即可。
