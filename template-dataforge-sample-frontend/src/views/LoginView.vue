<template>
  <div class="login-container">
    <div class="login-card">
      <!-- Left: Brand Area -->
      <div class="brand-section">
        <div class="brand-content">
          <div class="logo-area">
            <svg class="brand-logo" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M2 17L12 22L22 17" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M2 12L12 17L22 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            <span class="brand-name">Dataforge</span>
          </div>
          <div class="brand-slogan">
            <h1>Create.<br>Evole.<br>Succed.</h1>
            <p>Empowering your business with intelligent dataforge solutions.</p>
          </div>
          <div class="brand-footer">
            © {{ new Date().getFullYear() }} LRenYi Template
          </div>
        </div>
        <!-- Decorative Shapes -->
        <div class="shape shape-1"></div>
        <div class="shape shape-2"></div>
        <div class="shape-glass"></div>
      </div>

      <!-- Right: Login Form -->
      <div class="login-section">
        <div class="login-wrapper">
          <div class="login-header">
            <h2>{{ $t('login.title') }}</h2>
            <p>{{ $t('login.welcome') }}</p>
          </div>

          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            class="login-form"
            size="large"
            @submit.prevent="handleLogin"
          >
            <el-form-item prop="username">
              <el-input
                v-model="form.username"
                :placeholder="$t('login.username')"
                class="modern-input"
                :prefix-icon="UserIcon"
                tabindex="1"
              />
            </el-form-item>

            <el-form-item prop="password">
              <el-input
                v-model="form.password"
                type="password"
                :placeholder="$t('login.password')"
                show-password
                class="modern-input"
                :prefix-icon="LockIcon"
                @keyup.enter="handleLogin"
                tabindex="2"
              />
            </el-form-item>

            <el-form-item v-if="captchaImage" prop="captchaCode">
              <div class="captcha-container">
                <el-input
                  v-model="form.captchaCode"
                  :placeholder="$t('login.captcha')"
                  class="modern-input captcha-input"
                  :prefix-icon="KeyIcon"
                  @keyup.enter="handleLogin"
                  tabindex="3"
                />
                <div class="captcha-image-box" @click="fetchCaptcha" title="Click to refresh">
                   <img :src="captchaImage" alt="Captcha" />
                </div>
              </div>
            </el-form-item>

            <div class="form-actions">
              <el-button
                type="primary"
                :loading="loading"
                class="login-btn"
                @click="handleLogin"
                tabindex="4"
              >
                {{ $t('login.loginBtn') }}
              </el-button>
            </div>
            
            <div class="form-footer-links">
                <a href="#" class="forgot-password">{{ $t('login.forgotPwd') }}</a>
            </div>
          </el-form>
        </div>
        <div v-if="errorMsg" class="error-banner">
          <span>{{ errorMsg }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, shallowRef, computed } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { storeToRefs } from 'pinia';
import { ElMessage } from 'element-plus';
import { User, Lock, Key } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';

// Manually mapping icons
const UserIcon = shallowRef(User);
const LockIcon = shallowRef(Lock);
const KeyIcon = shallowRef(Key);

const { t } = useI18n();
const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();
const { captchaKey, captchaImage, loading } = storeToRefs(authStore);
const { login, fetchCaptcha } = authStore;

const formRef = ref();
const errorMsg = ref('');

const form = reactive({
  username: '',
  password: '',
  captchaCode: '',
});

const rules = computed(() => ({
  username: [{ required: true, message: t('login.username') + ' is required', trigger: 'blur' }],
  password: [{ required: true, message: t('login.password') + ' is required', trigger: 'blur' }],
  captchaCode: [{ required: false, message: t('login.captcha') + ' is required', trigger: 'blur' }],
}));

const handleLogin = async () => {
  if (!formRef.value) return;
  await formRef.value.validate(async (valid: boolean) => {
    if (valid) {
      errorMsg.value = '';
      try {
        await login({
          username: form.username,
          password: form.password,
          captchaKey: captchaKey.value || undefined,
          captchaCode: form.captchaCode || undefined,
        });
        
        ElMessage.success(t('login.success'));
        const redirect = (route.query.redirect as string) || '/';
        router.push(redirect);
      } catch (err: any) {
        // console.error(err);
        errorMsg.value = err.message || t('login.failed');
        fetchCaptcha();
      }
    }
  });
};

onMounted(() => {
  fetchCaptcha();
});
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap');

.login-container {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  font-family: 'Inter', sans-serif;
  overflow: hidden;
}

.login-card {
  display: flex;
  width: 960px;
  height: 600px;
  background: #ffffff;
  border-radius: 20px;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
  overflow: hidden;
  position: relative;
}

