<script lang="ts" setup>
import {computed, ref} from 'vue';
import {useEntityCrud, type TreeNode} from '../composables/useEntityCrud';
import type {SortOrder} from '@lrenyi/dataforge-headless/vue';

interface CrudEntity extends Record<string, unknown> {
  id: string | number;
}

const props = defineProps<{
  entity: string;
}>();

const emit = defineEmits<(e: 'batch-update') => void>();

const {
  items,
  pagedResult,
  loading,
  filters,
  sort,
  page,
  size,
  selectedIds,
  setFilters,
  setPage,
  setSize,
  setSelectedIds,
  search,
  init,
  delete: deleteEntities,
  entityMeta,
  metaLoading,
  refreshMeta,
  allColumns,
  displayColumns,
  visibleColumnProps,
  setVisibleColumnProps,
  exportExcel,
  error,
  canBatchDelete,
  canBatchUpdate,
  selectable,
  isMasterDetailTree,
  masterDetailTreeConfig,
  treeData,
  treeLoading,
  treeSelectedKey,
  onTreeSelect,
  listBlockedByTreeSelection,
} = useEntityCrud<CrudEntity>(props.entity);

const showSearch = ref(true);
const toggleSearch = () => {
  showSearch.value = !showSearch.value;
};

// 搜索为 POST 请求体，不再把 filters/sort/page/size 同步到 URL，避免冗余与 URL 过长
// 分页为 0 基（page 0 = 第 1 页），点击查询重置到第 1 页
const handleSearch = () => {
  setPage(0);
  search();
};

const handleExport = async () => {
  try {
    await exportExcel();
  } catch (e: unknown) {
    if (e instanceof Error && listBlockedByTreeSelection.value) {
      return;
    }
    throw e;
  }
};

const handleDelete = async () => {
  if (selectedIds.value.length > 0) {
    await deleteEntities();
    setSelectedIds([]);
    search();
  }
};

const handlePageChange = (currentPage: number) => {
  setPage(currentPage);
  search();
};

const handleSizeChange = (currentPageSize: number) => {
  setSize(currentPageSize);
  setPage(0);
  search();
};

const handleSortChange = (newSort: SortOrder[]) => {
  sort.value = newSort;
  search();
};

const handleSelectionChange = (_: unknown, ids: (string | number)[]) => {
  setSelectedIds(ids);
};

/** 批量更新：由父组件监听 @batch-update 并打开弹窗或调用 API */
const handleBatchUpdate = () => {
  emit('batch-update');
};

const total = computed(() => pagedResult.value?.totalElements ?? 0);

/** 树节点点击：el-tree 传入 (data) 即 TreeNode */
const handleTreeSelect = (data: unknown) => {
  onTreeSelect((data as TreeNode | null) ?? null);
};
</script>

<style scoped>
/* 与 git 历史 d214830 中 EntityCrudPage 的布局与样式保持一致 */

/* 历史版本：无底层背景 */
.entity-crud-page {
  padding: 10px;
  min-height: 100%;
}

/* 顶部搜索卡片 */
.crud-search-card {
  background-color: var(--el-bg-color-overlay);
  border-radius: 8px;
  box-shadow: var(--el-box-shadow-lighter);
  padding: 16px;
  margin-bottom: 10px;
  overflow: hidden;
  max-height: 500px;
  transition: all 0.3s ease-in-out;
  border-top: 2px solid var(--el-color-primary);
}

.crud-search-card.is-hidden {
  margin-bottom: 0;
  padding-top: 0;
  padding-bottom: 0;
  max-height: 0;
  opacity: 0;
  border: none;
  box-shadow: none;
}

/* 底部表格操作卡片 */
.crud-table-card {
  background-color: var(--el-bg-color-overlay);
  border-radius: 8px;
  box-shadow: var(--el-box-shadow-lighter);
  padding: 12px;
  border-top: 2px solid var(--el-color-primary);
}

/* 工具栏区域（slot 内为 EntityToolbar，保持与历史一致的左右布局） */
.crud-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.crud-toolbar :deep(.entity-toolbar) {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  margin-bottom: 0;
}

.crud-toolbar :deep(.toolbar-left) {
  display: flex;
  gap: 12px;
  align-items: center;
}

.crud-toolbar :deep(.toolbar-right) {
  display: flex;
  gap: 8px;
  align-items: center;
}

.crud-toolbar :deep(.toolbar-right .el-button) {
  margin-left: 0;
}

/* 表格区域 */
.crud-table {
  margin-bottom: 16px;
  background-color: transparent;
  border: none;
  padding: 0;
}

/* 分页区域：右对齐 */
.crud-pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 0;
  border-top: none;
}

/* 覆盖表格默认样式，使其更美观 */
.entity-crud-page :deep(.el-table) {
  --el-table-header-bg-color: var(--el-color-primary-light-9);
  --el-table-header-text-color: var(--el-text-color-primary);
  border-radius: 4px;
  overflow: hidden;
}

.entity-crud-page :deep(.column-setting-popover .popover-title) {
  font-weight: bold;
  margin-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  padding-bottom: 8px;
}

