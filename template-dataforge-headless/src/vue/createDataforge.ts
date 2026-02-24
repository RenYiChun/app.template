/**
 * 创建 dataforge 实例，供应用入口调用
 */

import type {AuthClientConfig, EntityClientConfig, MetaServiceConfig} from '../core';
import {AuthClient, EntityClient, MetaService} from '../core';
import {type App, inject, type InjectionKey} from 'vue';

export interface DataforgeOptions {
  client?: EntityClientConfig;
  meta?: MetaServiceConfig;
  auth?: AuthClientConfig;
  allowGlobalFallback?: boolean;
}

export interface DataforgeInstance {
  client: EntityClient;
  meta: MetaService;
  authClient: AuthClient | null;
  install(app: App): void;
}

export const DataforgeSymbol: InjectionKey<DataforgeInstance> = Symbol('Dataforge');

let defaultClient: EntityClient | null = null;
let defaultMeta: MetaService | null = null;
let defaultAuthClient: AuthClient | null = null;
let allowGlobalFallback = true;

export function createDataforge(options: DataforgeOptions = {}): DataforgeInstance {
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
  
  const requestWithToken = async (url: string, opts: RequestInit = {}) => {
    const token = authClient.getToken();
    const headers = new Headers(opts.headers);
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }
    const res = await baseRequest(url, { ...opts, headers });
    
    // 如果返回 401，尝试刷新 token 并重试
    if (res.status === 401 && authClient.getRefreshToken()) {
      try {
        const newToken = await authClient.refresh();
        headers.set('Authorization', `Bearer ${newToken}`);
        return await baseRequest(url, { ...opts, headers });
      } catch (e) {
        // 刷新失败，返回原始 401 响应，由后续逻辑触发登出
        return res;
      }
    }
    return res;
  };
  
  const client = new EntityClient({
    ...clientConfig,
    dataforgeId: clientConfig.dataforgeId,
    request: requestWithToken,
  });
  
  const meta = new MetaService(client, options.meta);

  // Set global singletons for backward compatibility
  defaultClient = client;
  defaultMeta = meta;
  defaultAuthClient = authClient;

  return {
    client,
    meta,
    authClient,
    install(app: App) {
      app.provide(DataforgeSymbol, this);
    }
  };
}

export function useDataforge() {
  const dataforge = inject(DataforgeSymbol);
  if (dataforge) {
    return dataforge;
  }
  if (!allowGlobalFallback) {
    throw new Error('Dataforge 未注入，请先在应用入口 app.use(createDataforge(...))');
  }
  return getDataforge();
}

export function getDataforge() {
  if (!defaultClient || !defaultMeta) {
    throw new Error('Dataforge 未初始化，请先在应用入口调用 createDataforge()');
  }
  return {
    client: defaultClient,
    meta: defaultMeta,
    authClient: defaultAuthClient ?? null,
  };
}
