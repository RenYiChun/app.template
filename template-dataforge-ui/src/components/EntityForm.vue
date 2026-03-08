<template>
  <div class="entity-form">
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      class="elegant-form"
      :label-position="labelPosition"
      :label-width="labelWidth"
      :validate-on-rule-change="false"
    >
      <div class="form-grid">
        <template v-if="hasGroups">
          <div v-for="(fields, groupName) in groupedFields" :key="groupName" class="form-group">
            <div v-if="groupName !== 'default'" class="group-title">{{ groupName }}</div>
            <div class="form-group-fields">
            <template v-for="([key, schema]) in fields" :key="key">
              <div
                v-if="!readonlyFields.includes(key)"
                class="form-field-container"
                :class="getFieldClass(key, schema)"
                :style="getFieldStyle(schema)"
              >
                <el-form-item
                  :prop="key"
                  :required="schema.required"
                  class="custom-form-item"
                >
                  <template #label>
                    <div class="field-label-content">
                      {{ getFieldLabel(key, schema) }}
                      <el-tooltip
                        v-if="schema.description && schema.description !== getFieldLabel(key, schema)"
                        :content="schema.description"
                        placement="top"
                      >
                        <el-icon class="help-icon"><InfoFilled /></el-icon>
                      </el-tooltip>
                    </div>
                  </template>

                  <div class="field-input-wrapper">
                    <slot v-if="$slots[`field-${key}`]" :field="key" :model="formData" :schema="schema" :name="`field-${key}`" :readonly="readonly"/>

                    <!-- Textarea -->
                    <el-input
                      v-else-if="inferInputType(schema) === 'textarea'"
                      v-model="formData[key]"
                      :placeholder="formatText(inputPlaceholder, { label: getFieldLabel(key, schema) })"
                      :rows="3"
                      :readonly="readonly"
                      type="textarea"
                      class="elegant-input"
                    />

                    <!-- Password -->
                    <el-input
                      v-else-if="inferInputType(schema) === 'password'"
                      v-model="formData[key]"
                      :placeholder="formatText(inputPlaceholder, { label: getFieldLabel(key, schema) })"
                      :readonly="readonly"
                      show-password
                      type="password"
                      class="elegant-input"
                      autocomplete="new-password"
                    />

                    <!-- Select/Enum -->
                    <el-select
                      v-else-if="schema.enum"
                      v-model="formData[key]"
                      :placeholder="formatText(selectPlaceholder, { label: getFieldLabel(key, schema) })"
                      :disabled="readonly"
                      clearable
                      class="elegant-select"
                    >
                      <el-option
                        v-for="opt in schema.enum"
                        :key="opt"
                        :label="opt"
                        :value="opt"
                      />
                    </el-select>

                    <!-- Boolean/Switch -->
                    <div v-else-if="schema.type === 'boolean'" class="switch-container">
                      <el-switch
                        v-model="formData[key]"
                        :active-text="$t('common.enable') || '启用'"
                        :inactive-text="$t('common.disable') || '禁用'"
                        :disabled="readonly"
                        inline-prompt
                        class="modern-switch"
                      />
                    </div>

                    <!-- DateTime -->
                    <el-date-picker
                      v-else-if="isDateType(schema)"
                      v-model="formData[key]"
                      :placeholder="formatText(selectPlaceholder, { label: getFieldLabel(key, schema) })"
                      :disabled="readonly"
                      type="datetime"
                      value-format="YYYY-MM-DD HH:mm:ss"
                      class="elegant-date-picker"
                    />

                     <!-- Number -->
                    <el-input-number
                      v-else-if="schema.type === 'integer' || schema.type === 'number'"
                      v-model="formData[key]"
                      :placeholder="formatText(inputPlaceholder, { label: getFieldLabel(key, schema) })"
                      :disabled="readonly"
                      class="elegant-input-number"
                      controls-position="right"
                    />

                    <!-- Default Input -->
                    <el-input
                      v-else
                      v-model="formData[key]"
                      :placeholder="formatText(inputPlaceholder, { label: getFieldLabel(key, schema) })"
                      :readonly="readonly"
                      clearable
                      class="elegant-input"
                    />
                  </div>
                  <template #error="{ error }">
                    <span v-if="error" class="custom-error-msg">{{ error }}</span>
                  </template>
                </el-form-item>
              </div>
            </template>
            </div>
          </div>
        </template>
        <template v-else>
          <template v-for="(schema, key) in sortedFields" :key="key">
            <div
              v-if="!readonlyFields.includes(key)"
              class="form-field-container"
              :class="getFieldClass(key, schema)"
              :style="getFieldStyle(schema)"
            >
              <el-form-item
                :prop="key"
                :required="schema.required"
                class="custom-form-item"
              >
                <template #label>
                  <div class="field-label-content">
                    {{ getFieldLabel(key, schema) }}
                    <el-tooltip
                      v-if="schema.description && schema.description !== getFieldLabel(key, schema)"
                      :content="schema.description"
                      placement="top"
                    >
                      <el-icon class="help-icon"><InfoFilled /></el-icon>
                    </el-tooltip>
                  </div>
                </template>

                <div class="field-input-wrapper">
                  <slot v-if="$slots[`field-${key}`]" :field="key" :model="formData" :schema="schema" :name="`field-${key}`" :readonly="readonly"/>

                  <!-- Textarea -->
                  <el-input
                    v-else-if="inferInputType(schema) === 'textarea'"
                    v-model="formData[key]"
                    :placeholder="formatText(inputPlaceholder, { label: getFieldLabel(key, schema) })"
                    :rows="3"
                    :readonly="readonly"
                    type="textarea"
                    class="elegant-input"
                  />

                  <!-- Password -->
                  <el-input
                    v-else-if="inferInputType(schema) === 'password'"
                    v-model="formData[key]"
                    :placeholder="formatText(inputPlaceholder, { label: getFieldLabel(key, schema) })"
                    :readonly="readonly"
                    show-password
                    type="password"
                    class="elegant-input"
                    autocomplete="new-password"
                  />

                  <!-- Select/Enum -->
                  <el-select
                    v-else-if="schema.enum"
                    v-model="formData[key]"
                    :placeholder="formatText(selectPlaceholder, { label: getFieldLabel(key, schema) })"
                    :disabled="readonly"
                    clearable
                    class="elegant-select"
                  >
                    <el-option
                      v-for="opt in schema.enum"
                      :key="opt"
                      :label="opt"
                      :value="opt"
                    />
                  </el-select>

                  <!-- Boolean/Switch -->
                  <div v-else-if="schema.type === 'boolean'" class="switch-container">
                    <el-switch
                      v-model="formData[key]"
                      :active-text="$t('common.enable') || '启用'"
                      :inactive-text="$t('common.disable') || '禁用'"
                      :disabled="readonly"
                      inline-prompt
                      class="modern-switch"
                    />
                  </div>

                  <!-- DateTime -->
                  <el-date-picker
                    v-else-if="isDateType(schema)"
                    v-model="formData[key]"
                    :placeholder="formatText(selectPlaceholder, { label: getFieldLabel(key, schema) })"
                    :disabled="readonly"
                    type="datetime"
                    value-format="YYYY-MM-DD HH:mm:ss"
                    class="elegant-date-picker"
                  />

                   <!-- Number -->
                  <el-input-number
                    v-else-if="schema.type === 'integer' || schema.type === 'number'"
                    v-model="formData[key]"
                    :placeholder="formatText(inputPlaceholder, { label: getFieldLabel(key, schema) })"
                    :disabled="readonly"
                    class="elegant-input-number"
                    controls-position="right"
                  />

                  <!-- Default Input -->
                  <el-input
                    v-else
                    v-model="formData[key]"
                    :placeholder="formatText(inputPlaceholder, { label: getFieldLabel(key, schema) })"
                    :readonly="readonly"
                    clearable
                    class="elegant-input"
                  />
                </div>
                <template #error="{ error }">
                  <span v-if="error" class="custom-error-msg">{{ error }}</span>
                </template>
              </el-form-item>
            </div>
          </template>
        </template>
      </div>

      <slot name="extra"/>

      <div class="form-actions">
        <slot name="footer">
          <el-button class="action-btn action-btn-cancel" @click="handleReset">{{ resetText }}</el-button>
          <el-button :loading="loading" type="primary" class="action-btn action-btn-submit" @click="handleSubmit">
            <el-icon class="btn-icon"><Check /></el-icon>
            {{ submitText }}
          </el-button>
        </slot>
      </div>
    </el-form>
  </div>
