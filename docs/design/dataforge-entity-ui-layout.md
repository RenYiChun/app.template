# Dataforge 实体 UI 布局元数据设计

> 目标：为存在关联实体的列表页提供统一的元数据配置，控制前端使用普通表格布局，还是使用“左树右表”的主从布局。首个落地场景为“部门-用户”。

## 一、背景

当前 Dataforge 已具备以下能力：

- 后端通过 `@DataforgeEntity` 扫描生成 `EntityMeta`
- 前端通用页基于 `EntityMeta` 渲染搜索、工具栏、表格和分页
- 用户与部门等实体之间已经可以通过字段建立关联

以示例工程为例：

- [User.java](/D:/github.com/app.template/template-dataforge-sample-backend/src/main/java/com/lrenyi/template/dataforge/backend/domain/User.java) 中存在 `departmentId`
- [Department.java](/D:/github.com/app.template/template-dataforge-sample-backend/src/main/java/com/lrenyi/template/dataforge/backend/domain/Department.java) 中存在 `parentId`

这说明“部门树筛选用户列表”已经具备业务建模基础，但当前框架还缺少一层统一的 UI 布局元数据，导致前端只能固定按表格方式展示。

## 二、设计目标

- 为实体列表页增加布局元数据，而不是为某个具体页面写死逻辑
- 支持两种基础布局：
  - 普通表格布局
  - 左树右表布局
- 配置应挂在“主列表实体”上，例如用户页的布局配置属于 `users`
- 前端通用页根据元数据自动切换布局，不要求业务页面重复实现
- 为未来扩展更多布局模式预留空间，不把设计收敛成布尔值

## 三、非目标

- 本阶段不引入完整低代码页面编排系统
- 本阶段不处理跨实体联动表单编排
- 本阶段不支持多个左侧树或多级主从面板
- 本阶段不改造所有查询协议，仅补充必要的树筛选能力

## 四、核心设计原则

### 4.1 配置挂在主实体上

页面主体是谁，布局配置就归谁。例如“用户管理页”以用户表为主体，因此布局配置应放在 `users` 的 `EntityMeta` 中，而不是 `departments` 中。

原因：

- 前端进入用户页面时只需要读取一份用户元数据即可完成渲染决策
- 部门树只是用户页的筛选视图，不是页面主体
- 可以避免前端在多个实体元数据之间做额外推断

### 4.2 使用结构化配置，不使用单一布尔值

不建议只加一个类似 `treeLayoutEnabled` 的字段，因为它只能表达“是否启用树表”，无法描述：

- 左侧树取哪个实体
- 树的父子字段和显示字段
- 右侧列表通过哪个字段关联
- 是否包含子节点数据
- 是否隐藏重复搜索项

因此应使用结构化的 `uiLayout` 配置。

### 4.3 默认值必须兼容现有行为

当实体未配置 `uiLayout` 时，前端默认使用普通表格布局，确保历史实体和历史页面无需改动。

## 五、方案总览

### 5.1 布局模式

建议引入统一布局模式枚举：

- `table`：普通表格布局
- `masterDetailTree`：左树右表布局

未来可扩展：

- `tabs`
- `treeOnly`
- `cardGrid`
- `splitPane`
- `masterDetailTabs`

### 5.2 元数据结构

建议在 `EntityMeta` 中新增一个 `uiLayout` 字段：

```java
public class EntityUiLayoutMeta {
    private String mode; // "table" | "masterDetailTree"
    private MasterDetailTreeMeta masterDetailTree;
}

public class MasterDetailTreeMeta {
    private String treeEntity;
    private String treeEntityLabel;
    private String treeIdField = "id";
    private String treeParentField = "parentId";
    private String treeLabelField = "name";
    private String treeSortField = "sortOrder";
    private String relationField;
    private String rootSelectionMode = "all";
    private boolean includeDescendants = false;
    private boolean hideTableSearchRelationField = true;
}
```

说明：

- `mode`：当前实体页采用的布局模式
- `treeEntity`：左侧树使用的实体 `pathSegment`，例如 `departments`
- `treeIdField`：树实体的主键字段名，默认 `id`
- `treeParentField`：树形父字段，默认 `parentId`
- `treeLabelField`：树节点名称字段，默认 `name`
- `treeSortField`：树节点排序字段，默认 `sortOrder`
- `relationField`：右侧主表中用于关联左树的字段，例如 `departmentId`
- `rootSelectionMode`：页面初始化时根节点如何处理，建议首版支持 `all` 和 `none`
- `includeDescendants`：点击部门时是否同时展示其子孙部门下的用户
- `hideTableSearchRelationField`：树表模式下，是否从右侧搜索栏隐藏同名关联字段

### 5.3 与现有“树形实体”元数据的关系

