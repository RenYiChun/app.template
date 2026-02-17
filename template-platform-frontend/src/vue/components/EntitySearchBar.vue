<template>
  <div class="entity-search-bar">
    <slot v-if="$slots.default" :meta="entityMeta" :on-search="handleSearch" :on-reset="handleReset" />
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
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch, computed } from 'vue';
import type { FilterCondition, Op } from '../../core/index.js';
import type { EntityMeta } from '../../core/index.js';

const props = withDefaults(
  defineProps<{
    entityMeta: EntityMeta | null;
    /** 限定可搜索的字段，不传则用 meta.queryableFields 全部 */
    fields?: string[];
    modelValue?: FilterCondition[];
  }>(),
  { modelValue: () => [] }
);

const emit = defineEmits<{
  (e: 'update:modelValue', v: FilterCondition[]): void;
  (e: 'search'): void;
  (e: 'reset'): void;
}>();

interface SearchField {
  field: string;
  label: string;
  type: string;
  operators: Op[];
}

const searchFields = computed<SearchField[]>(() => {
  const meta = props.entityMeta;
  const qf = meta?.queryableFields;
  const list = props.fields ?? (qf ? Object.keys(qf) : []);
  return list.map((f) => {
    const info = qf?.[f] ?? { type: 'string', operators: ['eq', 'ne', 'like'] as Op[] };
    return {
      field: f,
      label: f,
      type: info.type ?? 'string',
      operators: info.operators ?? (['eq', 'ne', 'like'] as Op[]),
    };
  });
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
    const op = f.operators.includes('like') ? ('like' as Op) : ('eq' as Op);
    result.push({ field: f.field, op, value: v });
  }
  return result;
};

const handleSearch = () => {
  emit('update:modelValue', toFilters());
  emit('search');
};

const handleReset = () => {
  for (const f of searchFields.value) {
    formModel[f.field] = undefined;
  }
  emit('update:modelValue', []);
  emit('reset');
};
</script>