</template>

<script lang="ts" setup>
import {computed, reactive, ref, watch} from 'vue';
import {Check, InfoFilled} from '@element-plus/icons-vue';
import type {FormInstance, FormRules} from 'element-plus';
import type {SchemaProperty} from '@lrenyi/dataforge-headless';

const props = withDefaults(
    defineProps<{
      schema: Record<string, SchemaProperty> | undefined;
      initial?: Record<string, unknown>;
      readonlyFields?: string[];
      /** 只读模式：所有控件不可编辑，用于详情查看 */
      readonly?: boolean;
      rulesOverride?: FormRules;
      rulesMode?: 'merge' | 'replace';
      loading?: boolean;
      labelPosition?: 'left' | 'right' | 'top';
      labelWidth?: string | number;
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
    {
      readonlyFields: () => [],
      readonly: false,
      rulesMode: 'merge',
      loading: false,
      labelPosition: 'right', // 默认右对齐（标签在左）
      labelWidth: '100px'     // 默认标签宽度
    }
);

const emit = defineEmits<(e: 'submit', data: Record<string, unknown>) => void>();

const formRef = ref<FormInstance>();
const formData = reactive<Record<string, unknown>>({});
const rules = reactive<FormRules>({});

const formFields = computed(() => props.schema ?? {});

const sortedFields = computed(() => {
  const fields = formFields.value;
  const entries = Object.entries(fields);

  // 排序逻辑：先按order属性，然后按是否必填，最后按字段名
  entries.sort(([keyA, schemaA], [keyB, schemaB]) => {
    const orderA = (schemaA as any).order ?? Number.MAX_SAFE_INTEGER;
    const orderB = (schemaB as any).order ?? Number.MAX_SAFE_INTEGER;

    if (orderA !== orderB) {
      return orderA - orderB;
    }

    // 必填字段优先
    if (schemaA.required && !schemaB.required) return -1;
    if (!schemaA.required && schemaB.required) return 1;

    // 按字段名排序
    return keyA.localeCompare(keyB);
  });

  return Object.fromEntries(entries);
});

const groupedFields = computed(() => {
  const fields = sortedFields.value;
  const groups: Record<string, Array<[string, SchemaProperty]>> = {};

  // 按分组组织字段
  Object.entries(fields).forEach(([key, schema]) => {
    const groupName = (schema as any).group || 'default';
    if (!groups[groupName]) {
      groups[groupName] = [];
    }
    groups[groupName].push([key, schema]);
  });

  return groups;
});

const hasGroups = computed(() => {
  const fields = formFields.value;
  return Object.values(fields).some(schema => !!(schema as any).group);
});

const formatText = (template: string, vars?: Record<string, string | number>) => {
  if (!vars) return template;
  return template.replaceAll(/\{(\w+)\}/g, (_, key) => String(vars[key] ?? ''));
};

const inputPlaceholder = computed(() => props.locale?.form?.inputPlaceholder ?? '请输入{label}');
const selectPlaceholder = computed(() => props.locale?.form?.selectPlaceholder ?? '请选择{label}');
const requiredText = computed(() => props.locale?.form?.required ?? '{label}是必填项');
const emailText = computed(() => props.locale?.form?.email ?? '请输入正确的{label}');
const submitText = computed(() => props.locale?.common?.submit ?? '提交');
const resetText = computed(() => props.locale?.common?.reset ?? '重置');

watch(
    () => props.initial,
    (v) => {
      if (v) Object.assign(formData, v);
    },
    {immediate: true, deep: true}
);

const getFieldLabel = (key: string, schema: SchemaProperty) => {
  return schema.title || schema.description || key;
};

const getFieldClass = (key: string, schema: SchemaProperty) => {
  const type = inferInputType(schema);
  return {
    'field-full': type === 'textarea' || (schema as any).colSpan === 2,
    'field-required': schema.required
  };
};

const getFieldStyle = (schema: SchemaProperty) => {
  if ((schema as any).colSpan) {
    return { gridColumn: `span ${(schema as any).colSpan}` };
  }
  return {};
};

const generateFieldRules = (key: string, prop: SchemaProperty): any[] => {
  const rs: any[] = [];
  const trigger = prop.enum || prop.type === 'boolean' || isDateType(prop) ? 'change' : 'blur';
  const label = getFieldLabel(key, prop);

  if (prop.required) {
    rs.push({required: true, message: formatText(requiredText.value, {label}), trigger});
  }

  const fmt = (prop.format ?? '').toLowerCase();
  if (fmt === 'email') {
    rs.push({type: 'email', message: formatText(emailText.value, {label}), trigger: 'blur'});
  }

  if ((prop as any).minLength) {
     rs.push({min: (prop as any).minLength, message: `长度不能小于 ${(prop as any).minLength}`, trigger: 'blur'});
  }

  if ((prop as any).maxLength) {
     rs.push({max: (prop as any).maxLength, message: `长度不能大于 ${(prop as any).maxLength}`, trigger: 'blur'});
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
      formData[key] = prop.type === 'boolean' ? false : undefined;
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
  if (s.description?.toLowerCase().includes('password') || s.format === 'password') return 'password';
  if (s.description?.toLowerCase().includes('描述') || s.description?.toLowerCase().includes('备注') || s.format === 'textarea')
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
  if (!formRef.value) return;
  await formRef.value.validate((valid, fields) => {
    if (valid) {
      emit('submit', {...formData});
    }
  });
};

const handleReset = () => {
  formRef.value?.resetFields();
};

defineExpose({
  handleSubmit,
  handleReset
});
</script>

<style scoped>
.entity-form {
  padding: 4px 0;
}

.elegant-form {
  /* 统一颜色系统 */
  --color-primary: #409eff;
  --color-primary-light: #79bbff;
  --color-primary-lighter: #a0cfff;
  --color-primary-extra-light: #d9ecff;
  --color-success: #67c23a;
  --color-warning: #e6a23c;
  --color-danger: #f56c6c;
  --color-info: #909399;

  /* 中性色 */
  --color-text-primary: #303133;
  --color-text-regular: #606266;
  --color-text-secondary: #909399;
  --color-text-placeholder: #c0c4cc;

  --color-border-base: #dcdfe6;
  --color-border-light: #e4e7ed;
  --color-border-lighter: #ebeef5;
  --color-border-extra-light: #f2f6fc;

  --color-bg: #ffffff;
  --color-bg-page: #f5f7fa;
  --color-bg-light: #fafafa;

  /* 统一间距系统 (基于8px) */
  --spacing-xs: 4px;
  --spacing-sm: 8px;
  --spacing-md: 16px;
  --spacing-lg: 24px;
  --spacing-xl: 32px;

  /* 表单特定变量 */
  --form-gap: var(--spacing-md);
  --label-color: var(--color-text-regular);
  --label-font-size: 13px;
  --input-bg: var(--color-bg);
  --input-border: var(--color-border-base);
  --input-hover-border: var(--color-border-light);
  --input-focus-bg: var(--color-bg);
  --transition: all 0.2s cubic-bezier(0.645, 0.045, 0.355, 1);

  /* 圆角系统 */
  --border-radius-sm: 4px;
  --border-radius-base: 6px;
  --border-radius-lg: 8px;
  --border-radius-round: 20px;

  /* 输入框特定 */
  --input-height: 36px;
  --input-padding: 0 12px;
  --input-font-size: 14px;
  --input-line-height: 1.5;
}

/* 紧凑网格布局，充分利用横向空间 */
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 24px;
  row-gap: 4px;
  align-items: start;
}

/* 分组时：每组占满一行，组内再两列 */
.form-grid .form-group {
  grid-column: 1 / -1;
  margin-bottom: 8px;
}

.form-grid .form-group:last-of-type {
  margin-bottom: 0;
}

.form-group-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 24px;
  row-gap: 4px;
  align-items: start;
}

