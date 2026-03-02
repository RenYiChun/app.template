<template>
  <div class="entity-search-bar">
    <slot v-if="$slots.default" :meta="entityMeta" :on-export="handleExport" :on-reset="handleReset"
          :on-search="handleSearch"/>
    <el-form v-else :inline="true" :model="formModel" class="search-form" label-width="80px">
      <template v-for="f in searchFields" :key="f.field">
        <el-form-item :class="{ 'is-range': isDateField(f) }" :label="f.label">
          <el-input
              v-if="f.type === 'string' || f.type === 'String'"
              v-model="formModel[f.field]"
              :placeholder="formatText(inputPlaceholder, { label: f.label })"
              clearable
              @keyup.enter="handleSearch"
          />
          <el-select
              v-else-if="f.type === 'boolean' || f.type === 'Boolean'"
              v-model="formModel[f.field]"
              :placeholder="formatText(selectPlaceholder, { label: f.label })"
              clearable
          >
            <el-option :label="allText" value=""/>
            <el-option :label="yesText" :value="true"/>
            <el-option :label="noText" :value="false"/>
          </el-select>
          <el-date-picker
              v-else-if="isDateField(f)"
              v-model="formModel[f.field]"
              :end-placeholder="endPlaceholder"
              :placeholder="formatText(rangePlaceholder, { label: f.label })"
              :start-placeholder="startPlaceholder"
              clearable
              type="datetimerange"
              value-format="YYYY-MM-DD HH:mm:ss"
              @change="handleSearch"
          />
          <el-input
              v-else
              v-model="formModel[f.field]"
              :placeholder="formatText(inputPlaceholder, { label: f.label })"
              clearable
              @keyup.enter="handleSearch"
          />
        </el-form-item>
      </template>
      <el-form-item class="search-actions">
        <el-button :icon="Search" type="primary" @click="handleSearch">{{ searchText }}</el-button>
        <el-button :icon="Refresh" @click="handleReset">{{ resetText }}</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script lang="ts" setup>
import {computed, reactive, watch} from 'vue';
import {Refresh, Search} from '@element-plus/icons-vue';
import {EntityMeta, FilterCondition, Op} from '@lrenyi/dataforge-headless';

