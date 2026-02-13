<!-- 通用单页模板：Generated from @Page ${page.simpleName}. 特殊页可单独建 Page_${page.simpleName}.vue.ftl -->
<template>
  <div class="page-${page.simpleName?lower_case}" :class="{ 'centered-layout': layout === 'centered' }">
    <el-card>
      <template #header>
        <h2>${page.title}</h2>
      </template>

      <el-form ref="formRef" :model="formData" :rules="rules" label-width="120px">
<#list page.fields as field>
        <el-form-item label="${field.label!field.name}" prop="${field.name}">
<#if field.formType == "password">
          <el-input v-model="formData.${field.name}" type="password" show-password placeholder="请输入${field.label!field.name}" />
<#elseif field.formType == "checkbox">
          <el-checkbox v-model="formData.${field.name}">${field.label!field.name}</el-checkbox>
<#elseif field.formType == "email">
          <el-input v-model="formData.${field.name}" type="email" placeholder="请输入${field.label!field.name}" />
<#elseif field.formType == "hidden">
          <!-- hidden 不展示 -->
<#else>
          <el-input v-model="formData.${field.name}" placeholder="请输入${field.label!field.name}" />
</#if>
        </el-form-item>
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
import { ref, reactive } from 'vue';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
<#if page.successPath?? && (page.successPath!)?has_content>
import { useRouter } from 'vue-router';
</#if>
<#if page.apiPath?? && (page.apiPath!)?has_content>
import request from '@/utils/request';
</#if>
<#if page.successPath?? && (page.successPath!)?has_content>
const router = useRouter();
</#if>

const layout = '${page.layout!"default"}';
const formRef = ref<FormInstance>();
<#if page.apiPath?? && (page.apiPath!)?has_content>
<#assign apiPathValue = page.apiPath?replace("/api/", "")?replace("/api", "")>
<#if apiPathValue?starts_with("/")><#assign apiPathValue = apiPathValue?substring(1)></#if>
const apiPath = '${apiPathValue}';
</#if>

const formData = reactive({
<#list page.fields as field>
  ${field.name}: <#if field.type == "Boolean">false<#elseif field.type == "Long" || field.type == "Integer">0<#else>''</#if>,
</#list>
});

const rules = reactive<FormRules>({
<#list page.fields as field>
<#if field.required>
  ${field.name}: [
    { required: true, message: '请输入${field.label!field.name}', trigger: 'blur' }
  ],
</#if>
</#list>
});

const handleSubmit = async () => {
  if (!formRef.value) return;
  try {
    await formRef.value.validate();
<#if page.apiPath?? && (page.apiPath!)?has_content>
    await request.post(apiPath, formData);
    ElMessage.success('提交成功');
    <#if page.successPath?? && (page.successPath!)?has_content>
    router.push('${page.successPath}');
    </#if>
<#else>
    console.log('提交数据:', formData);
    ElMessage.success('提交成功（未配置 apiPath，未请求后端）');
    <#if page.successPath?? && (page.successPath!)?has_content>
    router.push('${page.successPath}');
    </#if>
</#if>
  } catch (error: unknown) {
    if (error !== false) {
      const err = error as { response?: { data?: { message?: string } } };
      const msg = err?.response?.data?.message ?? '提交失败';
      ElMessage.error(msg);
    }
  }
};

const handleReset = () => {
  formRef.value?.resetFields();
};
</script>

<style scoped>
.page-${page.simpleName?lower_case} {
  padding: 20px;
}

.centered-layout {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background-color: #f5f5f5;
}

.centered-layout .el-card {
  width: 100%;
  max-width: 400px;
}

h2 {
  margin: 0;
  text-align: center;
}
</style>
