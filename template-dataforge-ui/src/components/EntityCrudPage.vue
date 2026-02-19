<template>
  <div class="entity-crud-page">
    <!-- 顶部卡片：搜索区域 -->
    <div class="crud-search-card" :class="{ 'is-hidden': !showSearch }">
      <EntitySearchBar
        :entity-meta="entityMeta"
        :fields="searchFieldsConfig"
        :model-value="filters"
        :export-loading="exportLoading"
        :locale="locale"
        @update:model-value="onFiltersChange"
        @search="onSearch"
        @reset="onReset"
        @export="handleExport"
      >
        <template v-if="$slots.search" #default="slotProps">
          <slot name="search" v-bind="slotProps" />
        </template>
      </EntitySearchBar>
    </div>

    <!-- 底部卡片：操作和表格区域 -->
    <div class="crud-table-card">
      <!-- 工具栏 -->
      <div class="crud-toolbar">
        <div class="toolbar-left">
          <el-button v-if="canCreate" type="primary" @click="emit('create')" size="default">
            <el-icon class="el-icon--left"><Plus /></el-icon>{{ createText }}
          </el-button>
          <el-button v-if="canBatchDelete && isSelectable" type="danger" plain :disabled="!selectedIds.length" @click="handleBatchDelete" size="default">
            <el-icon class="el-icon--left"><Delete /></el-icon>{{ batchDeleteText }}
          </el-button>
          <el-button v-if="canBatchUpdate && isSelectable" type="primary" plain :disabled="!selectedIds.length" @click="handleBatchUpdate" size="default">
            <el-icon class="el-icon--left"><Edit /></el-icon>{{ batchUpdateText }}
          </el-button>
          <!-- 导出按钮 -->
          <el-button v-if="canExport" type="primary" plain :loading="exportLoading" @click="handleExport" size="default">
            <el-icon class="el-icon--left"><Download /></el-icon>{{ exportText }}
          </el-button>
          <slot name="toolbar-left" />
        </div>
        <div class="toolbar-right">
          <slot name="toolbar-right" />
          <el-tooltip :content="showSearch ? '隐藏搜索' : '显示搜索'" placement="top">
            <el-button circle :icon="Search" @click="showSearch = !showSearch" size="default" />
          </el-tooltip>
          <el-tooltip :content="refreshText" placement="top">
            <el-button circle :icon="Refresh" @click="onSearch" size="default" />
          </el-tooltip>
          <el-popover placement="bottom" :width="200" trigger="click">
            <template #reference>
              <el-button circle :icon="Setting" size="default" />
            </template>
            <div class="column-setting-popover">
              <div class="popover-title">列设置</div>
              <el-checkbox-group v-model="visibleColumnProps" direction="vertical">
                <div v-for="col in allColumns" :key="col.prop" class="column-checkbox-item">
                  <el-checkbox :value="col.prop">
                    {{ col.label || col.prop }}
                  </el-checkbox>
                </div>
              </el-checkbox-group>
            </div>
          </el-popover>
        </div>
      </div>

      <!-- 表格 -->
      <div class="crud-table" v-loading="loading">
        <EntityTable
          :columns="displayColumns"
          :items="items"
          :loading="loading"
          :row-actions="rowActions"
          :selectable="isSelectable"
          :row-key="rowKey"
          :locale="locale"
          @selection-change="onSelectionChange"
          @view="(row) => emit('view', row)"
          @edit="(row) => emit('edit', row)"
          @delete="(row) => emit('delete', row)"
          @action="(act, row) => emit('action', act, row)"
        >
          <template v-if="$slots['row-actions']" #row-actions="scope">
            <slot name="row-actions" v-bind="scope" />
          </template>
          <template v-if="$slots.empty" #empty>
            <slot name="empty" />
          </template>
        </EntityTable>
      </div>

      <!-- 分页 -->
      <div class="crud-pagination">
        <el-pagination
          v-model:current-page="pageOneBased"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="onSizeChange"
          @current-change="onPageChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus, Delete, Refresh, Download, Search, Setting, Edit } from '@element-plus/icons-vue';
