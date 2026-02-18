<template>
  <div class="entity-form">
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="120px">
      <template v-for="(schema, key) in formFields" :key="key">
        <el-form-item v-if="!readonlyFields.includes(key)" :label="key" :prop="key">
          <slot v-if="$slots[`field-${key}`]" :name="`field-${key}`" :model="formData" :field="key" />
          <el-input
            v-else-if="inferInputType(schema) === 'textarea'"
            v-model="formData[key]"
            type="textarea"
            :rows="3"
            :placeholder="`请输入${key}`"
          />
          <el-input
            v-else-if="inferInputType(schema) === 'password'"
            v-model="formData[key]"
            type="password"
            show-password
            :placeholder="`请输入${key}`"
          />
          <el-select
            v-else-if="schema.enum"
            v-model="formData[key]"
            :placeholder="`请选择${key}`"
            clearable
          >
            <el-option
              v-for="opt in schema.enum"
              :key="opt"
              :label="opt"
              :value="opt"
            />
          </el-select>
          <el-switch v-else-if="schema.type === 'boolean'" v-model="formData[key]" />
          <el-date-picker
            v-else-if="isDateType(schema)"
            v-model="formData[key]"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            :placeholder="`请选择${key}`"
          />
          <el-input
            v-else
            v-model="formData[key]"
            :placeholder="`请输入${key}`"
            clearable
          />
        </el-form-item>
      </template>
      <slot name="extra" />
      <el-form-item>
        <slot name="footer">
          <el-button type="primary" @click="handleSubmit">提交</el-button>
          <el-button @click="handleReset">重置</el-button>
        </slot>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import type { SchemaProperty } from '@lrenyi/platform-headless';

const props = withDefaults(
  defineProps<{
    /** 表单 schema（create 或 update） */
    schema: Record<string, SchemaProperty> | undefined;
    /** 初始值 */
    initial?: Record<string, unknown>;
    /** 只读字段（不渲染） */
    readonlyFields?: string[];
  }>(),
  { readonlyFields: () => [] }
);

const emit = defineEmits<{
  (e: 'submit', data: Record<string, unknown>): void;
}>();

const formRef = ref<FormInstance>();
const formData = reactive<Record<string, unknown>>({});
const rules = reactive<FormRules>({});

const formFields = computed(() => props.schema ?? {});

watch(
  () => props.initial,
  (v) => {
    if (v) Object.assign(formData, v);
  },
  { immediate: true }
);

watch(
  formFields,
  (s) => {
    for (const key of Object.keys(s)) {
      if (!(key in formData)) {
        formData[key] = s[key].type === 'boolean' ? false : '';
      }
      const prop = s[key];
      if (prop && !rules[key]) {
        rules[key] = [{ required: true, message: `请输入${key}`, trigger: 'blur' }];
      }
    }
  },
  { immediate: true }
);

const inferInputType = (s: SchemaProperty): string => {
  if (s.format === 'email') return 'email';
  if (s.description?.toLowerCase().includes('password')) return 'password';
  if (s.description?.toLowerCase().includes('描述') || s.description?.toLowerCase().includes('备注'))
    return 'textarea';
  return 'text';
};

const isDateType = (s: SchemaProperty): boolean => {
  const t = (s.type ?? '').toLowerCase();
  const f = (s.format ?? '').toLowerCase();
  return (
    t.includes('date') ||
    t.includes('time') ||
    f.includes('date') ||
    f.includes('time')
  );
};

const handleSubmit = async () => {
  await formRef.value?.validate();
  emit('submit', { ...formData });
};

const handleReset = () => {
  formRef.value?.resetFields();
};
</script>
