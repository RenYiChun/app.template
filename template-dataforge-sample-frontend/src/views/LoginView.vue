<template>
  <div class="login-container">
    <div class="login-card">
      <!-- Left: Brand Area -->
      <div class="brand-section">
        <div class="brand-content">
          <div class="logo-area">
            <svg class="brand-logo" fill="none" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"
                    stroke-width="2"/>
              <path d="M2 17L12 22L22 17" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"
                    stroke-width="2"/>
              <path d="M2 12L12 17L22 12" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"
                    stroke-width="2"/>
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
                  :prefix-icon="UserIcon"
                  class="modern-input"
              />
            </el-form-item>

            <el-form-item prop="password">
              <el-input
                  v-model="form.password"
                  :placeholder="$t('login.password')"
                  :prefix-icon="LockIcon"
                  class="modern-input"
                  show-password
                  type="password"
                  @keyup.enter="handleLogin"
              />
            </el-form-item>

            <el-form-item v-if="captchaImage" prop="captchaCode">
              <div class="captcha-container">
                <el-input
                    v-model="form.captchaCode"
                    :placeholder="$t('login.captcha')"
                    :prefix-icon="KeyIcon"
                    class="modern-input captcha-input"
                    @keyup.enter="handleLogin"
                />
                <div class="captcha-image-box" title="Click to refresh" @click="fetchCaptcha">
                  <img :src="captchaImage" alt="Captcha"/>
                </div>
              </div>
            </el-form-item>

            <div class="form-actions">
              <el-button
                  :loading="loading"
                  class="login-btn"
                  type="primary"
                  @click="handleLogin"
              >
                {{ $t('login.loginBtn') }}
              </el-button>
            </div>

            <div class="form-footer-links">
              <a class="forgot-password" href="#">{{ $t('login.forgotPwd') }}</a>
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

<script lang="ts" setup>
import {computed, onMounted, reactive, ref, shallowRef} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import {useAuthStore} from '../stores/auth';
import {storeToRefs} from 'pinia';
import {ElMessage} from 'element-plus';
import {Key, Lock, User} from '@element-plus/icons-vue';
import {useI18n} from 'vue-i18n';

// Manually mapping icons
const UserIcon = shallowRef(User);
const LockIcon = shallowRef(Lock);
const KeyIcon = shallowRef(Key);

const {t} = useI18n();
const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();
const {captchaKey, captchaImage, loading} = storeToRefs(authStore);
const {login, fetchCaptcha} = authStore;

const formRef = ref();
const errorMsg = ref('');

const form = reactive({
  username: '',
  password: '',
  captchaCode: '',
});

const rules = computed(() => ({
  username: [{required: true, message: t('login.username') + ' is required', trigger: 'blur'}],
  password: [{required: true, message: t('login.password') + ' is required', trigger: 'blur'}],
  captchaCode: [{required: false, message: t('login.captcha') + ' is required', trigger: 'blur'}],
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
  color: #c7d2fe; /* Indigo 200 - 满足 WCAG AA 对比度（深色渐变背景） */
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
  color: #e0e7ff; /* Indigo 100 - 满足 WCAG AA 对比度（深色背景） */
  line-height: 1.6;
  max-width: 360px;
}

.brand-footer {
  position: absolute;
  bottom: 40px;
  left: 60px;
  color: #a5b4fc; /* Indigo 300 - 满足 WCAG AA 对比度（深色背景） */
  font-size: 12px;
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
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.05) 0%, rgba(255, 255, 255, 0) 100%);
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
  color: #4b5563; /* Gray 600 - 满足 WCAG AA 对比度 */
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
  color: #4b5563; /* Gray 600 - 满足 WCAG AA 对比度（灰底/白底） */
}

:deep(.modern-input .el-input__inner::placeholder) {
  color: #4b5563;
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
  background: #4338ca; /* Indigo 700 - 满足 WCAG AA 对比度（白字） */
  border: none;
  transition: all 0.2s;
  box-shadow: 0 4px 6px -1px rgba(67, 56, 202, 0.4);
}

.login-btn:hover {
  background: #3730a3; /* Indigo 800 - 满足 WCAG AA 对比度 */
  transform: translateY(-1px);
  box-shadow: 0 6px 8px -1px rgba(55, 48, 163, 0.5);
}

.login-btn:active {
  transform: translateY(0);
}

.form-footer-links {
  margin-top: 16px;
  text-align: center;
}

.forgot-password {
  color: #4338ca; /* Indigo 700 - 满足 WCAG AA 对比度 */
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
}

.forgot-password:hover {
  color: #3730a3;
  text-decoration: underline;
}

.error-banner {
  margin-top: 20px;
  padding: 10px 14px;
  background-color: #fee2e2;
  border-radius: 6px;
  color: #b91c1c; /* Red 700 - 满足 WCAG AA 对比度 */
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