import EntitySearchBar from './EntitySearchBar.vue';
import EntityTable from './EntityTable.vue';
import {
  usePlatform,
  useEntityMeta,
  useEntityCrud,
  resolveColumns,
  getEntityConfig,
} from '@lrenyi/platform-headless/vue';
import type { ColumnConfig } from '@lrenyi/platform-headless/vue';
import type { FilterCondition, SearchRequest } from '@lrenyi/platform-headless';

const props = withDefaults(
  defineProps<{
    entity: string;
    columns?: ColumnConfig[];
    searchFields?: string[];
    rowActions?: string[];
    selectable?: boolean;
    rowKey?: string;
    enableCreate?: boolean;
    enableExport?: boolean;
    confirmBatchDelete?: boolean;
    shadow?: 'always' | 'hover' | 'never';
    baseFilters?: FilterCondition[];
    immediate?: boolean;
    defaultPageSize?: number;
    locale?: {
      common?: {
        manageSuffix?: string;
        add?: string;
        search?: string;
        reset?: string;
        export?: string;
        batchDelete?: string;
        selectedCount?: string;
        batchDeleteConfirm?: string;
        tips?: string;
        actions?: string;
        view?: string;
        edit?: string;
        delete?: string;
        noData?: string;
        refresh?: string;
        paginationInfo?: string;
      };
      search?: {
        inputPlaceholder?: string;
        selectPlaceholder?: string;
        rangePlaceholder?: string;
        start?: string;
        end?: string;
        all?: string;
        yes?: string;
        no?: string;
      };
      form?: {
        inputPlaceholder?: string;
        selectPlaceholder?: string;
        required?: string;
        email?: string;
      };
      errors?: {
        exportFailed?: string;
      };
    };
  }>(),
  {
    rowActions: () => ['view', 'edit', 'delete'],
    selectable: false,
    rowKey: 'id',
    enableCreate: undefined,
    enableExport: undefined,
    confirmBatchDelete: true,
    shadow: 'always',
    baseFilters: () => [],
    immediate: true,
    defaultPageSize: 10,
  },
);

const emit = defineEmits<{
  (e: 'create'): void;
  (e: 'view', row: Record<string, unknown>): void;
  (e: 'edit', row: Record<string, unknown>): void;
  (e: 'delete', row: Record<string, unknown>): void;
  (e: 'batch-update', ids: (string | number)[]): void;
  (e: 'action', action: string, row: Record<string, unknown>): void;
}>();

const { client, meta } = usePlatform();
const { meta: entityMeta, loading: metaLoading, refresh: refreshMeta } = useEntityMeta(meta, props.entity);

// 强制触发一次 meta 加载检查
onMounted(async () => {
  if (!entityMeta.value) {
    try {
      await refreshMeta();
    } catch (e) {
      console.error('[EntityCrudPage] meta refresh failed:', e);
    }
  }
});

const crud = useEntityCrud(client, props.entity, {
  onError: (e) => console.error(e),
  initialPageSize: props.defaultPageSize,
});

const {
  items,
  total,
  loading,
  filters,
  sort,
  page,
  size,
  search,
  resetFilters,
  remove,
  removeBatch,
  exportExcel,
} = crud;

const showSearch = ref(true);

const exportLoading = ref(false);

const formatText = (template: string, vars?: Record<string, string | number>) => {
  if (!vars) return template;
  return template.replace(/\{(\w+)\}/g, (_, key) => String(vars[key] ?? ''));
};

const canCreate = computed(() => {
  if (props.enableCreate !== undefined) return props.enableCreate;
  return entityMeta.value ? !!entityMeta.value.operations?.create : true;
});

const canBatchDelete = computed(() => {
  return !!entityMeta.value?.operations?.deleteBatch;
});

const canBatchUpdate = computed(() => {
  return !!entityMeta.value?.operations?.updateBatch;
});