.group-title {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-secondary);
  letter-spacing: 0.02em;
  margin-bottom: 8px;
  padding-bottom: 4px;
  border-bottom: 1px solid var(--color-border-lighter);
}

/* 表单字段容器：防止错误提示溢出到相邻列 */
.form-field-container {
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: visible;
}

.form-field-container.field-full {
  grid-column: 1 / -1;
}

/* 自定义 Form Item 样式 */
.custom-form-item {
  margin-bottom: 12px;
}

/* 有错误时预留空间，避免提示被下一行遮挡 */
.custom-form-item.is-error {
  margin-bottom: 12px;
}

/* 标签内容布局 */
.field-label-content {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--label-color);
  font-weight: 500; /* 稍微加粗，但不像之前那么重 */
}

.help-icon {
  font-size: 14px;
  color: #909399;
  cursor: help;
}

/* 输入框包装器 */
.field-input-wrapper {
  width: 100%;
}

/* 确保表单项内容区为列布局，错误提示紧贴输入框下方 */
:deep(.custom-form-item .el-form-item__content) {
  display: flex;
  flex-direction: column;
  align-items: stretch;
}

/* 优雅的输入框样式 */
:deep(.elegant-input .el-input__wrapper),
:deep(.elegant-select .el-input__wrapper),
:deep(.elegant-date-picker .el-input__wrapper),
:deep(.elegant-input-number .el-input__wrapper) {
  background-color: var(--color-bg-light);
  border-radius: 6px;
  box-shadow: 0 0 0 1px var(--color-border-lighter) inset;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  padding: 0 10px;
  height: 36px;
}

