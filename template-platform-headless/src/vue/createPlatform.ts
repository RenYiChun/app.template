/**
 * 创建 platform 实例，供应用入口调用
 */

import { AuthClient } from '../core/authClient.js';
import { EntityClient } from '../core/index.js';
import { MetaService } from '../core/index.js';
import type { AuthClientConfig } from '../core/authClient.js';
import type { EntityClientConfig } from '../core/index.js';
import type { MetaServiceConfig } from '../core/index.js';
import { inject, type App, type InjectionKey } from 'vue';

export interface PlatformOptions {
  client?: EntityClientConfig;
  meta?: MetaServiceConfig;
  auth?: AuthClientConfig;
  allowGlobalFallback?: boolean;
}

export interface PlatformInstance {
  client: EntityClient;
  meta: MetaService;
  authClient: AuthClient | null;
  install(app: App): void;
}

export const PlatformSymbol: InjectionKey<PlatformInstance> = Symbol('Platform');

let defaultClient: EntityClient | null = null;
let defaultMeta: MetaService | null = null;
let defaultAuthClient: AuthClient | null = null;
let allowGlobalFallback = true;

export function createPlatform(options: PlatformOptions = {}): PlatformInstance {
  allowGlobalFallback = options.allowGlobalFallback ?? true;
  const clientConfig = options.client ?? {};
  const baseRequest = clientConfig.request ?? ((url: string, opts?: RequestInit) =>
    fetch(url, { ...opts, credentials: (opts?.credentials as RequestCredentials) ?? 'include' }));
  
  const authClient = new AuthClient({
    baseURL: clientConfig.baseURL,
    apiPrefix: clientConfig.apiPrefix,
    request: baseRequest,
    onUnauthorized: options.auth?.onUnauthorized,
    oauth2ClientId: options.auth?.oauth2ClientId,
    oauth2ClientSecret: options.auth?.oauth2ClientSecret,
    ...options.auth,
  });
  
  const requestWithToken = (url: string, opts: RequestInit = {}) => {
    const token = authClient.getToken();
    const headers = new Headers(opts.headers);
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }
    return baseRequest(url, { ...opts, headers });
  };
  
  const client = new EntityClient({
    ...clientConfig,
    platformId: clientConfig.platformId,
    request: requestWithToken,
  });
  
  const meta = new MetaService(client, options.meta);

  // Set global singletons for backward compatibility
  defaultClient = client;
  defaultMeta = meta;
  defaultAuthClient = authClient;

  const instance: PlatformInstance = { 
    client, 
    meta, 
    authClient,
    install(app: App) {
      app.provide(PlatformSymbol, this);
    }
  };

  return instance;
}

export function usePlatform() {
  const platform = inject(PlatformSymbol);
  if (platform) {
    return platform;
  }
  if (!allowGlobalFallback) {
    throw new Error('Platform 未注入，请先在应用入口 app.use(createPlatform(...))');
  }
  return getPlatform();
}

export function getPlatform() {
  if (!defaultClient || !defaultMeta) {
    throw new Error('Platform 未初始化，请先在应用入口调用 createPlatform()');
  }
  return {
    client: defaultClient,
    meta: defaultMeta,
    authClient: defaultAuthClient ?? null,
  };
}