当前 `EntityMeta` 已经存在一组“实体自身就是树”的配置，例如：

- `treeEntity`
- `treeParentField`
- `treeChildrenField`
- `treeNameField`
- `treeCodeField`

这些字段描述的是“当前实体本身如何作为树来管理”，例如部门管理页、字典树管理页。

本设计新增的 `uiLayout.masterDetailTree.*` 描述的是“某个主列表页左侧要挂一棵什么树”，例如用户页左侧挂部门树。

两者必须严格区分：

- **实体自身是树**：使用 `EntityMeta` 现有树字段
- **实体列表页左侧挂树**：使用 `uiLayout.masterDetailTree` 字段

为避免实现层误用，建议采用以下约定：

- 文档中保留 `treeLabelField` 命名，表示左侧树节点展示字段
- 若实现时希望与现有 `treeNameField` 命名统一，可在代码注释中明确 `treeLabelField` 与 `treeNameField` 语义等价，仅使用场景不同
- 前端不要把“实体自身的树字段”和“列表页左树字段”混为一套配置读取

## 六、注解层设计

建议在 `@DataforgeEntity` 上增加 `uiLayout` 配置入口，而不是单独走全局配置文件。

示意：

```java
public @interface DataforgeEntity {
    // 现有字段省略
    EntityUiLayout uiLayout() default @EntityUiLayout(mode = UiLayoutMode.TABLE);
}
```

配套新增注解：

```java
public @interface EntityUiLayout {
    UiLayoutMode mode() default UiLayoutMode.TABLE;
    MasterDetailTree masterDetailTree() default @MasterDetailTree;
}

public @interface MasterDetailTree {
    String treeEntity() default "";
    String treeEntityLabel() default "";
    String treeIdField() default "id";
    String treeParentField() default "parentId";
    String treeLabelField() default "name";
    String treeSortField() default "sortOrder";
    String relationField() default "";
    RootSelectionMode rootSelectionMode() default RootSelectionMode.ALL;
    boolean includeDescendants() default false;
    boolean hideTableSearchRelationField() default true;
}
```

枚举建议：

```java
public enum UiLayoutMode {
    TABLE,
    MASTER_DETAIL_TREE
}

public enum RootSelectionMode {
    ALL,
    NONE
}
```

## 七、运行时元数据设计

后端启动时由 `MetaScanner` 将注解配置转换为 `EntityMeta.uiLayout`，并通过元数据接口输出。

涉及模块：

- `template-dataforge`
- `template-dataforge-ui`

建议修改点：

- [DataforgeEntity.java](/D:/github.com/app.template/template-dataforge/src/main/java/com/lrenyi/template/dataforge/annotation/DataforgeEntity.java)
  增加 `uiLayout`
- [EntityMeta.java](/D:/github.com/app.template/template-dataforge/src/main/java/com/lrenyi/template/dataforge/meta/EntityMeta.java)
  增加 `uiLayout`
- [MetaScanner.java](/D:/github.com/app.template/template-dataforge/src/main/java/com/lrenyi/template/dataforge/support/MetaScanner.java)
  负责注解到元数据的映射
- [MetadataController.java](/D:/github.com/app.template/template-dataforge/src/main/java/com/lrenyi/template/dataforge/controller/MetadataController.java)
  无需改协议风格，只需自然输出新增字段

### 7.1 元数据校验规则

当 `uiLayout.mode = masterDetailTree` 时，建议在元数据构建阶段执行校验，而不是等前端运行时报错。

建议校验项如下：

- `masterDetailTree` 必填
- `treeEntity` 必填，且必须能按 `pathSegment` 解析到对应的树实体 `EntityMeta`
- `relationField` 必填，且必须存在于当前主列表实体字段中
- 树实体中必须存在 `treeIdField`
- 树实体中必须存在 `treeParentField`
- 树实体中必须存在 `treeLabelField`
- 若配置了 `treeSortField`，则树实体中应存在该字段

建议校验时机：

- 在 `MetaScanner` 完成全部实体注册后进行一次二次校验
- 或在元数据发布前由专门的元数据校验器处理

建议失败策略：

- 直接抛出明确异常，阻止应用启动或阻止元数据发布
- 不建议静默降级为普通表格布局

建议异常信息包含：

- 当前主实体 `pathSegment`
- 配置的 `treeEntity`
- 缺失或非法的字段名

## 八、前端渲染设计

### 8.1 入口位置

前端布局切换应发生在通用 CRUD 页层面，而不是业务页面层面。

建议改造入口：

- [EntityCrudPage.vue](/D:/github.com/app.template/template-dataforge-ui/src/components/EntityCrudPage.vue)
- [useEntityCrud.ts](/D:/github.com/app.template/template-dataforge-ui/src/composables/useEntityCrud.ts)