:deep(.elegant-input .el-input__inner),
:deep(.elegant-select .el-input__inner) {
  height: 36px;
  font-size: 13px;
  color: #1f2937;
  line-height: 36px;
}

:deep(.elegant-input .el-input__wrapper:hover),
:deep(.elegant-select .el-input__wrapper:hover),
:deep(.elegant-date-picker .el-input__wrapper:hover),
:deep(.elegant-input-number .el-input__wrapper:hover) {
  background-color: var(--color-bg);
  box-shadow: 0 0 0 1px var(--color-primary) inset;
}

:deep(.elegant-input .el-input__wrapper.is-focus),
:deep(.elegant-select .el-input__wrapper.is-focus),
:deep(.elegant-date-picker .el-input__wrapper.is-focus),
:deep(.elegant-input-number .el-input__wrapper.is-focus) {
  background-color: var(--color-bg);
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.15), 0 0 0 1px var(--color-primary) inset;
}

/* Textarea 样式 */
:deep(.elegant-input.el-textarea .el-textarea__inner) {
  background-color: #f9fafb;
  border-radius: 6px;
  padding: 8px 10px;
  min-height: 56px;
  resize: vertical;
  font-size: 13px;
  line-height: 1.5;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 0 0 1px #e5e7eb inset;
}

