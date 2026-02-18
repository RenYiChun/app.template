<template>
  <div class="entity-table">
    <el-table
      v-loading="loading"
      :data="items"
      border
      stripe
      :row-key="rowKey"
      v-bind="$attrs"
      @selection-change="onSelectionChange"
    >
      <el-table-column
        v-if="selectable"
        type="selection"
        width="55"
      />
      <el-table-column
        v-for="col in columns"
        :key="col.prop"
        :prop="col.prop"
        :label="col.label ?? col.prop"
        :width="col.width"
        :sortable="col.sortable"
        show-overflow-tooltip
      >
        <template #default="scope">
          <slot
            v-if="$slots[`column-${col.prop}`]"
            :name="`column-${col.prop}`"
            :row="scope.row"
            :value="scope.row[col.prop]"
          />
          <span v-else-if="col.formatter && scope.row[col.prop] !== undefined">{{
            col.formatter!(scope.row[col.prop], scope.row)
          }}</span>
          <span v-else>{{ scope.row[col.prop] }}</span>
        </template>
      </el-table-column>
      <el-table-column
        v-if="rowActions?.length || $slots['row-actions']"
        label="操作"
        fixed="right"
        :width="rowActionsWidth"
      >
        <template #default="scope">
          <slot v-if="$slots['row-actions']" name="row-actions" :row="scope.row" />
          <template v-else>
            <el-button
              v-for="act in rowActions"
              :key="act"
              v-bind="actionProps(act)"
              size="small"
              @click="emitAction(act, scope.row)"
            >
              {{ actionLabel(act) }}
            </el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && items.length === 0 && $slots.empty" description="">
      <slot name="empty" />
    </el-empty>
    <el-empty v-else-if="!loading && items.length === 0" description="暂无数据" />
  </div>
</template>

<script setup lang="ts">
import type { ColumnConfig } from '@lrenyi/platform-headless/vue';

const props = withDefaults(
  defineProps<{
    /** 列配置 */
    columns: ColumnConfig[];
    /** 表格数据 */
    items: Record<string, unknown>[];
    /** 加载中 */
    loading?: boolean;
    /** 行操作：view | edit | delete 或自定义 */
    rowActions?: string[];
    /** 是否可选 */
    selectable?: boolean;
    /** 行唯一标识字段 */
    rowKey?: string;
    /** 操作列宽度 */
    rowActionsWidth?: number;
  }>(),
  {
    loading: false,
    rowActions: () => ['view', 'edit', 'delete'],
    selectable: false,
    rowKey: 'id',
    rowActionsWidth: 180,
  }
);

const emit = defineEmits<{
  (e: 'view', row: Record<string, unknown>): void;
  (e: 'edit', row: Record<string, unknown>): void;
  (e: 'delete', row: Record<string, unknown>): void;
  (e: 'action', action: string, row: Record<string, unknown>): void;
  (e: 'selection-change', rows: Record<string, unknown>[], ids: Array<string | number>): void;
}>();

const actionLabel = (act: string) =>
  ({ view: '查看', edit: '编辑', delete: '删除' }[act] ?? act);

const actionProps = (act: string) =>
  act === 'delete' ? { type: 'danger' as const } : act === 'view' ? {} : { type: 'primary' as const };

const emitAction = (act: string, row: Record<string, unknown>) => {
  if (act === 'view') emit('view', row);
  else if (act === 'edit') emit('edit', row);
  else if (act === 'delete') emit('delete', row);
  else emit('action', act, row);
};

const onSelectionChange = (rows: Record<string, unknown>[]) => {
  const key = props.rowKey ?? 'id';
  const ids = rows
    .map((r) => r?.[key] as unknown)
    .filter((v): v is string | number => typeof v === 'string' || typeof v === 'number');
  emit('selection-change', rows, ids);
};
</script>
