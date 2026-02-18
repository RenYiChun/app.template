<template>
  <div class="entity-crud-page">
    <el-card v-loading="metaLoading" :shadow="shadow">
      <template #header>
        <div class="card-header">
          <span>{{ displayName }}管理</span>
          <el-button v-if="enableCreate" type="primary" @click="emit('create')">新增</el-button>
        </div>
      </template>

      <EntitySearchBar
        :entity-meta="entityMeta"
        :fields="searchFieldsConfig"
        :model-value="filters"
        :export-loading="exportLoading"
        @update:model-value="onFiltersChange"
        @search="onSearch"
        @reset="onReset"
        @export="handleExport"
      >
        <template v-if="$slots.search" #default="slotProps">
          <slot name="search" v-bind="slotProps" />
        </template>
      </EntitySearchBar>

      <div v-if="selectable && selectedIds.length" class="batch-actions">
        <el-button v-if="selectable && selectedIds.length" type="danger" size="small" @click="handleBatchDelete">批量删除</el-button>
      </div>

      <EntityTable
        :columns="columns"
        :items="items"
        :loading="loading"
        :row-actions="rowActions"
        :selectable="selectable"
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

      <el-pagination
        v-model:current-page="pageOneBased"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="onSizeChange"
        @current-change="onPageChange"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import EntitySearchBar from './EntitySearchBar.vue';
import EntityTable from './EntityTable.vue';
import { usePlatform, useEntityMeta, useEntityCrud } from '@lrenyi/platform-headless/vue';
import { resolveColumns, getEntityConfig } from '../config.js';
import type { ColumnConfig } from '../config.js';
import type { FilterCondition, SearchRequest } from '@lrenyi/platform-headless';

const props = withDefaults(
  defineProps<{
    entity: string;
    columns?: ColumnConfig[];
    searchFields?: string[];
    rowActions?: string[];
    selectable?: boolean;
    enableCreate?: boolean;
    shadow?: 'always' | 'hover' | 'never';
    baseFilters?: FilterCondition[];
    immediate?: boolean;
  }>(),
  {
    rowActions: () => ['view', 'edit', 'delete'],
    selectable: false,
    enableCreate: true,
    shadow: 'always',
    baseFilters: () => [],
    immediate: true,
  }
);

const emit = defineEmits<{
  (e: 'create'): void;
  (e: 'view', row: Record<string, unknown>): void;
  (e: 'edit', row: Record<string, unknown>): void;
  (e: 'delete', row: Record<string, unknown>): void;
  (e: 'action', action: string, row: Record<string, unknown>): void;
}>();

const { client, meta } = usePlatform();
const { meta: entityMeta, loading: metaLoading, refresh: refreshMeta } = useEntityMeta(meta, props.entity);

const crud = useEntityCrud(client, props.entity, {
  onError: (e) => console.error(e),
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

const exportLoading = ref(false);

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
  } finally {
    exportLoading.value = false;
  }
};

const displayName = computed(() => entityMeta.value?.displayName ?? props.entity);
const columns = computed(() => props.columns?.length ? props.columns : resolveColumns(props.entity, entityMeta.value) || []);
const searchFieldsConfig = computed(() => {
  if (props.searchFields) return props.searchFields;
  const config = getEntityConfig(props.entity);
  return config?.searchFields;
});

const onFiltersChange = (v: Array<{ field: string; op: import('@lrenyi/platform-headless').Op; value: unknown }>) => {
  filters.value = v as import('@lrenyi/platform-headless').FilterCondition[];
};

const pageOneBased = computed({
  get: () => page.value + 1,
  set: (v) => { page.value = Math.max(0, v - 1); },
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
  const userFilters = overrides?.filters ?? filters.value;
  const finalFilters = [...(props.baseFilters || []), ...userFilters];
  await search({ ...overrides, filters: finalFilters });
};

const handleBatchDelete = async () => {
  if (selectedIds.value.length === 0) return;
  await removeBatch(selectedIds.value);
  selectedIds.value = [];
  await doSearch();
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
/* 实体 CRUD 页面布局样式 */
/* 注意：这里没有使用 scoped，以便允许样式穿透或被外部覆盖。但为了避免污染，使用了 .entity-crud-page 命名空间 */
.entity-crud-page .card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.entity-crud-page .batch-actions {
  margin-bottom: 12px;
}

.entity-crud-page .el-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