.entity-crud-page :deep(.column-setting-popover .column-checkbox-item) {
  margin-bottom: 4px;
}

/* 左树右表布局 */
.master-detail-layout {
  display: flex;
  gap: 12px;
  min-height: 400px;
}

.master-detail-tree-pane {
  flex: 0 0 240px;
  background-color: var(--el-bg-color-overlay);
  border-radius: 8px;
  box-shadow: var(--el-box-shadow-lighter);
  padding: 12px;
  overflow: auto;
}

.master-detail-tree-pane .tree-pane-title {
  font-weight: 600;
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.master-detail-content-pane {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.master-detail-content-pane .crud-table-card {
  flex: 1;
}

.master-detail-block-hint {
  margin-bottom: 10px;
}
</style>

<template>
  <div class="entity-crud-page">
    <slot :error="error" name="alert"/>

    <!-- 左树右表布局 -->
    <template v-if="isMasterDetailTree">
      <div class="master-detail-layout">
        <aside class="master-detail-tree-pane">
          <div class="tree-pane-title">{{ masterDetailTreeConfig?.treeEntityLabel || masterDetailTreeConfig?.treeEntity || '树' }}</div>
          <el-tree
            v-loading="treeLoading"
            :data="treeData"
            :current-node-key="treeSelectedKey ?? undefined"
            :props="{ label: 'label', value: 'id' }"
            default-expand-all
            highlight-current
            node-key="id"
            @node-click="handleTreeSelect"
          />
        </aside>
        <main class="master-detail-content-pane">
          <el-alert
              v-if="listBlockedByTreeSelection"
              class="master-detail-block-hint"
              show-icon
              type="info"
          >
            请先选择左侧树节点以查询、分页或导出数据
          </el-alert>
          <div :class="{ 'is-hidden': !showSearch }" class="crud-search-card">
            <slot :entityMeta="entityMeta" :filters="filters" :handleSearch="handleSearch" :setFilters="setFilters"
                  :showSearch="showSearch" name="search"/>
          </div>
          <div class="crud-table-card">
            <div class="crud-toolbar">
        <slot
            :allColumns="allColumns"
            :canBatchDelete="canBatchDelete"
            :canBatchUpdate="canBatchUpdate"
            :displayColumns="displayColumns"
            :handleBatchUpdate="handleBatchUpdate"
            :handleDelete="handleDelete"
            :handleExport="handleExport"
            :handleSearch="handleSearch"
            :listBlockedByTreeSelection="listBlockedByTreeSelection"
            :selectedIds="selectedIds"
            :setVisibleColumnProps="setVisibleColumnProps"
            :showSearch="showSearch"
            :toggleSearch="toggleSearch"
            :visibleColumnProps="visibleColumnProps"
            name="toolbar"
          />
            </div>
            <div class="crud-table">
              <slot
                  :displayColumns="displayColumns"
                  :handleSelectionChange="handleSelectionChange"
                  :handleSortChange="handleSortChange"
                  :items="items"
                  :loading="loading"
                  :selectable="selectable"
                  :sort="sort"
                  name="table"
              />
            </div>
            <div class="crud-pagination">
              <slot
                  :handlePageChange="handlePageChange"
                  :handleSizeChange="handleSizeChange"
                  :page="page"
                  :size="size"
                  :total="total"
                  name="pagination"
              />
            </div>
          </div>
        </main>
      </div>
    </template>

    <!-- 普通表格布局 -->
    <template v-else>
      <div :class="{ 'is-hidden': !showSearch }" class="crud-search-card">
        <slot :entityMeta="entityMeta" :filters="filters" :handleSearch="handleSearch" :setFilters="setFilters"
              :showSearch="showSearch" name="search"/>
      </div>
      <div class="crud-table-card">
      <!-- 工具栏 -->
      <div class="crud-toolbar">
        <slot
            :allColumns="allColumns"
            :canBatchDelete="canBatchDelete"
            :canBatchUpdate="canBatchUpdate"
            :displayColumns="displayColumns"
            :handleBatchUpdate="handleBatchUpdate"
            :handleDelete="handleDelete"
            :handleExport="handleExport"
            :handleSearch="handleSearch"
            :listBlockedByTreeSelection="listBlockedByTreeSelection"
            :selectedIds="selectedIds"
            :setVisibleColumnProps="setVisibleColumnProps"
            :showSearch="showSearch"
            :toggleSearch="toggleSearch"
            :visibleColumnProps="visibleColumnProps"
            name="toolbar"
        />
      </div>

      <!-- 表格 -->
      <div class="crud-table">
        <slot
            :displayColumns="displayColumns"
            :handleSelectionChange="handleSelectionChange"
            :handleSortChange="handleSortChange"
            :items="items"
            :loading="loading"
            :selectable="selectable"
            :sort="sort"
            name="table"
        />
      </div>

      <!-- 分页 -->
      <div class="crud-pagination">
        <slot
            :handlePageChange="handlePageChange"
            :handleSizeChange="handleSizeChange"
            :page="page"
            :size="size"
            :total="total"
            name="pagination"
        />
      </div>
    </div>
    </template>
  </div>
</template>
