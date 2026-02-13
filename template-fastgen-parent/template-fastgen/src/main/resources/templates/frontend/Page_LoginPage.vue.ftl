<!-- 登录页专用模板：Generated from @Page ${page.simpleName}. 左品牌装饰区 + 右表单，层次更丰富 -->
<template>
  <div class="page-login-enhanced login-page-wrap">
    <div class="login-panel">
      <!-- 左侧：品牌与装饰区 -->
      <div class="login-left">
        <div class="login-left-inner">
          <div class="login-brand">
            <div class="login-brand-icon">Fastgen</div>
            <p class="login-brand-desc">欢迎回来，请登录您的账号</p>
          </div>
          <div class="login-deco">
            <span class="login-deco-circle login-deco-circle-1"></span>
            <span class="login-deco-circle login-deco-circle-2"></span>
            <span class="login-deco-circle login-deco-circle-3"></span>
          </div>
        </div>
      </div>
      <!-- 右侧：表单区 -->
      <div class="login-right">
        <div class="login-card">
          <div class="login-card-accent"></div>
          <h1 class="login-title">${page.title}</h1>
          <p class="login-subtitle">请输入账号与验证码完成登录</p>
          <el-form ref="formRef" :model="formData" :rules="rules" label-position="top" class="login-form">
<#list page.fields as field>
<#if field.formType == "hidden">
            <!-- ${field.name} 由脚本设置，不渲染表单项 -->
<#elseif field.formType == "captcha">
            <el-form-item label="${field.label!field.name}" prop="${field.name}">
              <div class="captcha-row">
                <img v-if="captchaImage" :src="captchaImage" class="captcha-img" alt="验证码" @click="refreshCaptcha" />
                <el-button v-if="captchaImage" type="primary" link @click="refreshCaptcha" aria-label="刷新验证码">刷新</el-button>
                <el-input v-model="formData.${field.name}" placeholder="请输入验证码" maxlength="6" clearable class="captcha-input" size="large" />
              </div>
            </el-form-item>
<#elseif field.formType == "password">
            <el-form-item label="${field.label!field.name}" prop="${field.name}">
              <el-input v-model="formData.${field.name}" type="password" show-password placeholder="请输入${field.label!field.name}" size="large">
                <template #prefix><el-icon><Lock /></el-icon></template>
              </el-input>
            </el-form-item>
<#else>
            <el-form-item label="${field.label!field.name}" prop="${field.name}">
              <el-input v-model="formData.${field.name}" placeholder="请输入${field.label!field.name}" size="large">
                <template #prefix><el-icon><User /></el-icon></template>
              </el-input>
            </el-form-item>
</#if>
</#list>
            <el-form-item class="login-actions">
              <el-button type="primary" size="large" :loading="submitting" @click="handleSubmit" class="submit-btn">登录</el-button>
              <el-button size="large" @click="handleReset">重置</el-button>
            </el-form-item>
          </el-form>
          <div class="login-footer">
            <el-link type="info" :underline="false" href="#">忘记密码？</el-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { User, Lock } from '@element-plus/icons-vue';
<#if page.successPath?? && (page.successPath!)?has_content>
import { useRouter } from 'vue-router';
</#if>
<#if page.apiPath?? && (page.apiPath!)?has_content>
import request from '@/utils/request';
</#if>
<#if page.successPath?? && (page.successPath!)?has_content>
const router = useRouter();
</#if>

const formRef = ref<FormInstance>();
const captchaImage = ref('');
const submitting = ref(false);
const fetchCaptcha = async () => {
  try {
    const res = await request.get<{ key: string; imageBase64: string }>('auth/captcha');
    formData.captchaKey = res.key;
    captchaImage.value = res.imageBase64;
    formData.captchaCode = '';
  } catch {
    ElMessage.error('验证码加载失败');
  }
};
const refreshCaptcha = () => { fetchCaptcha(); };
onMounted(() => { fetchCaptcha(); });

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
  submitting.value = true;
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
      fetchCaptcha();
      formData.captchaCode = '';
    }
  } finally {
    submitting.value = false;
  }
};