const props = withDefaults(
    defineProps<{
      entityMeta: EntityMeta | null;
      /** 限定可搜索的字段，不传则用 meta.queryableFields 全部 */
      fields?: string[] | any[];
      modelValue?: FilterCondition[];
      exportLoading?: boolean;
      locale?: {
        common?: {
          search?: string;
          reset?: string;
          export?: string;
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
      };
    }>(),
    {modelValue: () => []}
);

const emit = defineEmits<{
  (e: 'update:modelValue', v: FilterCondition[]): void;
  (e: 'search'): void;
  (e: 'reset'): void;
  (e: 'export'): void;
}>();

interface SearchField {
  field: string;
  label: string;
  type: string;
  operators: Op[];
  order: number;
}

const formatText = (template: string, vars?: Record<string, string | number>) => {
  if (!vars) return template;
  return template.replaceAll(/\{(\w+)\}/g, (_, key) => String(vars[key] ?? ''));
};

const searchText = computed(() => props.locale?.common?.search ?? '查询');
const resetText = computed(() => props.locale?.common?.reset ?? '重置');
const exportText = computed(() => props.locale?.common?.export ?? '导出');
const inputPlaceholder = computed(() => props.locale?.search?.inputPlaceholder ?? '请输入{label}');
const selectPlaceholder = computed(() => props.locale?.search?.selectPlaceholder ?? '请选择{label}');
const rangePlaceholder = computed(() => props.locale?.search?.rangePlaceholder ?? '选择{label}范围');
const startPlaceholder = computed(() => props.locale?.search?.start ?? '开始时间');
const endPlaceholder = computed(() => props.locale?.search?.end ?? '结束时间');
const allText = computed(() => props.locale?.search?.all ?? '全部');
const yesText = computed(() => props.locale?.search?.yes ?? '是');
const noText = computed(() => props.locale?.search?.no ?? '否');

const isDateField = (f: SearchField) =>
    ['date', 'localdate', 'localdatetime', 'datetime', 'instant'].includes(f.type.toLowerCase());

const searchFields = computed<SearchField[]>(() => {
  const meta = props.entityMeta;
  const qf = meta?.queryableFields ?? {};

  // 如果提供了 fields prop，则优先使用，并保持顺序
  if (props.fields?.length) {
    return props.fields.map((f) => {
      // 兼容传入的是字符串还是对象
      const fieldName = typeof f === 'string' ? f : f.field;
      const info = qf[fieldName] ?? {type: 'string', operators: [Op.EQ, Op.NE, Op.LIKE]};

      // 如果传入的是对象且有 label/type 等配置，优先使用
      const label = (typeof f === 'object' && f.label) ? f.label : ((info as any).label || fieldName);

      return {
        field: fieldName,
        label: label,
        type: (typeof f === 'object' && f.type) ? f.type : (info.type ?? 'string'),
        operators: info.operators ?? [Op.EQ, Op.NE, Op.LIKE],
        order: 0,
      };
    });
  }

  // 否则使用 queryableFields 中的所有字段，并排序
  const list = Object.keys(qf);
  return list
      .map((f) => {
        const info = qf[f];
        return {
          field: f,
          label: (info as any).label || f,
          type: info.type ?? 'string',
          operators: info.operators ?? [Op.EQ, Op.NE, Op.LIKE],
          order: (info as any).order ?? 0,
        };
      })
      .sort((a, b) => a.order - b.order);
});

const formModel = reactive<Record<string, unknown>>({});

watch(
    () => props.fields ?? props.entityMeta?.queryableFields,
    () => {
      for (const f of searchFields.value) {
        if (!(f.field in formModel)) formModel[f.field] = undefined;
      }
    },
    {immediate: true}
);

const toFilters = (): FilterCondition[] => {
  const result: FilterCondition[] = [];
  for (const f of searchFields.value) {
    const v = formModel[f.field];
    if (v === undefined || v === null || v === '') continue;

    if (Array.isArray(v) && ['date', 'localdate', 'localdatetime', 'datetime', 'instant'].includes(f.type.toLowerCase())) {
      // 日期范围处理：拆分为 gte 和 lte
      if (v[0]) result.push({field: f.field, op: Op.GE, value: v[0]});
      if (v[1]) result.push({field: f.field, op: Op.LE, value: v[1]});
      continue;
    }

    const op = f.operators.includes(Op.LIKE) ? Op.LIKE : Op.EQ;
    result.push({field: f.field, op, value: v});
  }
  return result;
};

const handleSearch = (customFilters?: FilterCondition[]) => {
  if (customFilters && Array.isArray(customFilters)) {
    emit('update:modelValue', customFilters);
  } else {
    emit('update:modelValue', toFilters());
  }
  emit('search');
};

const handleReset = () => {
  for (const f of searchFields.value) {
    formModel[f.field] = undefined;
  }
  emit('update:modelValue', []);
  emit('reset');
};

const handleExport = () => {
  emit('export');
};
</script>

<style>
.entity-search-bar .search-form {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
  align-items: center;
}

.entity-search-bar .search-form .el-form-item {
  margin-bottom: 0;
  margin-right: 0;
  width: 100%;
}

.entity-search-bar .search-form .el-form-item__label {
  line-height: 28px;
  /* Ensure label doesn't take too much space but stays aligned */
  white-space: nowrap;
}

.entity-search-bar .search-form .el-form-item__content {
  min-width: 0;
}

.entity-search-bar .search-form .el-input,
.entity-search-bar .search-form .el-select,
.entity-search-bar .search-form .el-date-editor {
  width: 100%;
}

/* Date range spans 2 columns */
.entity-search-bar .search-form .el-form-item.is-range {
  grid-column: span 2;
}

/* Actions group styling */
.entity-search-bar .search-actions {
  /* Allow natural flow */
  grid-column: auto;
  display: flex;
  justify-content: flex-start;
  min-width: auto;
}

.entity-search-bar .search-actions .el-form-item__content {
  justify-content: flex-end;
  gap: 8px;
}

/* Responsive adjustments */
@media (max-width: 1400px) {
  .entity-search-bar .search-form {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 992px) {
  .entity-search-bar .search-form {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .entity-search-bar .search-form {
    grid-template-columns: 1fr;
  }

  .entity-search-bar .search-form .el-form-item.is-range {
    grid-column: span 1;
  }

  .entity-search-bar .search-actions {
    justify-content: flex-start;
  }

  .entity-search-bar .search-actions .el-form-item__content {
    justify-content: flex-start;
  }
}
</style>
