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

// 自动计算操作列宽度
const calculatedActionsWidth = computed(() => {
  // 1. 如果 props 显式传入了宽度，优先使用
  if (props.rowActionsWidth) return props.rowActionsWidth;

  // 2. 如果没有自定义插槽，且有默认的操作按钮，根据按钮数量和文字长度估算宽度
  if (!slots['row-actions'] && props.rowActions && props.rowActions.length > 0) {
    const buttonsWidth = props.rowActions.reduce((acc, act) => {
      const label = actionLabel(act);
      // 估算：每个汉字约 14px，英文约 8px。
      // 文字链接没有边框和大的内边距，所以 buffer 可以减小。
      // 这里简化为：字符数 * 14 + 12 (margin)
      const textWidth = label.length * 14;
      const btnWidth = textWidth + 12;

      // 按钮之间的间距已包含在 btnWidth 估算中
      return acc + btnWidth;
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
