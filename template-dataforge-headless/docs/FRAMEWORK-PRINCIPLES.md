# Dataforge 框架原理与正确用法

## 一、整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│  应用层 (sample-frontend)                                         │
│  - createDataforge() 初始化                                       │
│  - 可选：registerEntityConfig() 做显示名/列标签等覆盖               │
└───────────────────────────────┬─────────────────────────────────┘
                                │
┌───────────────────────────────▼─────────────────────────────────┐
│  template-dataforge-ui (Element Plus 实现)                        │
│  - EntityCrudPage / EntityTable / EntitySearchBar / EntityToolbar │
│  - useEntityCrud(entity) 封装 CRUD 状态 + 列解析                   │
└───────────────────────────────┬─────────────────────────────────┘
                                │
┌───────────────────────────────▼─────────────────────────────────┐
│  template-dataforge-headless (框架无关 + Vue 适配)                 │
│  - Core: EntityClient, MetaService, EntityCrudManager             │
│  - Vue: useEntityCrud(client, entity), useEntityMeta,            │
│         resolveColumns, registerEntityConfig                      │
└───────────────────────────────┬─────────────────────────────────┘
                                │
┌───────────────────────────────▼─────────────────────────────────┐
│  后端 (GET /api/docs OpenAPI + REST CRUD)                         │
└─────────────────────────────────────────────────────────────────┘
```

- **Headless**：不包含任何 UI 组件，只提供 Core（Client、Meta、EntityCrudManager）和 Vue 层的 composables、config。
- **UI 包**：基于 Headless 的 composables / Manager，用 Element Plus 实现具体组件；列配置通过 headless 的 `resolveColumns` 取得，不在 UI 里写死。

## 二、元数据来源（列配置的真相）

- **主数据源是后端**：`MetaService` 请求 **GET /api/docs** 拿到 OpenAPI 文档，解析出各实体的 **EntityMeta**。
- **EntityMeta** 里与列相关：
  - **schemas.response**：从 OpenAPI 里该实体「查询/列表」接口的 200 响应 schema 解析得到（`meta.schemas.response` = schema.properties）。
  - **properties**：从 `components.schemas` 里与该实体同名的 schema 解析得到（如 User、users 等）。
- **resolveColumns(pathSegment, meta)** 的约定：
  - **列从 meta 来**：列集合与默认展示来自 `meta.schemas.response ?? meta.properties`（后端提供的结构）。
  - **registerEntityConfig 只做覆盖**：注册表里的 `config.columns` 只用于按 `prop` 覆盖某几列的 label、width、formatter 等，**不**用 config 决定「有哪些列」；列有哪些、顺序如何，以 meta 为准。

因此：**列数据是由后端接口提供的元数据解析出来的**；前端只在需要时用 `registerEntityConfig` 做展示层面的覆盖（如中文标签、formatter）。

## 三、正确用法

### 1. 应用入口

```ts
import { createDataforge } from '@lrenyi/dataforge-headless/vue';

createDataforge({
  client: { baseURL: '', apiPrefix: '/api' },
  auth: { onUnauthorized: () => router.push('/login') },
});
app.use(dataforge);
```

### 2. 零配置列表页（用 UI 包）

```vue
<EntityCrudPage entity="users" @create="..." @edit="..." @delete="..." />
```

- 列由 **meta（后端 OpenAPI）** 解析，UI 包的 `useEntityCrud` 内部会：拉 meta → `resolveColumns(entity, meta)` → 得到 `displayColumns` 传给表格。
- 不需要在页面或某个「实体配置文件」里写死 columns 数组。

### 3. 组合式用法（仅用 headless 时）

```ts
const { client, meta } = getDataforge();
const { meta: entityMetaRef } = useEntityMeta(meta, entity);
const { items, total, loading, filters, page, size, search } = useEntityCrud(client, entity);

const columns = computed(() => resolveColumns(entity, entityMetaRef.value) || []);
```

- 列仍然来自 **resolveColumns(entity, entityMeta)**，entityMeta 来自 **useEntityMeta(meta, entity)**，即后端元数据。
- 表格用 `columns` 渲染即可。

### 4. 可选：实体级覆盖（显示名、列标签、formatter）

当后端 schema 没有友好 label，或需要对某几列做格式化时，再用注册表**覆盖**，而不是重新定义整张表：

```ts
import { registerEntityConfig } from '@lrenyi/dataforge-headless/vue';

registerEntityConfig('users', {
  displayName: '用户',
  columns: [
    { prop: 'status', label: '状态', formatter: (v) => (v === 1 ? '启用' : '禁用') },
  ],
  searchFields: ['username', 'status'],
});
```

- **columns**：只写要覆盖的列（如 `status`），未写到的列仍由 meta 提供，resolveColumns 会合并。
- 列集合、顺序以 **meta** 为准；config 只提供「某几列」的 label/width/formatter 覆盖。

## 四、常见错误（避免）

1. **在前端用一整个「实体配置」文件为每个实体写死完整 columns**  
   → 列应由后端元数据解析；前端只做按 prop 的覆盖。

2. **在 EntityCrudPage 或 useEntityCrud 里用 props 传入完整 columns 并优先于 meta**  
   → 会破坏「元数据驱动」；列应以 meta + resolveColumns 为主，props 不应替代 meta。

3. **认为 registerEntityConfig 的 columns 是「完整列定义」**  
   → 它是「覆盖/补充」；列有哪些、顺序如何，由 meta（后端）决定。

4. **表格组件用错 prop 名**  
   → EntityTable 的列 prop 是 **columns**，应传 `:columns="displayColumns"`，不要传成 `:display-columns`。

## 五、与 UI 包的衔接

- UI 包的 **useEntityCrud(entity)** 内部：创建 EntityCrudManager、订阅 state、在 onMounted 时调用 **refreshMeta()** 和 **init()** 拉取 meta，再用 **resolveColumns(entityName, crudState.entityMeta)** 得到 allColumns/displayColumns。
- 只要后端 **GET /api/docs** 返回的 OpenAPI 里，该实体的 search/get 等接口有正确的 200 response schema（或 components.schemas 中有对应 schema），meta 就会带上 **schemas.response** 或 **properties**，resolveColumns 就能解析出列；表格收到 `:columns="displayColumns"` 即可正常展示。

---

总结：**列数据由后端接口提供的元数据（OpenAPI 解析出的 EntityMeta）解析；resolveColumns 以 meta 为主、registerEntityConfig 仅做按列覆盖；不在前端写死完整列配置。**