/* Brand Section (Left) */
.brand-section {
  flex: 1;
  background: radial-gradient(circle at top right, #39366e 0%, #1e1b4b 100%);
  color: white;
  display: flex;
  flex-direction: column;
  justify-content: center;
  position: relative;
  overflow: hidden;
  padding: 60px;
  min-width: 400px;
}

.brand-content {
  position: relative;
  z-index: 10;
}

.logo-area {
  display: flex;
  align-items: center;
  margin-bottom: 40px;
  gap: 12px;
}

.brand-logo {
  width: 36px;
  height: 36px;
  color: #a5b4fc;
}

.brand-name {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.brand-slogan h1 {
  font-size: 52px;
  line-height: 1.1;
  font-weight: 800;
  margin-bottom: 24px;
  color: #ffffff;
}

.brand-slogan p {
  font-size: 16px;
  color: #c7d2fe;
  line-height: 1.6;
  max-width: 360px;
}

.brand-footer {
  position: absolute;
  bottom: 40px;
  left: 60px;
  color: #6366f1;
  font-size: 12px;
  opacity: 0.8;
}

/* Shapes & Effects */
.shape {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.6;
}
.shape-1 {
  width: 300px;
  height: 300px;
  background: #4338ca;
  top: -80px;
  right: -80px;
}
.shape-2 {
  width: 250px;
  height: 250px;
  background: #db2777;
  bottom: -60px;
  left: -60px;
  opacity: 0.4;
}
.shape-glass {
    position: absolute;
    bottom: 0;
    right: 0;
    width: 100%;
    height: 100%;
    background: linear-gradient(135deg, rgba(255,255,255,0.05) 0%, rgba(255,255,255,0) 100%);
    pointer-events: none;
}

/* Login Section (Right) */
.login-section {
  width: 480px;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 50px;
  position: relative;
}

.login-wrapper {
  width: 100%;
  max-width: 340px;
}

.login-header {
  margin-bottom: 32px;
}

.login-header h2 {
  font-size: 28px;
  font-weight: 700;
  color: #111827;
  margin-bottom: 8px;
}

.login-header p {
  color: #6b7280;
  font-size: 14px;
}

.login-form .el-form-item {
  margin-bottom: 20px;
}

/* Modern Input overrides */
:deep(.modern-input .el-input__wrapper) {
  box-shadow: none !important;
  background-color: #f3f4f6;
  border: 1px solid transparent;
  border-radius: 8px;
  transition: all 0.2s ease;
  padding: 4px 12px;
}

:deep(.modern-input .el-input__wrapper:hover),
:deep(.modern-input .el-input__wrapper.is-focus) {
  background-color: #ffffff;
  border-color: #e5e7eb;
  box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.1) !important;
}

:deep(.modern-input .el-input__inner) {
  height: 40px;
  color: #1f2937;
  font-weight: 500;
}

:deep(.el-input__prefix-inner) {
    color: #9ca3af;
}

/* Captcha */
.captcha-container {
  display: flex;
  gap: 12px;
  width: 100%;
}

.captcha-input {
  flex: 1;
}

.captcha-image-box {
  width: 110px;
  height: 50px;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  border: 1px solid #e5e7eb;
  background: #f9fafb;
  display: flex;
  align-items: center;
  justify-content: center;
}

.captcha-image-box:hover {
    border-color: #d1d5db;
}

.captcha-image-box img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}

/* Buttons */
.form-actions {
  margin-top: 24px;
}

.login-btn {
  width: 100%;
  height: 48px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 8px;
  background: #4f46e5;
  border: none;
  transition: all 0.2s;
  box-shadow: 0 4px 6px -1px rgba(79, 70, 229, 0.4);
}

.login-btn:hover {
  background: #4338ca;
  transform: translateY(-1px);
  box-shadow: 0 6px 8px -1px rgba(79, 70, 229, 0.5);
}

.login-btn:active {
  transform: translateY(0);
}

.form-footer-links {
    margin-top: 16px;
    text-align: center;
}

.forgot-password {
    color: #6366f1;
    text-decoration: none;
    font-size: 14px;
    font-weight: 500;
}
.forgot-password:hover {
    text-decoration: underline;
}

.error-banner {
  margin-top: 20px;
  padding: 10px 14px;
  background-color: #fee2e2;
  border-radius: 6px;
  color: #dc2626;
  font-size: 13px;
  text-align: center;
  border: 1px solid #fecaca;
}

/* Responsive */
@media (max-width: 1000px) {
  .login-card {
    width: 90%;
    height: auto;
    flex-direction: column;
  }
  .brand-section {
    display: none; /* Hide brand on smaller screens or tablets */
  }
  .login-section {
    width: 100%;
    padding: 40px 20px;
  }
}
</style>
