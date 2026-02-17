<template>
  <div class="login-page">
    <div class="login-card">
      <h2 class="login-title">{{ title }}</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" @submit.prevent="handleSubmit">
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="用户名"
            size="large"
            :disabled="loading"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            show-password
            size="large"
            :disabled="loading"
            @keyup.enter="handleSubmit"
          />
        </el-form-item>
        <el-form-item v-if="captchaImage" prop="captchaCode">
          <div class="captcha-row">
            <el-input
              v-model="form.captchaCode"
              placeholder="验证码"
              size="large"
              :disabled="loading"
              @keyup.enter="handleSubmit"
            />
            <img
              :src="captchaImage"
              class="captcha-img"
              alt="验证码"
              @click="doFetchCaptcha"
            />
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" :loading="loading" class="submit-btn" @click="handleSubmit">
            登录
          </el-button>
        </el-form-item>
      </el-form>
      <p v-if="errorMsg" class="error-msg">{{ errorMsg }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { useAuth } from '../composables/useAuth.js';
import type { AuthUser } from '../../core/authClient.js';

const props = withDefaults(
  defineProps<{
    title?: string;
    redirectPath?: string;
    /** 登录成功回调，接收用户与重定向路径。若使用 vue-router，可在此调用 router.push(path) */
    onSuccess?: (user: AuthUser, redirectPath: string) => void;
  }>(),
  {
    title: '登录',
    redirectPath: '/',
  }
);

const auth = useAuth();
const { loading, captchaKey, captchaImage, fetchCaptcha, login } = auth;

const formRef = ref<FormInstance>();
const errorMsg = ref('');

const form = reactive({
  username: '',
  password: '',
  captchaCode: '',
});

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
};

async function doFetchCaptcha() {
  try {
    await fetchCaptcha();
  } catch {
    ElMessage.error('获取验证码失败');
  }
}

async function handleSubmit() {
  if (!formRef.value) return;
  await formRef.value.validate((valid) => {
    if (!valid) return;
  }).catch(() => {});
  errorMsg.value = '';
  try {
    const redirect = props.redirectPath;
    const u = await login({
      username: form.username.trim(),
      password: form.password,
      captchaKey: captchaKey.value || undefined,
      captchaCode: form.captchaCode.trim() || undefined,
    });
    ElMessage.success('登录成功');
    props.onSuccess?.(u, redirect);
  } catch (e) {
    errorMsg.value = (e as Error).message || '登录失败';
    doFetchCaptcha();
  }
}

onMounted(() => {
  doFetchCaptcha();
});
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card {
  width: 380px;
  padding: 40px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.15);
}
.login-title {
  margin: 0 0 32px;
  font-size: 24px;
  font-weight: 600;
  text-align: center;
  color: #333;
}
.captcha-row {
  display: flex;
  gap: 12px;
  width: 100%;
}
.captcha-row .el-input {
  flex: 1;
}
.captcha-img {
  width: 120px;
  height: 40px;
  cursor: pointer;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}
.submit-btn {
  width: 100%;
}
.error-msg {
  margin: 0;
  font-size: 12px;
  color: #f56c6c;
  text-align: center;
}
</style>
