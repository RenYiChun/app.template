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
        :label="actionsText"
        fixed="right"
        :width="calculatedActionsWidth"
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
    <el-empty v-else-if="!loading && items.length === 0" :description="noDataText" />
  </div>
</template>

<script setup lang="ts">
import { computed, useSlots } from 'vue';
import type { ColumnConfig } from '@lrenyi/platform-headless/vue';

const slots = useSlots();

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
    locale?: {
      common?: {
        actions?: string;
        view?: string;
        edit?: string;
        delete?: string;
        noData?: string;
      };
    };
  }>(),
  {
    loading: false,
    rowActions: () => ['view', 'edit', 'delete'],
    selectable: false,
    rowKey: 'id',
    // rowActionsWidth 默认不设值，通过 computed 自动计算
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
  ({
    view: props.locale?.common?.view ?? '查看',
    edit: props.locale?.common?.edit ?? '编辑',
    delete: props.locale?.common?.delete ?? '删除',
  }[act] ?? act);

const actionsText = computed(() => props.locale?.common?.actions ?? '操作');
const noDataText = computed(() => props.locale?.common?.noData ?? '暂无数据');

const actionProps = (act: string) => {
  const common = { link: true };
  if (act === 'delete') {
    return { ...common, type: 'danger' as const };
  }
  return { ...common, type: 'primary' as const };
};

const emitAction = (act: string, row: Record<string, unknown>) => {
  if (act === 'view') emit('view', row);
  else if (act === 'edit') emit('edit', row);
  else if (act === 'delete') emit('delete', row);
  else emit('action', act, row);
};

// 自动计算操作列宽度
const calculatedActionsWidth = computed(() => {
  // 1. 如果 props 显式传入了宽度，优先使用
  if (props.rowActionsWidth) return props.rowActionsWidth;

  // 2. 如果没有自定义插槽，且有默认的操作按钮，根据按钮数量和文字长度估算宽度
  if (!slots['row-actions'] && props.rowActions && props.rowActions.length > 0) {
    const buttonsWidth = props.rowActions.reduce((acc, act, index) => {
      const label = actionLabel(act);
      // 估算：每个汉字约 14px，英文约 8px。
      // 文字链接没有边框和大的内边距，所以 buffer 可以减小。
      // 这里简化为：字符数 * 14 + 12 (margin)
      const textWidth = label.length * 14;
      const btnWidth = textWidth + 12; 
      
      // 按钮之间的间距（Element Plus 默认 margin-left: 12px）
      // 文字链接可能更紧凑，但保持一致性
      const margin = index > 0 ? 0 : 0; // margin 包含在 btnWidth 估算里了，或者忽略
      
      return acc + btnWidth + margin;
    }, 0);

    // 加上单元格左右 padding (通常是 12px * 2 = 24px)
    // 额外加 10px buffer 以防万一
    return buttonsWidth + 24 + 10;
  }

  // 3. 如果使用了插槽或者无法估算，使用默认值 180
  return 180;
});

const onSelectionChange = (rows: Record<string, unknown>[]) => {
  const key = props.rowKey ?? 'id';
  const ids = rows
    .map((r) => r?.[key] as unknown)
    .filter((v): v is string | number => typeof v === 'string' || typeof v === 'number');
  emit('selection-change', rows, ids);
};
</script>
