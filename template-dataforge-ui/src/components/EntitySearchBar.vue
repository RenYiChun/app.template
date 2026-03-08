<template>
  <div class="entity-search-bar">
    <slot v-if="$slots.default" :meta="entityMeta" :on-export="handleExport" :on-reset="handleReset"
          :on-search="handleSearch"/>
    <el-form v-else :model="formModel" class="search-form" label-width="80px">
      <div class="search-fields">
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
                v-else-if="f.foreignKey && f.referencedEntity && getOptions"
                v-model="formModel[f.field]"
                :placeholder="formatText(selectPlaceholder, { label: f.label })"
                filterable
                remote
                :remote-method="(q: string) => loadSearchOptions(f.field, f.referencedEntity!, q)"
                :loading="searchOptionsLoading[f.field]"
                clearable
            >
              <el-option :label="allText" value=""/>
              <el-option
                  v-for="opt in (searchOptionsByKey[f.field] || [])"
                  :key="String(opt.id)"
                  :label="opt.label"
                  :value="opt.id"
              />
            </el-select>
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
      </div>
      <div class="search-actions-bar">
        <el-button :icon="Search" type="primary" @click="handleSearch">{{ searchText }}</el-button>
        <el-button :icon="Refresh" @click="handleReset">{{ resetText }}</el-button>
      </div>
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
      /** 关联实体选项加载，用于 searchable + foreignKey 字段的下拉 */
      getOptions?: (entityName: string, params?: { query?: string; page?: number; size?: number }) => Promise<{ content: { id: string | number; label: string }[] }>;
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
  foreignKey?: boolean;
  referencedEntity?: string;
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

  // 如果提供了 fields prop，则优先使用，并按元数据 order 排序
  if (props.fields?.length) {
    return props.fields
        .map((f) => {
          const fieldName = typeof f === 'string' ? f : f.field;
          const info = qf[fieldName] ?? {type: 'string', operators: [Op.EQ, Op.NE, Op.LIKE]};

          const label = (typeof f === 'object' && f.label) ? f.label : ((info as any).label || fieldName);

          const fieldMeta = meta?.fields?.find((fm: any) => fm.name === fieldName);
          return {
            field: fieldName,
            label: label,
            type: (typeof f === 'object' && f.type) ? f.type : (info.type ?? 'string'),
            operators: info.operators ?? [Op.EQ, Op.NE, Op.LIKE],
            order: (info as any).order ?? 0,
            foreignKey: fieldMeta?.foreignKey,
            referencedEntity: fieldMeta?.referencedEntity,
          };
        })
        .sort((a, b) => a.order - b.order);
}

  // 否则使用 queryableFields 中的所有字段，并排序
  const list = Object.keys(qf);
  const fieldsList = meta?.fields ?? [];
  return list
      .map((f) => {
        const info = qf[f];
        const fieldMeta = fieldsList.find((fm: any) => fm.name === f);
        return {
          field: f,
          label: (info as any).label || f,
          type: info.type ?? 'string',
          operators: info.operators ?? [Op.EQ, Op.NE, Op.LIKE],
          order: (info as any).order ?? 0,
          foreignKey: fieldMeta?.foreignKey,
          referencedEntity: fieldMeta?.referencedEntity,
        };
      })
      .sort((a, b) => a.order - b.order);
});

const formModel = reactive<Record<string, unknown>>({});
const searchOptionsByKey = reactive<Record<string, { id: string | number; label: string }[]>>({});
const searchOptionsLoading = reactive<Record<string, boolean>>({});
const getOptions = props.getOptions;

async function loadSearchOptions(fieldKey: string, entityName: string, query: string) {
  if (!getOptions) return;
  searchOptionsLoading[fieldKey] = true;
  try {
    const res = await getOptions(entityName, { query: query || undefined, page: 0, size: 50 });
    searchOptionsByKey[fieldKey] = (res?.content ?? []).map((o: { id: string | number; label: string }) => ({ id: o.id, label: o.label }));
  } finally {
    searchOptionsLoading[fieldKey] = false;
  }
}

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
.entity-search-bar {
  width: 100%;
}

.entity-search-bar .search-form {
  display: flex;
  flex-direction: column;
  width: 100%;
}

.entity-search-bar .search-fields {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
  align-items: center;
  width: 100%;
  min-width: 0;
}

.entity-search-bar .search-form .el-form-item {
  margin-bottom: 0;
  margin-right: 0;
  width: 100%;
}

.entity-search-bar .search-form .el-form-item__label {
  line-height: 28px;
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

/* 操作栏：底部独立一行，右对齐 */
.entity-search-bar .search-actions-bar {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 16px;
  margin-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
  width: 100%;
}

/* Responsive adjustments */
@media (max-width: 1400px) {
  .entity-search-bar .search-fields {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 992px) {
  .entity-search-bar .search-fields {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .entity-search-bar .search-fields {
    grid-template-columns: 1fr;
  }

  .entity-search-bar .search-form .el-form-item.is-range {
    grid-column: span 1;
  }

  .entity-search-bar .search-actions-bar {
    justify-content: flex-start;
  }
}
</style>
