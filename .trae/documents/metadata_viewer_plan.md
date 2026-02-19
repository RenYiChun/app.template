# 计划：添加前端元数据查看组件到 UI 库

本计划旨在开发一个可复用的“元数据查看”页面组件，将其放入 `template-platform-ui` 组件库中，并在示例应用中引用。

## 1. 开发 EntityMetadataPage 组件 (UI 库)

在 `template-platform-ui` 项目中创建新的 Vue 组件。

* **文件路径**: `d:\github.com\app.template\template-platform-ui\src\components\EntityMetadataPage.vue`

* **功能**:

  * **数据获取**: 使用 `usePlatform().meta.getEntities()` 获取所有实体元数据。

  * **布局**:

    * 使用 `el-container` 实现左右分栏。

    * **左侧 (Aside)**: 实体列表，包含搜索框（过滤 `displayName` 或 `pathSegment`）。

    * **右侧 (Main)**: 实体详情展示。

      * **Header**: 实体名称与路径。

      * **Tabs**:

        * **操作 (Operations)**: 表格展示 (Method, Path, Summary, Permissions)。

        * **字段 (Fields)**: 表格展示可查询字段 (Name, Type, Operators)。

        * **结构 (Schemas)**: 子 Tabs 展示 Create/Update/Response 结构定义。

* **Props**:

  * `locale`: 用于传入国际化文本对象（类似于 `EntityCrudPage` 的设计）。

## 2. 导出组件 (UI 库)

在 `template-platform-ui` 的入口文件中导出新组件。

* **文件路径**: `d:\github.com\app.template\template-platform-ui\src\index.ts`

* **操作**: 添加 `export { default as EntityMetadataPage } from './components/EntityMetadataPage.vue';`

## 3. 集成到示例应用 (Sample Frontend)

在 `template-platform-sample-frontend` 中使用该组件。

### 3.1 创建路由页面

虽然可以直接引用，但为了传递 locale 等配置，建议创建一个简单的包装页面。

* **文件路径**: `d:\github.com\app.template\template-platform-sample-frontend\src\views\system\MetadataList.vue`

* **内容**:

  ```vue
  <template>
    <EntityMetadataPage :locale="platformUiLocale" />
  </template>
  <script setup>
  import { EntityMetadataPage } from '@lrenyi/platform-ui';
  import { usePlatformUiLocale } from '@/i18n';
  const platformUiLocale = usePlatformUiLocale();
  </script>
  ```

### 3.2 更新路由配置

* **文件路径**: `d:\github.com\app.template\template-platform-sample-frontend\src\router\index.ts`

* **操作**: 添加路由 `/system/metadata`，指向 `MetadataList.vue`。

### 3.3 更新导航菜单

* **文件路径**: `d:\github.com\app.template\template-platform-sample-frontend\src\views\HomeView.vue`

* **操作**: 在“系统管理”下添加“元数据查看”菜单项。

### 3.4 更新国际化

* **文件路径**: `d:\github.com\app.template\template-platform-sample-frontend\src\locales/zh-CN.ts` (及 en.ts)

* **操作**: 添加 `menu.metadata` 及 `platformUi.metadata` 相关的翻译文本，确保传递给组件的 `locale` 对象包含所需文本。

## 4. 验证

* 确认组件库构建正常（如果是开发模式，Vite Alias 会处理源码引用）。

* 确认示例应用能正确显示新页面。

* 验证功能：列表加载、搜索、详情展示。

