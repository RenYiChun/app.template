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
        <span class="batch-actions__hint">已选 {{ selectedIds.length }} 项</span>
        <el-button type="danger" size="small" @click="handleBatchDelete">批量删除</el-button>
      </div>

      <EntityTable
        :columns="columns"
        :items="items"
        :loading="loading"
        :row-actions="rowActions"
        :selectable="selectable"
        :row-key="rowKey"
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
import { ElMessageBox } from 'element-plus';
import EntitySearchBar from './EntitySearchBar.vue';
import EntityTable from './EntityTable.vue';
import { usePlatform, useEntityMeta, useEntityCrud, resolveColumns, getEntityConfig } from '@lrenyi/platform-headless/vue';
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
    confirmBatchDelete?: boolean;
    shadow?: 'always' | 'hover' | 'never';
    baseFilters?: FilterCondition[];
    immediate?: boolean;
  }>(),
  {
    rowActions: () => ['view', 'edit', 'delete'],
    selectable: false,
    rowKey: 'id',
    enableCreate: true,
    confirmBatchDelete: true,
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
      await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 条记录吗？`, '提示', { type: 'warning' });
    } catch (e) {
      return;
    }
  }
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
.entity-crud-page .card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.entity-crud-page .batch-actions {
  margin-bottom: 12px;
  display: flex;
  gap: 12px;
  align-items: center;
}

.entity-crud-page .batch-actions__hint {
  color: var(--el-text-color-regular);
}

.entity-crud-page .el-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