const canExport = computed(() => {
  if (props.enableExport !== undefined) return props.enableExport;
  return entityMeta.value ? (!!entityMeta.value.operations?.export || !!entityMeta.value.exportEnabled) : true;
});

const isSelectable = computed(() => {
  if (props.selectable) return true;
  return canBatchDelete.value || canBatchUpdate.value;
});

const manageSuffix = computed(() => props.locale?.common?.manageSuffix ?? '管理');
const createText = computed(() => props.locale?.common?.add ?? '新增');
const batchDeleteText = computed(() => props.locale?.common?.batchDelete ?? '批量删除');
const batchUpdateText = computed(() => props.locale?.common?.batchUpdate ?? '批量更新');
const exportText = computed(() => props.locale?.common?.export ?? '导出');
const refreshText = computed(() => props.locale?.common?.refresh ?? '刷新');
const selectedCountText = computed(() =>
  formatText(props.locale?.common?.selectedCount ?? '已选 {count} 项', { count: selectedIds.value.length }),
);
const paginationInfoText = computed(() => {
  const start = total.value === 0 ? 0 : (pageOneBased.value - 1) * size.value + 1;
  const end = Math.min(pageOneBased.value * size.value, total.value);
  const template = props.locale?.common?.paginationInfo ?? '显示第 {start} 到第 {end} 条记录，总共 {total} 条记录';
  return formatText(template, { start, end, total: total.value });
});

