/**
 * 认证组合式 API：管理登录状态、验证码、登录/登出
 */

import {ref, type Ref} from 'vue';
import {usePlatform} from '../createPlatform.js';
import type {AuthUser, LoginRequest} from '../../core';

export interface UseAuthReturn {
    user: Ref<AuthUser | null>;
    loading: Ref<boolean>;
    captchaKey: Ref<string>;
    captchaImage: Ref<string>;
    fetchCaptcha: () => Promise<void>;
    login: (req: LoginRequest) => Promise<AuthUser>;
    logout: () => Promise<void>;
    refreshMe: () => Promise<AuthUser | null>;
}

const user = ref<AuthUser | null>(null);
const loading = ref(false);
const captchaKey = ref('');
const captchaImage = ref('');

export function useAuth(): UseAuthReturn {
    const {authClient} = usePlatform();

    if (!authClient) {
        throw new Error('Platform 未初始化或未配置 auth，请先调用 createPlatform({ auth: {...} })');
    }

    async function fetchCaptcha(): Promise<void> {
        const result = await authClient!.getCaptcha();
        captchaKey.value = result.key;
        captchaImage.value = result.imageBase64;
    }

    async function login(req: LoginRequest): Promise<AuthUser> {
        loading.value = true;
        try {
            await authClient!.login(req);
            const u = await refreshMe();
            return u ?? ({} as AuthUser);
        } finally {
            loading.value = false;
        }
    }

    async function logout(): Promise<void> {
        loading.value = true;
        try {
            await authClient!.logout();
            user.value = null;
            captchaKey.value = '';
            captchaImage.value = '';
        } finally {
            loading.value = false;
        }
    }

    async function refreshMe(): Promise<AuthUser | null> {
        try {
            const u = await authClient!.me();
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
        refreshMe,
    };
}
