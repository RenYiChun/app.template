<!-- Generated from @Domain ${entity.simpleName}. -->
<template>
  <div class="${entity.simpleName?lower_case}-form">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ isEdit ? '编辑' : '新增' }}${entity.displayName}</span>
          <el-button @click="handleBack">返回</el-button>
        </div>
      </template>

      <el-form ref="formRef" :model="formData" :rules="rules" label-width="120px">
<#list entity.fields as field>
<#if field.editable && !field.id>
        <el-form-item label="${field.label!field.name}" prop="${field.name}">
<#if field.formType == "password">
          <el-input v-model="formData.${field.name}" type="password" show-password placeholder="请输入${field.label!field.name}" />
<#elseif field.formType == "email">
          <el-input v-model="formData.${field.name}" type="email" placeholder="请输入${field.label!field.name}" />
<#elseif field.formType == "checkbox">
          <el-checkbox v-model="formData.${field.name}">${field.label!field.name}</el-checkbox>
<#elseif field.formType == "date">
          <el-date-picker v-model="formData.${field.name}" type="date" placeholder="请选择${field.label!field.name}" />
<#elseif field.formType == "datetime">
          <el-date-picker v-model="formData.${field.name}" type="datetime" placeholder="请选择${field.label!field.name}" />
<#elseif field.formType == "textarea">
          <el-input v-model="formData.${field.name}" type="textarea" :rows="3" placeholder="请输入${field.label!field.name}" />
<#else>
          <el-input v-model="formData.${field.name}" placeholder="请输入${field.label!field.name}" />
</#if>
        </el-form-item>
</#if>
</#list>

        <el-form-item>
          <el-button type="primary" @click="handleSubmit">提交</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { get, create, update } from '@/api/${entity.simpleName?lower_case}';
import type { ${entity.simpleName} } from '@/types/${entity.simpleName?lower_case}';

const router = useRouter();
const route = useRoute();
const formRef = ref<FormInstance>();

const isEdit = ref(false);
const formData = reactive<Partial<${entity.simpleName}>>({
<#list entity.fields as field>
<#if field.editable && !field.id>
  ${field.name}: <#if field.type == "Boolean">false<#elseif field.type == "Long" || field.type == "Integer">0<#else>''</#if>,
</#if>
</#list>
});

const rules = reactive<FormRules>({
<#list entity.fields as field>
<#if field.required && !field.id>
  ${field.name}: [
    { required: true, message: '请输入${field.label!field.name}', trigger: 'blur' }
  ],
</#if>
</#list>
});

const loadData = async () => {
  const id = route.params.id as string;
  if (id) {
    isEdit.value = true;
    try {
      const res = await get(Number(id));
      Object.assign(formData, res.data);
    } catch (error) {
      ElMessage.error('加载数据失败');
    }
  }
};

const handleSubmit = async () => {
  if (!formRef.value) return;
  
  try {
    await formRef.value.validate();
    if (isEdit.value) {
      await update(formData.id!, formData);
      ElMessage.success('更新成功');
    } else {
      await create(formData);
      ElMessage.success('创建成功');
    }
    handleBack();
  } catch (error) {
    if (error !== false) {
      ElMessage.error(isEdit.value ? '更新失败' : '创建失败');
    }
  }
};

const handleReset = () => {
  formRef.value?.resetFields();
};

const handleBack = () => {
  router.back();
};

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.el-form {
  max-width: 600px;
}
</style>