### 8.2 渲染策略

当 `entityMeta.uiLayout.mode = table` 时：

- 维持现有搜索区 + 工具栏 + 表格 + 分页布局

当 `entityMeta.uiLayout.mode = masterDetailTree` 时：

- 页面左右分栏
- 左侧渲染树实体数据
- 右侧保持原有搜索区、工具栏、表格和分页
- 右侧查询自动叠加树选择条件

### 8.3 树数据来源约定

首版建议直接复用现有通用 CRUD 列表接口，不新增专用树接口。

约定如下：

- 左侧树数据使用现有 `GET /{treeEntity}` 或等价列表查询接口获取
- 首版默认一次性拉取全量树数据，不分页
- 若树实体存在排序字段，优先按 `treeSortField` 排序
- 前端拿到平铺列表后，根据 `treeIdField` 和 `treeParentField` 构造成树

这样可以获得以下收益：

- 不新增后端树专用接口
- 与现有 Dataforge CRUD 机制保持一致
- 便于首版快速落地

### 8.4 左树右表的推荐交互

- 页面初始化时默认加载树和表
- 当 `rootSelectionMode = all`（或未配置为 `none`）时，**默认选中第一个根节点**，右表展示该节点及其子孙节点下的数据（依赖 `includeDescendants` 配置）；即“默认展示根部门，父部门会展示子部门的用户”
- 点击树节点时，右表自动刷新
- 右表仍保留用户名、手机号、状态等搜索条件
- 若 `hideTableSearchRelationField = true`，则右表搜索栏中隐藏 `departmentId`
- 右表分页、排序逻辑保持不变

### 8.5 `rootSelectionMode` 的行为约定

为避免前端实现歧义，建议明确：

- `all`（或未配置）
  - 页面初始化时**自动选中第一个根节点**（按 `treeSortField` 排序后的首个根）
  - 右表展示该节点对应数据；若 `includeDescendants = true`，则包含其全部子孙节点下的数据（如根部门展示全公司用户）
  - 用户点击其他树节点后，再切换为按该节点过滤
- `none`
  - 页面初始化时不自动选中任何节点
  - 右表不发起列表请求，或只显示空状态占位
  - 直到用户显式选择树节点后，右表才开始请求列表数据

若未来支持通过 URL query 或路由参数指定默认节点，则其优先级应高于 `rootSelectionMode` 默认行为。

## 九、查询与过滤设计

### 9.1 基础场景

当 `includeDescendants = false` 时，点击部门节点后，右表直接注入：

```text
departmentId = 当前部门ID
```

### 9.2 包含子孙部门场景

当 `includeDescendants = true` 时，单个等值过滤不够，需要将当前节点及其全部子孙节点 id 汇总后，生成：

```text
departmentId in [当前节点及全部子孙节点ID]
```

推荐首版实现：

- 前端加载完整部门树
- 前端根据树结构计算子孙节点 id 集合
- 右表查询注入 `IN` 条件

不推荐首版实现：

- 新增后端专用查询操作符如 `TREE_DESCENDANT_OF`

原因：

- 会扩展通用过滤协议
- 改动范围更大
- 与当前 Dataforge 通用 CRUD 协议相比收益不成比例

## 十、用户与部门场景的推荐配置

### 10.1 用户实体配置示意

```java
@DataforgeEntity(
    pathSegment = "users",
    displayName = "用户",
    uiLayout = @EntityUiLayout(
        mode = UiLayoutMode.MASTER_DETAIL_TREE,
        masterDetailTree = @MasterDetailTree(
            treeEntity = "departments",
            treeLabelField = "name",
            treeParentField = "parentId",
            treeSortField = "sortOrder",
            relationField = "departmentId",
            includeDescendants = true,
            hideTableSearchRelationField = true
        )
    )
)
public class User extends BaseEntity<Long> {
}
```

### 10.2 部门实体建议

部门实体本身不需要声明用户页的布局配置，但建议保留或补充这些字段语义：

- `id`
- `parentId`
- `name`
- `sortOrder`

若未来要增强通用树渲染，也可以复用实体已有树信息：

- `treeEntity`
- `treeParentField`
- `treeNameField`

但“用户页是否使用部门树”仍应配置在用户实体上。

## 十一、使用方法

### 11.1 后端使用步骤

1. 在 `template-dataforge` 中新增 UI 布局相关注解、枚举和元数据类
2. 在 `@DataforgeEntity` 上暴露 `uiLayout` 配置
3. 在 `MetaScanner` 中将注解转换为 `EntityMeta.uiLayout`
4. 在业务实体上声明布局模式和树关联关系
5. 对 `uiLayout.masterDetailTree` 执行元数据校验

### 11.2 前端使用步骤

