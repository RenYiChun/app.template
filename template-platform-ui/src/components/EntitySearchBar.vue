<template>
  <div class="entity-search-bar">
    <slot v-if="$slots.default" :meta="entityMeta" :on-search="handleSearch" :on-reset="handleReset" :on-export="handleExport" />
    <el-form v-else :inline="true" :model="formModel" class="search-form">
      <template v-for="f in searchFields" :key="f.field">
        <el-form-item :label="f.label">
          <el-input
            v-if="f.type === 'string' || f.type === 'String'"
            v-model="formModel[f.field]"
            :placeholder="`请输入${f.label}`"
            clearable
            @keyup.enter="handleSearch"
          />
          <el-select
            v-else-if="f.type === 'boolean' || f.type === 'Boolean'"
            v-model="formModel[f.field]"
            placeholder="全部"
            clearable
          >
            <el-option label="全部" :value="undefined" />
            <el-option label="是" :value="true" />
            <el-option label="否" :value="false" />
          </el-select>
          <el-date-picker
            v-else-if="['date', 'localdate', 'localdatetime', 'datetime', 'instant'].includes(f.type.toLowerCase())"
            v-model="formModel[f.field]"
            type="datetimerange"
            :placeholder="`选择${f.label}范围`"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            clearable
            @change="handleSearch"
          />
          <el-input
            v-else
            v-model="formModel[f.field]"
            :placeholder="`请输入${f.label}`"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
      </template>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <el-button 
          v-if="entityMeta?.exportEnabled" 
          type="success" 
          :loading="exportLoading"
          @click="handleExport"
        >
          导出
        </el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch, computed } from 'vue';
import type { FilterCondition, Op, EntityMeta } from '@lrenyi/platform-headless';

const props = withDefaults(
  defineProps<{
    entityMeta: EntityMeta | null;
    /** 限定可搜索的字段，不传则用 meta.queryableFields 全部 */
    fields?: string[];
    modelValue?: FilterCondition[];
    exportLoading?: boolean;
  }>(),
  { modelValue: () => [] }
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

const searchFields = computed<SearchField[]>(() => {
  const meta = props.entityMeta;
  const qf = meta?.queryableFields;
  // 直接使用后端返回的顺序（OpenAPI 中已排序），或者根据 order 字段再次排序
  const list = qf ? Object.keys(qf) : [];
  return list
    .map((f) => {
      const info = qf?.[f] ?? { type: 'string', operators: ['eq', 'ne', 'like'] as Op[] };
      return {
        field: f,
        // 优先使用后端返回的 label，否则回退到字段名
        label: (info as any).label || f,
        type: info.type ?? 'string',
        operators: info.operators ?? (['eq', 'ne', 'like'] as Op[]),
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
  { immediate: true }
);

const toFilters = (): FilterCondition[] => {
  const result: FilterCondition[] = [];
  for (const f of searchFields.value) {
    const v = formModel[f.field];
    if (v === undefined || v === null || v === '') continue;

    if (Array.isArray(v) && ['date', 'localdate', 'localdatetime', 'datetime', 'instant'].includes(f.type.toLowerCase())) {
      // 日期范围处理：拆分为 gte 和 lte
      if (v[0]) result.push({ field: f.field, op: 'gte', value: v[0] });
      if (v[1]) result.push({ field: f.field, op: 'lte', value: v[1] });
      continue;
    }

    const op = f.operators.includes('like') ? ('like' as Op) : ('eq' as Op);
    result.push({ field: f.field, op, value: v });
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
