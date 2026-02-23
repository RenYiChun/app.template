<script setup lang="ts">
import { ElButton, ElSpace, ElTooltip, ElIcon } from 'element-plus';
import { computed } from 'vue';
import { Plus, Delete, Edit, Download, Search, Refresh } from '@element-plus/icons-vue';
import EntityColumnConfigurator from './EntityColumnConfigurator.vue';
import type { ColumnConfig } from '@lrenyi/dataforge-headless/vue';

const props = withDefaults(defineProps<{
  selectedIds?: (string | number)[];
  canCreate?: boolean;
  createDisabled?: boolean;
  createText?: string;
  canBatchDelete?: boolean;
  batchDeleteText?: string;
  canBatchUpdate?: boolean;
  batchUpdateText?: string;
  canExport?: boolean;
  exportText?: string;
  refreshText?: string;
  showSearch?: boolean;
  allColumns?: ColumnConfig[];
  displayColumns?: ColumnConfig[];
  visibleColumnProps?: string[];
  setVisibleColumnProps?: (props: string[]) => void;
  loading?: boolean;
}>(), {
  selectedIds: () => [],
  canCreate: true,
  createDisabled: false,
  createText: '新增',
  canBatchDelete: false,
  batchDeleteText: '批量删除',
  canBatchUpdate: false,
  batchUpdateText: '批量更新',
  canExport: true,
  exportText: '导出',
  refreshText: '刷新',
  showSearch: true,
  allColumns: () => [],
  displayColumns: () => [],
  visibleColumnProps: () => [],
  setVisibleColumnProps: () => {},
  loading: false,
});

const emit = defineEmits<{
  (e: 'create'): void;
  (e: 'batch-delete'): void;
  (e: 'batch-update'): void;
  (e: 'export'): void;
  (e: 'toggle-search'): void;
  (e: 'refresh'): void;
}>();

const hasSelected = computed(() => props.selectedIds.length > 0);
</script>

<template>
  <div class="entity-toolbar">
    <div class="toolbar-left">
      <ElButton 
        v-if="canCreate" 
        type="primary" 
        :disabled="createDisabled"
        @click="emit('create')"
      >
        <ElIcon class="el-icon--left"><Plus /></ElIcon>{{ createText }}
      </ElButton>
      <ElButton 
        v-if="canBatchDelete && hasSelected" 
        type="danger" 
        plain 
        @click="emit('batch-delete')"
      >
        <ElIcon class="el-icon--left"><Delete /></ElIcon>{{ batchDeleteText }}
      </ElButton>
      <ElButton 
        v-if="canBatchUpdate && hasSelected" 
        type="primary" 
        plain 
        @click="emit('batch-update')"
      >
        <ElIcon class="el-icon--left"><Edit /></ElIcon>{{ batchUpdateText }}
      </ElButton>
      <slot name="left-actions"></slot>
    </div>

    <div class="toolbar-right">
      <slot name="right-actions"></slot>
      
      <ElTooltip v-if="canExport" :content="exportText" placement="top">
        <ElButton circle :icon="Download" @click="emit('export')" />
      </ElTooltip>
      
      <ElTooltip :content="showSearch ? '隐藏搜索' : '显示搜索'" placement="top">
        <ElButton circle :icon="Search" @click="emit('toggle-search')" />
      </ElTooltip>
      
      <ElTooltip :content="refreshText" placement="top">
        <ElButton circle :icon="Refresh" @click="emit('refresh')" />
      </ElTooltip>
      
      <EntityColumnConfigurator
        :all-columns="props.allColumns"
        :display-columns="props.displayColumns"
        :visible-column-props="props.visibleColumnProps"
        :set-visible-column-props="props.setVisibleColumnProps"
      />
    </div>
  </div>
</template>

<style scoped>
.entity-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  width: 100%;
}

.toolbar-left {
  display: flex;
  gap: 12px;
  align-items: center;
}

.toolbar-right {
  display: flex;
  gap: 8px;
  align-items: center;
}

.toolbar-right .el-button {
  margin-left: 0;
}
</style>