const handleReset = () => {
  formRef.value?.resetFields();
  fetchCaptcha();
};
</script>

<style scoped>
.login-page-wrap {
  min-height: 100vh;
  background: #f0f2f5;
  background-image: var(--login-bg-image, none);
  background-size: cover;
  background-position: center;
}
.login-panel {
  display: flex;
  min-height: 100vh;
  width: 100%;
}
.login-left {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  position: relative;
  overflow: hidden;
}
.login-left-inner {
  position: relative;
  z-index: 1;
  max-width: 360px;
}
.login-brand {
  text-align: left;
  margin-bottom: 32px;
}
.login-brand-icon {
  font-size: 2rem;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.02em;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}
.login-brand-desc {
  margin: 12px 0 0;
  font-size: 1rem;
  color: rgba(255, 255, 255, 0.9);
  line-height: 1.5;
}
.login-deco {
  position: relative;
  height: 200px;
  margin-top: 24px;
}
.login-deco-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
}
.login-deco-circle-1 {
  width: 120px;
  height: 120px;
  top: 0;
  left: 0;
}
.login-deco-circle-2 {
  width: 80px;
  height: 80px;
  top: 60px;
  left: 100px;
}
.login-deco-circle-3 {
  width: 160px;
  height: 160px;
  bottom: 0;
  right: 0;
  background: rgba(255, 255, 255, 0.08);
}
.login-right {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
  background: linear-gradient(180deg, #fafbfc 0%, #f0f2f5 100%);
  box-shadow: -8px 0 24px rgba(0, 0, 0, 0.06);
  position: relative;
}
.login-right::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image: radial-gradient(circle at 20% 80%, rgba(102, 126, 234, 0.04) 0%, transparent 50%);
  pointer-events: none;
}
.login-card {
  width: 100%;
  max-width: 380px;
  padding: 40px 32px;
  position: relative;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);
}
.login-card-accent {
  position: absolute;
  left: 0;
  top: 24px;
  bottom: 24px;
  width: 4px;
  border-radius: 2px;
  background: linear-gradient(180deg, #667eea 0%, #764ba2 100%);
}
.login-title {
  margin: 0 0 6px;
  font-size: 1.5rem;
  font-weight: 600;
  text-align: center;
  color: #303133;
}
.login-subtitle {
  margin: 0 0 24px;
  font-size: 0.875rem;
  color: #909399;
  text-align: center;
}
.login-form {
  margin-top: 0;
}
.login-form :deep(.el-form-item) {
  margin-bottom: 20px;
}
.login-form :deep(.el-form-item__label) {
  font-weight: 500;
  color: #606266;
}
.login-form :deep(.el-input__wrapper) {
  border-radius: 8px;
  box-shadow: 0 0 0 1px #dcdfe6 inset;
}
.login-form :deep(.el-input__wrapper:hover),
.login-form :deep(.el-input.is-focus .el-input__wrapper) {
  box-shadow: 0 0 0 1px #667eea inset;
}
.login-footer {
  margin-top: 20px;
  text-align: center;
}
.login-actions {
  margin-top: 28px;
  margin-bottom: 0;
}
.login-actions .el-form-item__content {
  display: flex;
  gap: 12px;
  justify-content: center;
}
.submit-btn {
  min-width: 120px;
}
.captcha-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.captcha-img {
  height: 40px;
  border-radius: 8px;
  cursor: pointer;
  border: 1px solid #dcdfe6;
  vertical-align: middle;
}
.captcha-input {
  flex: 1;
  min-width: 120px;
}
@media (max-width: 768px) {
  .login-panel {
    flex-direction: column;
  }
  .login-left {
    padding: 32px 24px;
    min-height: 200px;
  }
  .login-deco {
    height: 100px;
  }
  .login-right {
    padding: 32px 24px;
    box-shadow: none;
  }
}
</style>
