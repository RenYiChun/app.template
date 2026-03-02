# 修复 DTO 字段显示不一致及 PAGE_RESPONSE 逻辑统一计划

问题分析发现，导致前端 `/system/metadata` 页面 DTO 字段显示不全（仅显示 ID）的根本原因是后端 `FieldMeta` 类中 `dtoIncludeTypes` 字段被标记了 `@JsonIgnore`，导致前端无法获取字段的 DTO 配置信息。

同时，为了彻底解决逻辑不一致问题，我们将统一 `PAGE_RESPONSE` 的处理策略，使其与其他 DTO 类型一样遵循“无配置则默认包含，有配置则按配置”的规则。

## 涉及文件与修改

### 1. 后端元数据模型
- **文件**: `template-dataforge/src/main/java/com/lrenyi/template/dataforge/meta/FieldMeta.java`
- **修改**: 移除 `dtoIncludeTypes` 字段上的 `@com.fasterxml.jackson.annotation.JsonIgnore` 注解，确保该字段能序列化返回给前端。

### 2. 注解处理器 (DTO 生成)
- **文件**: `template-dataforge-processor/src/main/java/com/lrenyi/template/dataforge/processor/DtoGeneratorProcessor.java`
- **修改**: 修改 `generateDtos` 方法中 `pageResponseFields` 的过滤逻辑。不再使用硬编码的 `contains("PAGE_RESPONSE")`，而是调用 `shouldInclude(f, "PAGE_RESPONSE")`，从而复用通用的“默认包含”逻辑。

### 3. 前端展示逻辑
- **文件**: `template-dataforge-sample-frontend/src/views/system/MetadataDetail.vue`
- **修改**: 在 `getDtoFields` 方法中，移除对 `PAGE_RESPONSE` 的特殊严格判断分支，使其完全复用后续的通用逻辑（即 ID 处理 + include 检查）。

## 预期效果
1.  前端能正确接收 `dtoIncludeTypes` 数据，从而正确渲染配置了 `include` 的字段（如 `username`）。
2.  `PAGE_RESPONSE` DTO 将遵循通用规则：如果没有配置 `@DataforgeDto`，字段将默认包含在分页响应中（与详情响应一致）。如果需要排除，需显式配置 `include` 且不包含 `PAGE_RESPONSE`。
3.  前后端逻辑完全统一，消除“代码里有但页面上没有”或“页面上有但代码里没有”的歧义。