1. 在元数据加载完成后读取 `entityMeta.uiLayout`
2. 根据 `mode` 决定使用普通表格还是树表布局
3. 若为树表模式，加载 `treeEntity` 对应实体数据
4. 用户选择树节点后，将 `relationField` 对应的过滤条件注入右侧列表查询
5. 若 `rootSelectionMode = none`，则首屏不自动请求右表数据

### 11.3 业务方最小配置

若业务方只需要“左部门树右用户表”，最小配置只需要：

- `mode`
- `treeEntity`
- `treeParentField`
- `treeLabelField`
- `relationField`
- `includeDescendants`

其余字段可使用默认值。

## 十二、兼容性策略

- 未配置 `uiLayout` 的实体保持当前普通表格行为
- 已有 `EntityCrudPage` 插槽能力不应被破坏
- 右侧表格、分页、批量操作、导出逻辑尽量复用现有实现
- 树表模式是增量能力，不应要求业务页面重写全部组件

## 十三、扩展设计

本设计需要考虑后续能力扩展，因此结构上要避免收缩到某个特定场景。

### 13.1 可扩展布局模式

未来可以扩展的模式包括：

- `tabs`
  适用于一个实体对应多个列表视图
- `cardGrid`
  适用于卡片式内容管理
- `treeOnly`
  适用于纯树管理页面
- `masterDetailTabs`
  左侧主实体，右侧多个 Tab 分视图展示
- `splitPane`
  通用双栏布局，不要求左侧一定是树

因此 `uiLayout` 应设计成统一入口，不建议未来继续向 `EntityMeta` 平铺多个布局专属字段。

### 13.2 主从关系的扩展

未来可能出现：

- 左侧不是树，而是字典分组、状态分组或组织分组
- 右侧不是表，而是卡片或嵌套明细
- 一个列表依赖多个筛选实体

因此后续可考虑将 `masterDetailTree` 抽象为更通用的 `masterDetail`，再在 `master.type = tree` 下挂树专属字段。

### 13.3 查询协议扩展

如果后续树筛选需求越来越强，可考虑扩展统一过滤操作符：

- `TREE_DESCENDANT_OF`
- `TREE_ANCESTOR_OF`
- `BELONGS_TO_NODE`

但这属于第二阶段演进，不建议在首版中同步实现。

### 13.4 前端缓存与性能

当树数据量很大时，后续可扩展：

- 树懒加载
- 树节点搜索
- 展开状态缓存
- 最近选择节点缓存
- 局部刷新

这些能力不影响当前 `uiLayout` 结构，可在前端实现层渐进增强。

## 十四、风险与边界

### 14.1 关联字段不存在

若 `relationField` 在右表实体中不存在，应在元数据解析或前端初始化阶段直接报错，而不是静默退化。

### 14.2 树实体不是树

若 `treeEntity` 对应实体缺失 `id`、`parentId`、`name` 等关键字段，前端无法正确构树。建议在后端扫描或前端运行时增加校验和告警。

更具体地说，当 `mode = masterDetailTree` 时，建议至少校验：

- 树实体存在
- `treeIdField` 存在
- `treeParentField` 存在
- `treeLabelField` 存在
- 主实体中的 `relationField` 存在

### 14.3 大树场景的性能

对于上万节点组织树，首版一次性加载全部节点可能有压力。建议先满足中小规模后台场景，超大树场景后续引入懒加载。

### 14.4 数据权限

若未来启用数据权限，左树与右表必须遵循同一权限边界，否则会出现：

- 左树能看到节点，但右表看不到对应数据
- 右表能看到数据，但左树无法定位节点

这部分属于数据权限与 UI 布局的联动问题，应在后续权限设计中一并考虑。

## 十五、实施建议

建议按以下顺序落地：

1. 定义注解、枚举和 `EntityMeta.uiLayout`
2. 改造 `MetaScanner` 输出元数据
3. 前端在 `EntityCrudPage` 中切换布局模式
4. 首先支持用户-部门场景
5. 补充一到两个回归测试或示例页面
6. 再考虑查询协议增强和树懒加载

## 十六、结论

“实体 UI 布局元数据”是比页面特例更稳妥的设计。它将布局控制提升为框架级能力，使 Dataforge 可以在不破坏现有 CRUD 模型的前提下，支持更复杂的管理台列表场景。

对当前项目而言，推荐采用以下结论：

- 配置挂在主列表实体上
- 使用结构化 `uiLayout`，不使用布尔值
- 首版支持 `table` 和 `masterDetailTree`
- 用户页通过部门树筛选用户列表
- 包含子孙节点时优先通过前端计算 `IN` 过滤实现

该设计既能直接支撑“左部门树右用户表”，也为未来扩展更多页面布局提供了稳定入口。
