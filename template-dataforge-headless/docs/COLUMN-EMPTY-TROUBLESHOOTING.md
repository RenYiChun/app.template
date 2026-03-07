# 列表列为空时的排查链（无 fallback）

约定：表格列**只**来自后端 OpenAPI 的 DTO schema，不做按实体字段的 fallback。列为空时按下面链路自下往上查。

## 1. 前端：列从哪里来

- **`resolveColumns(pathSegment, meta)`**（`@lrenyi/dataforge-headless/vue`）
    - `props = meta?.properties ?? meta?.schemas?.pageResponse`
    - `Object.keys(props)` 为空 → 列配置为 `[]` → 表格无数据列

- **meta 来源**
    - `useEntityCrud` 里：`crudState.meta ?? crudState.entityMeta`
    - `meta`：`EntityCrudManager.init()` → `MetaService.getEntity(entityName)` 写入
    - `entityMeta`：`refreshMeta()` → 同样 `meta.getEntity(entityName)`

- **结论**：列为空 ⇒ 当前实体的 `meta.properties` 和 `meta.schemas.pageResponse` 都为空。

## 2. 前端：meta 如何被填

- **MetaService.getEntity()** → `getEntities()` → **parseEntities(doc)**（`core/meta.ts`）
    - 请求 **GET /api/docs** 拿到 OpenAPI 的 `paths`、`components.schemas`。
    - 对每个 path 的 operation：
        - 若是 **search** 且 200 响应为 PagedResult（`content.items.$ref`），则用 **item 的 schema** 填
          `meta.schemas.pageResponse`。
        - item 即列表行类型，例如 `UserPageResponseDTO`。
        - 若是 **get**（GET by id），则用 200 响应的 schema 填 `meta.schemas.detail`（单条详情/表单回显）。
    - 若某实体 `meta.properties` 仍为空，会用 `meta.schemas.pageResponse` 拷到 `meta.properties`。

- **结论**：列为空 ⇒ 要么 **/api/docs** 里该实体的 **list item schema**（如 `UserPageResponseDTO`）的 **properties 为空**
  ，要么前端没正确解析到该 schema（例如 path/operation 不匹配）。

## 3. 后端：list item schema 从哪里来

- **OpenApiController.buildSchemas()**
    - 对每个实体：`pageResponseDto = EntityDtoResolver.resolvePageResponseDto(entity)`
    - 若 **不为 null**：`buildDtoSchema(pageResponseDto)` → 用 DTO 类的字段生成 schema，有 properties。
    - 若 **为 null**：用 **emptySchema()**（`properties: {}`）注册 `{Entity}PageResponseDTO`。

- **结论**：列为空 ⇒ 后端 **resolvePageResponseDto** 对该实体返回了 **null**，于是用了 emptySchema，前端拿到的 list item 的
  properties 为空。

## 4. 后端：为什么 resolvePageResponseDto 为 null

- **EntityDtoResolver.resolve(meta, "PageResponseDTO")**
    - `entityClass = meta.getEntityClass()` 为 null ⇒ 返回 null（并打 debug 日志）。
    - 否则：`className = entityClass.getPackageName() + ".dto." + entityClass.getSimpleName() + "PageResponseDTO"`  
      例如：`com.lrenyi.template.dataforge.backend.domain.dto.UserPageResponseDTO`
    - **Class.forName(className, true, entityClass.getClassLoader())** 抛 **ClassNotFoundException** ⇒ 返回 null（并打
      warn 日志）。

- **结论**：列为空 ⇒ 要么 **entityClass 为 null**（实体未正确注册），要么 **运行时找不到 *PageResponseDTO 类**（未编译或不在同一
  ClassLoader）。

## 5. 根因与修复

| 现象                                                                  | 可能原因                                             | 修复                                                                                                                                                   |
|---------------------------------------------------------------------|--------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| 后端日志：`entityClass is null`                                          | 该实体未注册或未设置 entityClass                           | 检查 EntityRegistry、MetaScanner，确保实体扫描并设置了 `meta.setEntityClass(...)`                                                                                  |
| 后端日志：`DTO class not found: ...UserPageResponseDTO`                  | 编译期未生成 DTO 或运行时 ClassLoader 看不到                  | 1）在 **含该实体的模块** 执行 **mvn clean compile**，确保注解处理器运行并生成 `*PageResponseDTO`<br>2）已改为用 **entityClass.getClassLoader()** 加载 DTO，与实体同模块、同 classpath 时一般可找到 |
| 后端日志：`[OpenApi] PageResponseDTO class not found for entity User`    | 同上，buildSchemas 时发现 DTO 为 null 用了 emptySchema    | 同上，保证该模块 clean compile 且生成的 DTO 在运行 classpath 中                                                                                                      |
| 前端控制台：`[MetaService] entity pathSegment=users has empty properties` | 前端拿到的 OpenAPI 里 list item schema 的 properties 为空 | 说明后端已用 emptySchema；按上面两条修后端并重启，再清缓存刷新 /api/docs                                                                                                      |

## 6. 诊断日志位置（便于往上层层排查）

- **后端**
    - `EntityDtoResolver`：entityClass 为 null 时 debug；`ClassNotFoundException` 时 warn，带完整
      className、entity、pathSegment。
    - `OpenApiController.buildSchemas()`：对某实体使用 emptySchema 注册 PageResponseDTO 时 warn，带 entity
      名、pathSegment、修复提示。
- **前端**
    - `meta.ts` parseEntities：某实体最终 `meta.properties` 仍为空时 console.warn，带 pathSegment、原因与修复提示。

按上述顺序从「列空 → meta 空 → OpenAPI schema 空 → resolve 为 null → entityClass/ClassNotFoundException」往上查即可定位根因，且无需再加
fallback。
