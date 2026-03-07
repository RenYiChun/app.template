# 修复 DTO 生成器与元数据展示不一致的计划

## 问题分析

用户反馈 `/system/metadata` 页面展示的 DTO 字段比实际生成的 DTO 对象字段少。经过代码分析，发现原因如下：

1.  **前端表现 (MetadataDetail.vue)**：
    - 前端逻辑严格遵循 `@DataforgeDto` 注解的定义。
    - 当字段标记为 `updateOnly = true` 时，前端会将其识别为**仅包含在 Update DTO 中**。
    - 因此，在 Response DTO 的展示中，前端会正确地**隐藏**该字段。

2.  **后端生成逻辑 (DtoGeneratorProcessor.java)**：
    - 注解处理器在解析 `@DataforgeDto` 时，**遗漏**了对 `updateOnly` 和 `queryOnly` 属性的处理。
    - 目前代码仅处理了 `readOnly`, `writeOnly` 和 `createOnly`。
    - 结果：标记为 `updateOnly = true` 的字段，在生成器眼中没有被设置为 "仅包含在 Update"，因此被当作普通字段处理（即默认包含在 Response DTO 中）。

**结论**：
元数据页面的展示是符合注解预期的（更准确），而实际生成的 DTO 代码存在 Bug，导致包含了一些本该排除的字段。这造成了“页面上看到的字段比实际对象少”的现象。

## 实施步骤

### 1. 修复 `DtoGeneratorProcessor.java`

修改 `getDtoTypeNamesFromMirrors` 方法，补充缺失的逻辑：

- [ ] 添加对 `updateOnly` 属性的检查：如果为 `true`，将 `UPDATE` 类型加入 include 集合。
- [ ] 添加对 `queryOnly` 属性的检查：如果为 `true`，将 `QUERY` 类型加入 include 集合。
- [ ] (可选) 优化变量命名，将 `excludes` 变量名在处理 include 逻辑时改为更贴切的名字，避免混淆。

### 2. 验证修复

- [ ] 由于无法直接运行注解处理器测试，我们将通过代码审查确保逻辑与 `createOnly` 的处理方式一致。
- [ ] 确保 `DtoType` 常量的引用正确。

## 预期结果

修复后，重新编译项目，生成的 DTO (如 ResponseDTO) 将不再包含标记为 `updateOnly` 的字段。此时实际 DTO 的字段将减少，与元数据页面的展示保持一致。
