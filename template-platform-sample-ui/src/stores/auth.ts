import { defineStore } from 'pinia';
import { ref } from 'vue';
import { getPlatform } from '@lrenyi/platform-headless/vue';
import type { AuthUser, LoginRequest } from '@lrenyi/platform-headless';

export const useAuthStore = defineStore('auth', () => {
  const user = ref<AuthUser | null>(null);
  const loading = ref(false);
  const captchaKey = ref('');
  const captchaImage = ref('');

  // Access authClient lazily
  const getAuthClient = () => {
    const { authClient } = getPlatform();
    if (!authClient) throw new Error('AuthClient not initialized');
    return authClient;
  };

  async function fetchCaptcha() {
    const client = getAuthClient();
    const res = await client.getCaptcha();
    captchaKey.value = res.key;
    captchaImage.value = res.imageBase64;
  }

  async function login(req: LoginRequest) {
    loading.value = true;
    try {
      const client = getAuthClient();
      await client.login(req);
      const u = await client.me();
      user.value = u;
      return u;
    } finally {
      loading.value = false;
    }
  }

  async function logout() {
    loading.value = true;
    try {
      const client = getAuthClient();
      await client.logout();
    } finally {
      user.value = null;
      captchaKey.value = '';
      captchaImage.value = '';
      loading.value = false;
    }
  }

  async function refreshMe() {
    try {
      const client = getAuthClient();
      const u = await client.me();
      user.value = u;
      return u;
    } catch {
      user.value = null;
      return null;
    }
  }

  return {
    user,
    loading,
    captchaKey,
    captchaImage,
    fetchCaptcha,
    login,
    logout,
    refreshMe
  };
});
