# 默认展示根部门 — 需求与实现 Review

## 需求确认

- **原设计**：`rootSelectionMode = all` 时“右表直接展示全量数据”，不强制选中树节点。
- **现需求**：默认展示根部门；父部门展示子部门的用户（即选中根节点 + `includeDescendants = true`）。
- **结论**：已按“默认选中第一个根节点”实现，设计文档 8.4 / 8.5 已同步为“默认展示根部门、父部门展示子部门用户”的语义。

---

## 实现 Review（useEntityCrud.ts）

### 1. 抽离 `applyTreeSelection(node, triggerSearch)` — 通过

- 将“设选中节点、算 treeFilter、合并 filters、可选触发 search”收口到一处，`onTreeSelect` 与自动选根共用一个逻辑，避免重复。
- `triggerSearch` 区分“用户点击”（需要刷新列表）和“仅恢复选中状态”（可不打请求），语义清晰。

### 2. `loadTreeData(autoSelectRoot, triggerSearchOnAutoSelect)` — 通过

- **签名**：`autoSelectRoot = false`、`triggerSearchOnAutoSelect = false` 的默认值合理；none 模式传 `(false, false)`，all 模式传 `(true, true)`。
- **选根逻辑**：`treeData.value.length > 0` 时取 `treeData.value[0]` 作为“第一个根节点”。`buildTree` 的 roots 顺序与平铺列表顺序一致，平铺列表已按 `treeSortField` 排序，故第一个根即“按 treeSortField 排序后的首个根”，与设计一致。
- **多根情况**：若存在多个根（如多棵独立树），当前会选中第一个根；若业务上“根部门”唯一，无影响；若未来需支持“指定根”，可再扩展为按 id/编码匹配或配置。
- **返回值**：返回 `boolean` 表示是否执行了自动选根，便于 onMounted 决定是否再调 `entityCrudManager.search()`，避免重复请求，逻辑正确。

### 3. onMounted 流程 — 通过

- `rootSelectionMode === 'none'`：`loadTreeData(false, false)`，不选根、不请求右表，符合设计。
- 非 none：`loadTreeData(true, true)`；若 `autoSelected === true` 则 return，不再执行后面的 `entityCrudManager.search()`，因为 `applyTreeSelection(..., true)` 已触发 search，无重复请求。
- 若树无根（`treeData.value.length === 0`）或未走自动选根，则 fallback 到 `entityCrudManager.search()`，兼容无树或异常数据。

### 4. 与 listBlockedByTreeSelection 的配合 — 通过

- none 模式下未选节点时，`listBlockedByTreeSelection` 为 true，search/export 已被拦截；选根仅在 all（或未配置）时执行，不会与 none 语义冲突。

---

## 建议与可选优化

1. **watch entityMeta 与树加载**  
   当前仅在 onMounted 中根据 `isMasterDetailTree` 调用 `loadTreeData`，原先“entityMeta 变化时 loadTreeData()”的 watch 已移除。若后续存在“meta 延迟下发或切换 entity 再切回”的场景，可考虑在 watch entityMeta 中当 `isMasterDetailTree` 且树数据为空时再调一次 `loadTreeData(false, false)`，仅补拉树、不自动选根，避免依赖唯一入口。当前仅 onMounted 单路径加载可接受。

2. **“第一个根”的稳定性**  
   若后端根节点列表顺序或 `treeSortField` 不稳定，第一个根可能变化。若业务要求“始终选名称=根部门的那条”，可后续在配置中增加 `defaultRootName` 或 `defaultRootId`，在 roots 中查找后选中；首版按“第一个根”实现即可。

3. **设计文档**  
   已在 8.4、8.5 中写明：默认选中第一个根节点、父部门展示子部门用户（含 `includeDescendants`），与当前实现一致。

---

## 小结

- 需求“默认展示根部门、父部门展示子部门用户”已实现：all 模式下自动选中第一个根并触发一次 search，结合现有 `includeDescendants = true` 即展示根及其子孙下的用户。
- 抽离 `applyTreeSelection`、`loadTreeData` 双参数与返回值、onMounted 分支与防重复请求均合理；设计文档已同步更新。
