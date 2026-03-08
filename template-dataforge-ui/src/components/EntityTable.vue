<template>
  <div class="entity-table">
    <el-table
        v-loading="loading"
        :data="items"
        :row-key="getRowKey"
        border
        stripe
        v-bind="$attrs"
        @selection-change="onSelectionChange"
    >
      <el-table-column
          v-if="selectable"
          :reserve-selection="true"
          type="selection"
          width="55"
      />
      <el-table-column
          v-for="col in columnsWithWidth"
          :key="col.prop"
          :label="col.label ?? col.prop"
          :prop="col.prop"
          :sortable="col.sortable"
          :width="col.width"
          :min-width="col.minWidth"
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
          :width="calculatedActionsWidth"
          fixed="right"
      >
        <template #default="scope">
          <slot v-if="$slots['row-actions']" :row="scope.row" name="row-actions"/>
          <template v-else>
            <el-button
                v-for="act in rowActions"
                :key="act"
                size="small"
                v-bind="actionProps(act)"
                @click="emitAction(act, scope.row)"
            >
              {{ actionLabel(act) }}
            </el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && items.length === 0 && $slots.empty" description="">
      <slot name="empty"/>
    </el-empty>
    <el-empty v-else-if="!loading && items.length === 0" :description="noDataText"/>
  </div>
</template>

<script lang="ts" setup>
import {computed, useSlots} from 'vue';
import type {ColumnConfig} from '@lrenyi/dataforge-headless/vue';

/** 列配置（含 width/minWidth 计算后的结果） */
type ColumnWithWidth = ColumnConfig & { minWidth?: number };

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
      /** 操作列宽度（显式指定时使用） */
      rowActionsWidth?: number;
      /** 自定义插槽时传入的按钮文案列表，用于精确计算列宽（宽度=内容宽度，无多余） */
      rowActionsLabels?: string[];
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
      rowActions: () => ['view', 'edit', 'delete'], // 默认含详情、编辑、删除
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

/** 使用函数形式的 row-key，确保 Element Plus 多选列正确渲染勾选框。
 * 当 row-key 指向的字段为 undefined/null 时，Element Plus 会导致勾选框不显示（见 element-plus#16388）。
 * 因此当主键缺失时使用行索引作为后备，确保每行有唯一 key。 */
const getRowKey = (row: Record<string, unknown>) => {
  const key = props.rowKey ?? 'id';
  const v = row?.[key];
  if (v != null) return String(v);
  const idx = props.items.indexOf(row);
  return idx >= 0 ? `__idx_${idx}` : `__row_${crypto.randomUUID()}`;
};

const actionLabel = (act: string) =>
    ({
      view: props.locale?.common?.view ?? '查看',
      edit: props.locale?.common?.edit ?? '编辑',
      delete: props.locale?.common?.delete ?? '删除',
    }[act] ?? act);

const actionsText = computed(() => props.locale?.common?.actions ?? '操作');
const noDataText = computed(() => props.locale?.common?.noData ?? '暂无数据');

/** 估算文本渲染宽度（px）：中文约 14px/字，英文数字约 8px/字 */
function estimateTextWidth(str: string): number {
  if (!str) return 0;
  let w = 0;
  for (const c of str) {
    if (c >= '\u4e00' && c <= '\u9fff') w += 14;
    else if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) w += 8;
    else w += 8;
  }
  return w;
}

/** 当 columnWidth=0 时，Element Plus 不传 width 会均分剩余空间而非按内容收缩。此处对无显式宽度的列按内容估算宽度，实现近似「自动宽度」。 */
const columnsWithWidth = computed<ColumnWithWidth[]>(() => {
  const MIN_WIDTH = 80;
  const PADDING = 24;
  return props.columns.map((col) => {
    if (col.width != null && Number(col.width) > 0) return col;
    if (col.width != null && Number(col.width) === -1) {
      const label = String(col.label ?? col.prop ?? '');
      return {
        ...col,
        width: undefined,
        minWidth: Math.max(MIN_WIDTH, estimateTextWidth(label) + PADDING),
      };
    }
    const label = String(col.label ?? col.prop ?? '');
    let maxW = estimateTextWidth(label);
    for (const row of props.items) {
      const val = row[col.prop];
      const display = col.formatter ? col.formatter(val, row) : String(val ?? '');
      maxW = Math.max(maxW, estimateTextWidth(display));
    }
    return {...col, width: Math.max(MIN_WIDTH, maxW + PADDING)};
  });
});

const actionProps = (act: string) => {
  const common = {link: true};
  if (act === 'delete') {
    return {...common, type: 'danger' as const};
  }
  return {...common, type: 'primary' as const};
};

const emitAction = (act: string, row: Record<string, unknown>) => {
  if (act === 'view') emit('view', row);
  else if (act === 'edit') emit('edit', row);
  else if (act === 'delete') emit('delete', row);
  else emit('action', act, row);
};

/** 根据按钮文案列表精确计算操作列宽度（刚好等于内容宽度，无多余） */
function calcActionsWidthFromLabels(labels: string[]): number {
  if (!labels.length) return 120;
  const BTN_PADDING = 10;   // el-button link 水平内边距（紧凑）
  const GAP = 5;            // 按钮间距
  const CELL_PADDING = 16;  // 单元格左右 padding
  const total = labels.reduce((acc, label) => acc + estimateTextWidth(label) + BTN_PADDING, 0)
      + GAP * (labels.length - 1)
      + CELL_PADDING;
  return Math.ceil(total);
}

// 自动计算操作列宽度（精确等于内容宽度）
const calculatedActionsWidth = computed(() => {
  // 1. 显式传入宽度时使用
  if (props.rowActionsWidth) return props.rowActionsWidth;

  // 2. 自定义插槽时，若传入 rowActionsLabels 则按文案精确计算
  if (slots['row-actions'] && props.rowActionsLabels?.length) {
    return calcActionsWidthFromLabels(props.rowActionsLabels);
  }

  // 3. 默认操作按钮（view/edit/delete），按文案精确计算
  if (!slots['row-actions'] && props.rowActions && props.rowActions.length > 0) {
    const labels = props.rowActions.map((act) => actionLabel(act));
    return calcActionsWidthFromLabels(labels);
  }

  // 4. 插槽但未传 labels，使用保守默认
  return 200;
});

const onSelectionChange = (rows: Record<string, unknown>[]) => {
  const key = props.rowKey ?? 'id';
  const ids = rows
      .map((r) => r?.[key] as unknown)
      .filter((v): v is string | number => typeof v === 'string' || typeof v === 'number');
  emit('selection-change', rows, ids);
};
</script>