const handleExport = async () => {
  exportLoading.value = true;
  try {
    const blob = await exportExcel();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${props.entity}-export.xlsx`;
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(url);
    document.body.removeChild(a);
  } catch (e) {
    console.error('Export failed', e);
    ElMessage.error(props.locale?.errors?.exportFailed ?? '导出失败');
  } finally {
    exportLoading.value = false;
  }
};

const displayName = computed(() => entityMeta.value?.displayName ?? props.entity);
const searchFieldsConfig = computed(() => {
  // 优先使用 props.searchFields
  if (props.searchFields && props.searchFields.length > 0) {
    return props.searchFields;
  }

  // 其次尝试从 entityConfig 获取
  const config = getEntityConfig(props.entity);
  if (config?.searchFields && config.searchFields.length > 0) {
    return config.searchFields;
  }

  // 最后尝试从 meta.queryableFields 自动生成
  if (entityMeta.value?.queryableFields) {
    // 这里不需要手动转换，EntitySearchBar 会自动处理 meta.queryableFields
    // 但为了让 EntitySearchBar 正确渲染，我们需要确保传递了 entityMeta
    return []; // 返回空数组，让 EntitySearchBar 内部基于 meta 自动生成
  }

  return [];
});

// 调试日志：监控 entityMeta 的变化
watch(() => entityMeta.value, (newVal) => {
  // meta changed
}, { immediate: true });

watch(() => metaLoading.value, (newVal) => {
  // loading changed
});

const allColumns = computed(() => props.columns?.length ? props.columns : resolveColumns(props.entity, entityMeta.value) || []);

const visibleColumnProps = ref<string[]>([]);

watch(() => allColumns.value, (newVal) => {
  if (newVal && newVal.length > 0) {
    visibleColumnProps.value = newVal.map(col => col.prop);
  }
}, { immediate: true });

const displayColumns = computed(() => {
  if (!allColumns.value) return [];
  return allColumns.value.filter(col => visibleColumnProps.value.includes(col.prop));
});

const onFiltersChange = (v: Array<{ field: string; op: import('@lrenyi/platform-headless').Op; value: unknown }>) => {
  filters.value = v as import('@lrenyi/platform-headless').FilterCondition[];
};

const pageOneBased = computed({
  get: () => page.value + 1,
  set: (v) => {
    page.value = Math.max(0, v - 1);
  },
});

const selectedIds = ref<(string | number)[]>([]);

const onSearch = async () => {
  page.value = 0;
  await doSearch();
};

const onReset = async () => {
  resetFilters();
  await doSearch();
};

const onSizeChange = async () => {
  page.value = 0;
  await doSearch();
};

const onPageChange = async () => {
  await doSearch();
};

const doSearch = async (overrides?: Partial<SearchRequest>) => {
  selectedIds.value = [];
  const userFilters = overrides?.filters ?? filters.value;
  const finalFilters = [...(props.baseFilters || []), ...userFilters];
  await search({ ...overrides, filters: finalFilters });
};

const onSelectionChange = (_rows: Record<string, unknown>[], ids: Array<string | number>) => {
  selectedIds.value = ids;
};

const handleBatchDelete = async () => {
  if (selectedIds.value.length === 0) return;
  if (props.confirmBatchDelete) {
    try {
      const message = formatText(
        props.locale?.common?.batchDeleteConfirm ?? '确定删除选中的 {count} 条记录吗？',
        { count: selectedIds.value.length },
      );
      await ElMessageBox.confirm(message, props.locale?.common?.tips ?? '提示', { type: 'warning' });
    } catch (e) {
      return;
    }
  }
  await removeBatch(selectedIds.value);
  selectedIds.value = [];
  await doSearch();
};

const handleBatchUpdate = () => {
  if (selectedIds.value.length === 0) return;
  emit('batch-update', selectedIds.value);
};

// Watch baseFilters change to trigger search if immediate or already loaded
watch(() => props.baseFilters, () => {
  if (props.immediate || items.value.length > 0) {
    page.value = 0;
    doSearch();
  }
}, { deep: true });

onMounted(async () => {
  await refreshMeta();
  if (props.immediate) {
    await doSearch();
  }
});

defineExpose({
  refresh: doSearch,
  reset: onReset,
});
</script>

<style>
/* 整体页面容器 */
.entity-crud-page {
  padding: 10px;
  background-color: var(--el-fill-color-light);
  min-height: 100%;
}

/* 顶部搜索卡片 */
.entity-crud-page .crud-search-card {
  background-color: var(--el-bg-color-overlay);
  border-radius: 8px;
  box-shadow: var(--el-box-shadow-lighter);
  padding: 12px;
  margin-bottom: 10px;
  overflow: hidden;
  max-height: 500px;
  transition: all 0.3s ease-in-out;
  border-top: 2px solid var(--el-color-primary);
}

.entity-crud-page .crud-search-card.is-hidden {
  margin-bottom: 0;
  padding-top: 0;
  padding-bottom: 0;
  max-height: 0;
  opacity: 0;
  border: none;
  box-shadow: none;
}

/* 底部表格操作卡片 */
.entity-crud-page .crud-table-card {
  background-color: var(--el-bg-color-overlay);
  border-radius: 8px;
  box-shadow: var(--el-box-shadow-lighter);
  padding: 12px;
  border-top: 2px solid var(--el-color-primary);
}

/* 工具栏区域 */
.entity-crud-page .crud-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.entity-crud-page .toolbar-left {
  display: flex;
}

/* 工具栏按钮组 */
.entity-crud-page .toolbar-right {
  display: flex;
  gap: 8px;
  align-items: center;
}

.entity-crud-page .toolbar-right .el-button {
  margin-left: 0;
}

/* 表格区域 */
.entity-crud-page .crud-table {
  margin-bottom: 16px;
  background-color: transparent;
  border: none;
  padding: 0;
}

/* 分页区域：右对齐 */
.entity-crud-page .crud-pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 0;
  border-top: none;
}

/* 覆盖表格默认样式，使其更美观 */
.entity-crud-page .el-table {
  --el-table-header-bg-color: var(--el-color-primary-light-9);
  --el-table-header-text-color: var(--el-text-color-primary);
  border-radius: 4px;
  overflow: hidden;
}

.column-setting-popover .popover-title {
  font-weight: bold;
  margin-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  padding-bottom: 8px;
}

.column-setting-popover .column-checkbox-item {
  margin-bottom: 4px;
}
</style>
