<template>
  <div class="entity-form">
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="120px">
      <template v-for="(schema, key) in formFields" :key="key">
        <el-form-item v-if="!readonlyFields.includes(key)" :label="key" :prop="key">
          <slot v-if="$slots[`field-${key}`]" :field="key" :model="formData" :name="`field-${key}`"/>
          <el-input
              v-else-if="inferInputType(schema) === 'textarea'"
              v-model="formData[key]"
              :placeholder="formatText(inputPlaceholder, { label: key })"
              :rows="3"
              type="textarea"
          />
          <el-input
              v-else-if="inferInputType(schema) === 'password'"
              v-model="formData[key]"
              :placeholder="formatText(inputPlaceholder, { label: key })"
              show-password
              type="password"
          />
          <el-select
              v-else-if="schema.enum"
              v-model="formData[key]"
              :placeholder="formatText(selectPlaceholder, { label: key })"
              clearable
          >
            <el-option
                v-for="opt in schema.enum"
                :key="opt"
                :label="opt"
                :value="opt"
            />
          </el-select>
          <el-switch v-else-if="schema.type === 'boolean'" v-model="formData[key]"/>
          <el-date-picker
              v-else-if="isDateType(schema)"
              v-model="formData[key]"
              :placeholder="formatText(selectPlaceholder, { label: key })"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
          />
          <el-input
              v-else
              v-model="formData[key]"
              :placeholder="formatText(inputPlaceholder, { label: key })"
              clearable
          />
        </el-form-item>
      </template>
      <slot name="extra"/>
      <el-form-item>
        <slot name="footer">
          <el-button type="primary" @click="handleSubmit">{{ submitText }}</el-button>
          <el-button @click="handleReset">{{ resetText }}</el-button>
        </slot>
      </el-form-item>
    </el-form>
  </div>
</template>

<script lang="ts" setup>
import {computed, reactive, ref, watch} from 'vue';
import type {FormInstance, FormRules} from 'element-plus';
import type {SchemaProperty} from '@lrenyi/dataforge-headless';

const props = withDefaults(
    defineProps<{
      /** 表单 schema（create 或 update） */
      schema: Record<string, SchemaProperty> | undefined;
      /** 初始值 */
      initial?: Record<string, unknown>;
      /** 只读字段（不渲染） */
      readonlyFields?: string[];
      /** 额外/覆盖校验规则 */
      rulesOverride?: FormRules;
      /** 规则覆盖模式：merge=追加，replace=完全替换 */
      rulesMode?: 'merge' | 'replace';
      locale?: {
        common?: {
          submit?: string;
          reset?: string;
        };
        form?: {
          inputPlaceholder?: string;
          selectPlaceholder?: string;
          required?: string;
          email?: string;
        };
      };
    }>(),
    {readonlyFields: () => [], rulesMode: 'merge'}
);

const emit = defineEmits<{
  (e: 'submit', data: Record<string, unknown>): void;
}>();

const formRef = ref<FormInstance>();
const formData = reactive<Record<string, unknown>>({});
const rules = reactive<FormRules>({});

const formFields = computed(() => props.schema ?? {});

const formatText = (template: string, vars?: Record<string, string | number>) => {
  if (!vars) return template;
  return template.replace(/\{(\w+)\}/g, (_, key) => String(vars[key] ?? ''));
};

const inputPlaceholder = computed(() => props.locale?.form?.inputPlaceholder ?? '请输入{label}');
const selectPlaceholder = computed(() => props.locale?.form?.selectPlaceholder ?? '请选择{label}');
const requiredText = computed(() => props.locale?.form?.required ?? '请输入{label}');
const emailText = computed(() => props.locale?.form?.email ?? '请输入正确的{label}');
const submitText = computed(() => props.locale?.common?.submit ?? '提交');
const resetText = computed(() => props.locale?.common?.reset ?? '重置');

watch(
    () => props.initial,
    (v) => {
      if (v) Object.assign(formData, v);
    },
    {immediate: true}
);

const generateFieldRules = (key: string, prop: SchemaProperty): any[] => {
  const rs: any[] = [];
  const trigger = prop.enum || prop.type === 'boolean' || isDateType(prop) ? 'change' : 'blur';
  if (prop.required) {
    rs.push({required: true, message: formatText(requiredText.value, {label: key}), trigger});
  }
  const fmt = (prop.format ?? '').toLowerCase();
  if (fmt === 'email') {
    rs.push({type: 'email', message: formatText(emailText.value, {label: key}), trigger: 'blur'});
  }
  return rs;
};

const mergeRules = (key: string, generatedRules: any[]) => {
  const ov = props.rulesOverride?.[key] as any[] | undefined;
  
  if (props.rulesMode === 'replace') {
    if (ov) {
      rules[key] = ov;
    } else if (generatedRules.length) {
      rules[key] = generatedRules;
    }
    return;
  }
  
  // merge mode
  const newRules = [...(generatedRules || [])];
  if (ov?.length) {
    newRules.push(...ov);
  }
  if (newRules.length) {
    rules[key] = newRules;
  }
};

const applyOverrideRules = () => {
  if (!props.rulesOverride || props.rulesMode !== 'merge') return;
  for (const [k, v] of Object.entries(props.rulesOverride)) {
    if (!rules[k as any]) (rules as any)[k] = v as any;
  }
};

const updateRulesAndData = (s: Record<string, SchemaProperty>) => {
  for (const k of Object.keys(rules)) {
    delete (rules as any)[k];
  }
  for (const key of Object.keys(s)) {
    const prop = s[key];
    if (!prop) continue;

    if (!(key in formData)) {
      formData[key] = prop.type === 'boolean' ? false : '';
    }

    const generatedRules = generateFieldRules(key, prop);
    mergeRules(key, generatedRules);
  }
  applyOverrideRules();
};

watch(
    formFields,
    (s) => updateRulesAndData(s),
    {immediate: true}
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
  emit('submit', {...formData});
};

const handleReset = () => {
  formRef.value?.resetFields();
};
</script>
