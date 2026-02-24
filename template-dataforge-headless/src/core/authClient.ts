/**
 * 认证客户端：OAuth2 Password Grant 获取 JWT，Bearer Token 访问接口
 */

import type { Result, StorageProvider } from './types.js';
import { SUCCESS_CODE } from './types.js';
import { joinPath, ensureSlash } from './utils.js';
import { NetworkError, HttpError, BusinessError, AuthError } from './errors.js';

export interface AuthClientConfig {
  baseURL?: string;
  apiPrefix?: string;
  request?: (url: string, options: RequestInit) => Promise<Response>;
  onUnauthorized?: () => void;
  /** OAuth2 客户端 ID */
  oauth2ClientId?: string;
  /** OAuth2 客户端密钥 */
  oauth2ClientSecret?: string;
  /** 存储接口（默认为 localStorage） */
  storage?: StorageProvider;
}

export interface LoginRequest {
  username: string;
  password: string;
  captchaKey?: string;
  captchaCode?: string;
}

export interface CaptchaResult {
  key: string;
  imageBase64: string;
}

export interface AuthUser {
  id: number;
  username: string;
  email: string;
}


export class AuthClient {
  private readonly baseURL: string;
  private readonly apiPrefix: string;
  private readonly oauth2BaseURL: string;
  private readonly tokenURL: string;
  private readonly requestFn: (url: string, options: RequestInit) => Promise<Response>;
  private readonly onUnauthorized?: () => void;
  private readonly clientId: string;
  private readonly clientSecret: string;
  private readonly storage?: StorageProvider;
  private token: string | null = null;
  private refreshToken: string | null = null;

  constructor(config: AuthClientConfig = {}) {
    this.baseURL = (config.baseURL ?? '').replace(/\/$/, '');
    this.apiPrefix = ensureSlash(config.apiPrefix ?? '') || '';
    this.oauth2BaseURL = this.baseURL;
    this.tokenURL = joinPath(this.oauth2BaseURL, 'oauth2/token');
    this.clientId = config.oauth2ClientId ?? 'dataforge-client';
    this.clientSecret = config.oauth2ClientSecret ?? 'dataforge-secret';
    const baseRequest = config.request ?? fetch.bind(globalThis);
    this.requestFn = (url, opts = {}) =>
      baseRequest(url, { ...opts, credentials: (opts.credentials as RequestCredentials) ?? 'include' });
    this.onUnauthorized = config.onUnauthorized;

    // 优先使用传入的 storage，否则尝试使用 localStorage
    if (config.storage) {
      this.storage = config.storage;
    } else if (typeof window !== 'undefined' && window.localStorage) {
      this.storage = window.localStorage;
    }

    // 从存储加载 token
    this.token = this.storage?.getItem('auth_token') ?? null;
    this.refreshToken = this.storage?.getItem('refresh_token') ?? null;
  }

  /** 获取当前 token，供请求拦截器使用 */
  getToken(): string | null {
    return this.token;
  }

  /** 获取 refresh token */
  getRefreshToken(): string | null {
    return this.refreshToken;
  }

  /** 清除 token */
  clearToken(): void {
    this.token = null;
    this.refreshToken = null;
    this.storage?.removeItem('auth_token');
    this.storage?.removeItem('refresh_token');
  }

  private authHeaders(): Record<string, string> {
    return this.token ? { Authorization: `Bearer ${this.token}` } : {};
  }

  private url(path: string): string {
    return joinPath(this.baseURL, this.apiPrefix, path);
  }

  private async json<T>(res: Response): Promise<T> {
    const text = await res.text();
    if (!text) return {} as T;
    return JSON.parse(text) as T;
  }

  private async handleResult<T>(res: Response): Promise<T> {
    if (!res.ok) {
        if (res.status === 401 || res.status === 403) {
            this.clearToken();
            this.onUnauthorized?.();
            throw new AuthError(res);
        }
        throw new HttpError(res);
    }
    
    const result = (await this.json<Result<T>>(res)) as Result<T>;
    if (result.code !== SUCCESS_CODE && result.code !== 200) {
      throw new BusinessError(result.code, result.message ?? `请求失败: ${res.status}`, result.data);
    }
    return result.data as T;
  }

