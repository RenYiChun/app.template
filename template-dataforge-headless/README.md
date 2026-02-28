# @lrenyi/dataforge-headless

template-dataforge 前端配套：通用 EntityClient、元数据驱动、Vue CRUD 组件。

## 安装

```bash
npm install @lrenyi/dataforge-headless element-plus
```

## 快速开始

### 1. 初始化 Dataforge

在应用入口（如 `main.ts`）初始化：

```ts
import { createApp } from 'vue';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import App from './App.vue';
import { createDataforge } from '@lrenyi/dataforge-headless/vue';

createDataforge({
  client: {
    baseURL: import.meta.env.VITE_API_BASE_URL || '',
    apiPrefix: '/api',
  },
});

const app = createApp(App);
app.use(ElementPlus);
app.mount('#app');
```

### 2. 零配置 CRUD 页

```vue
<template>
  <EntityCrudPage
    entity="users"
    @create="router.push('/users/create')"
    @view="(row) => router.push(`/users/${row.id}`)"
    @edit="(row) => router.push(`/users/edit/${row.id}`)"
    @delete="handleDelete"
  />
</template>

<script setup lang="ts">
import { EntityCrudPage } from '@lrenyi/dataforge-headless/vue';
import { useRouter } from 'vue-router';

const router = useRouter();

const handleDelete = async (row: Record<string, unknown>) => {
  // 使用确认框后调用删除
};
</script>
```

### 3. 组合式用法（高度定制）

```vue
<template>
  <el-card>
    <EntitySearchBar
      :entity-meta="entityMetaRef"
      v-model="filters"
      @search="search"
    />
    <EntityTable
      :columns="columns"
      :items="items"
      :loading="loading"
      @view="handleView"
    >
      <template #column-status="{ row, value }">
        <el-tag :type="value === 1 ? 'success' : 'info'">
          {{ value === 1 ? '启用' : '禁用' }}
        </el-tag>
      </template>
    </EntityTable>
    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      @current-change="search"
    />
  </el-card>
</template>

<script setup lang="ts">
import {
  getDataforge,
  useEntityCrud,
  useEntityMeta,
  EntityTable,
  EntitySearchBar,
  resolveColumns,
} from '@lrenyi/dataforge-headless/vue';

const { client, meta } = getDataforge();
const entity = 'users';

const { meta: entityMetaRef } = useEntityMeta(meta, entity);

const {
  items,
  total,
  loading,
  filters,
  page,
  size,
  search,
} = useEntityCrud(client, entity);

const columns = computed(() => resolveColumns(entity, entityMetaRef.value) || []);
</script>
```

### 4. 登录（Auth）

后端需实现 auth 域：`/api/auth/0/captcha`、`/oauth2/token`（标准 OAuth2 登录）、`/api/auth/0/logout`、`/api/auth/0/me`
。EntityClient 与 AuthClient 自动携带 `credentials: 'include'` 以支持 Session Cookie 及 Bearer Token。

**初始化时配置 auth**（含 401 回调）：

```ts
import { createDataforge } from '@lrenyi/dataforge-headless/vue';
import { useRouter } from 'vue-router';

const router = useRouter();

createDataforge({
  client: { baseURL: 'http://localhost:8080' },
  auth: {
    onUnauthorized: () => router.push('/login'),
  },
});
```

**登录页**（配合 vue-router 路由守卫）：

使用 `useAuth` 组合式 API 自定义登录页。

```vue
<template>
  <el-form :model="form" @submit.prevent="handleLogin">
    <el-form-item>
      <el-input v-model="form.username" placeholder="用户名" />
    </el-form-item>
    <el-form-item>
      <el-input v-model="form.password" type="password" placeholder="密码" />
    </el-form-item>
    <el-form-item>
      <el-button type="primary" @click="handleLogin">登录</el-button>
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { useAuth } from '@lrenyi/dataforge-headless/vue';
import { useRouter } from 'vue-router';
import { reactive } from 'vue';

const { login } = useAuth();
const router = useRouter();
const form = reactive({ username: '', password: '' });

const handleLogin = async () => {
  await login(form);
  router.push('/');
};
</script>
```

**路由守卫**：在 `router.beforeEach` 中判断 `useAuth().user`，未登录且非 `/login` 时重定向到登录页。

## API 参考

### Core 层（框架无关）

- `EntityClient`：通用 API 客户端，对接 template-dataforge REST，请求自动携带 `credentials: 'include'`
- `AuthClient`：认证 API（getCaptcha、login、logout、me），支持 `onUnauthorized` 回调
- `MetaService`：解析 `/api/docs` 获取实体元数据
- 类型：`Result`、`PagedResult`、`SearchRequest`、`AuthUser`、`LoginRequest`、`CaptchaResult`

### Vue 层

- `createDataforge(options)`：初始化，支持 `client`、`meta`、`auth` 配置
- `getDataforge()`：获取 client 与 meta
- `useEntityCrud(client, entity, options)`：CRUD 逻辑
- `useEntityMeta(meta, pathSegment)`：实体元数据
- `EntityTable`：列表表格
- `EntitySearchBar`：搜索栏
- `EntityForm`：新建/编辑表单
- `EntityCrudPage`：完整 CRUD 页（搜索+表格+分页）
- `useAuth()`：认证状态与操作（user、fetchCaptcha、login、logout、refreshMe）
- `registerEntityConfig(pathSegment, config)`：实体级配置覆盖

### 实体配置

```ts
import { registerEntityConfig } from '@lrenyi/dataforge-headless/vue';

registerEntityConfig('users', {
  displayName: '用户',
  columns: [
    { prop: 'username', label: '用户名' },
    { prop: 'status', label: '状态', formatter: (v) => (v === 1 ? '启用' : '禁用') },
  ],
  searchFields: ['username', 'status'],
});
```

## 依赖

- Vue 3
- Element Plus（组件使用）
- template-dataforge 后端（GET /api/docs、CRUD 接口）

## 开发

```bash
npm install
npm run build
```