:deep(.elegant-input.el-textarea .el-textarea__inner:hover) {
  background-color: #ffffff;
}

:deep(.elegant-input.el-textarea .el-textarea__inner:focus) {
  background-color: #ffffff;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.15);
}

/* Switch 容器 */
.switch-container {
  display: flex;
  align-items: center;
  height: 32px;
}

/* 现代开关样式 */
:deep(.modern-switch .el-switch__core) {
  border-radius: 10px;
  background-color: #e5e7eb;
  height: 24px;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

:deep(.modern-switch .el-switch__core::after) {
  border-radius: 50%;
  background-color: #ffffff;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

:deep(.modern-switch.is-checked .el-switch__core) {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

:deep(.modern-switch .el-switch__label) {
  font-size: 13px;
  color: #6b7280;
  font-weight: 500;
}

/* 错误状态样式 */
:deep(.custom-form-item.is-error .elegant-input .el-input__wrapper),
:deep(.custom-form-item.is-error .elegant-select .el-input__wrapper),
:deep(.custom-form-item.is-error .elegant-date-picker .el-input__wrapper),
:deep(.custom-form-item.is-error .elegant-input-number .el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--color-danger) inset;
}

:deep(.custom-form-item.is-error .elegant-input .el-input__wrapper.is-focus),
:deep(.custom-form-item.is-error .elegant-select .el-input__wrapper.is-focus),
:deep(.custom-form-item.is-error .elegant-date-picker .el-input__wrapper.is-focus),
:deep(.custom-form-item.is-error .elegant-input-number .el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--color-danger) inset;
}

:deep(.custom-form-item.is-error .elegant-input.el-textarea .el-textarea__inner) {
  box-shadow: 0 0 0 1px #f56c6c inset;
}

:deep(.custom-form-item.is-error .elegant-input.el-textarea .el-textarea__inner:focus) {
  box-shadow: 0 0 0 1px #f56c6c inset;
}

:deep(.custom-form-item.is-error .field-label-content) {
  color: #f56c6c;
}

:deep(.custom-form-item .el-form-item__error) {
  color: #f56c6c;
  font-size: 11px;
  line-height: 1;
  padding-top: 1px;
  position: relative;
  animation: errorSlideIn 0.3s ease;
}

/* 自定义错误提示：紧贴输入框下方，避免跑偏 */
.custom-error-msg {
  display: block;
  width: 100%;
  color: #f56c6c;
  font-size: 11px;
  line-height: 1.2;
  padding-top: 2px;
  animation: errorSlideIn 0.3s ease;
}

@keyframes errorSlideIn {
  from {
    opacity: 0;
    transform: translateY(-5px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 底部操作区 */
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}

/* 优雅的按钮样式 */
.action-btn {
  padding: 8px 20px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  height: 36px;
  border: none;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-width: 100px;
}

.action-btn-cancel {
  background-color: #f3f4f6;
  color: #6b7280;
}

.action-btn-cancel:hover {
  background-color: #e5e7eb;
  color: #374151;
  transform: translateY(-1px);
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
}

.action-btn-submit {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #ffffff;
  box-shadow: 0 4px 6px -1px rgba(102, 126, 234, 0.3);
}

.action-btn-submit:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 12px -2px rgba(102, 126, 234, 0.4);
}

.action-btn-submit:active {
  transform: translateY(0);
}

.btn-icon {
  font-size: 16px;
}

/* 响应式 */
@media (max-width: 768px) {
  .form-grid {
    grid-template-columns: 1fr;
    column-gap: 0;
    row-gap: 0;
  }

  .form-group-fields {
    grid-template-columns: 1fr;
    column-gap: 0;
  }

  .group-title {
    font-size: 12px;
    margin-bottom: 10px;
    padding-bottom: 6px;
  }

  .custom-form-item {
    margin-bottom: 12px;
  }

  :deep(.elegant-input .el-input__wrapper),
  :deep(.elegant-select .el-input__wrapper),
  :deep(.elegant-date-picker .el-input__wrapper),
  :deep(.elegant-input-number .el-input__wrapper) {
    height: 34px;
  }

  :deep(.elegant-input .el-input__inner),
  :deep(.elegant-select .el-input__inner) {
    height: 34px;
    line-height: 34px;
    font-size: 13px;
  }

  :deep(.elegant-input.el-textarea .el-textarea__inner) {
    min-height: 48px;
    font-size: 13px;
  }

  .field-label-content {
    font-size: 13px;
  }

  .form-actions {
    flex-direction: column-reverse;
    gap: 10px;
    margin-top: 8px;
  }

  .action-btn {
    width: 100%;
    justify-content: center;
    padding: 8px 16px;
    height: 34px;
    font-size: 13px;
  }

  .btn-icon {
    font-size: 13px;
  }
}
</style>