  /** 获取验证码 */
  async getCaptcha(): Promise<CaptchaResult> {
    const res = await this.requestFn(this.url('auth/_action/captcha'), { method: 'GET' });
    const data = await this.handleResult<CaptchaResult>(res);
    if (!data?.key || !data?.imageBase64) {
      throw new Error('验证码接口返回格式异常');
    }
    return data;
  }

  /** 登录：OAuth2 Password Grant */
  async login(req: LoginRequest): Promise<AuthUser> {
    const params = new URLSearchParams();
    params.set('grant_type', 'authorization_password');
    params.set('username', req.username);
    params.set('password', req.password);
    params.set('client_id', this.clientId);
    params.set('client_secret', this.clientSecret);
    if (req.captchaKey && req.captchaCode) {
      params.set('captchaKey', req.captchaKey);
      params.set('captchaCode', req.captchaCode);
    }

    const res = await this.requestFn(this.tokenURL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: params.toString(),
    });

    if (!res.ok) {
      const text = await res.text();
      // OAuth2 登录失败通常返回 400 或 401，这里统一包装为 AuthError
      if (res.status === 401 || res.status === 400) {
        throw new AuthError(res, `登录失败: ${text}`);
      }
      throw new HttpError(res, `登录失败: ${res.status} ${text}`);
    }

    const tokenData = (await this.json<{
      access_token?: string;
      refresh_token?: string;
      username?: string;
      sub?: string;
    }>(res)) as Record<string, unknown>;

    const accessToken = tokenData.access_token as string;
    if (!accessToken) {
      throw new BusinessError(-1, '登录成功但未返回 access_token', tokenData);
    }

    this.token = accessToken;
    this.storage?.setItem('auth_token', accessToken);

    const refreshToken = tokenData.refresh_token as string;
    if (refreshToken) {
      this.refreshToken = refreshToken;
      this.storage?.setItem('refresh_token', refreshToken);
    }

    const username = (tokenData.username ?? tokenData.sub ?? req.username) as string;
    return {
      id: 0,
      username: username || req.username,
      email: '',
    };
  }

  /** 刷新 token */
  async refresh(): Promise<string> {
    if (!this.refreshToken) {
      throw new Error('No refresh token available');
    }

    const params = new URLSearchParams();
    params.set('grant_type', 'refresh_token');
    params.set('refresh_token', this.refreshToken);
    params.set('client_id', this.clientId);
    params.set('client_secret', this.clientSecret);

    const res = await this.requestFn(this.tokenURL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: params.toString(),
    });

    if (!res.ok) {
      this.clearToken();
      throw new AuthError(res, 'Token refresh failed');
    }

    const tokenData = (await this.json<{
      access_token?: string;
      refresh_token?: string;
    }>(res)) as Record<string, unknown>;

    const accessToken = tokenData.access_token as string;
    if (!accessToken) {
      throw new Error('Token refresh response missing access_token');
    }

    this.token = accessToken;
    this.storage?.setItem('auth_token', accessToken);

    if (tokenData.refresh_token) {
      this.refreshToken = tokenData.refresh_token as string;
      this.storage?.setItem('refresh_token', this.refreshToken);
    }

    return accessToken;
  }

  /** 登出（清除本地 token） */
  async logout(): Promise<void> {
    this.clearToken();
    try {
      await this.requestFn(this.url('auth/_action/logout'), {
        method: 'POST',
        headers: this.authHeaders(),
      });
    } catch {
    }
  }

  /** 获取当前用户 */
  async me(): Promise<AuthUser | null> {
    const res = await this.requestFn(this.url('auth/_action/me'), {
      method: 'GET',
      headers: this.authHeaders(),
    });
    const data = await this.handleResult<AuthUser | null>(res);
    return data ?? null;
  }
}
