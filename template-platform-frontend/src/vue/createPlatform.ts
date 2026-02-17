/**
 * 创建 platform 实例，供应用入口调用
 */

import { AuthClient } from '../core/authClient.js';
import { EntityClient } from '../core/index.js';
import { MetaService } from '../core/index.js';
import type { AuthClientConfig } from '../core/authClient.js';
import type { EntityClientConfig } from '../core/index.js';
import type { MetaServiceConfig } from '../core/index.js';

export interface PlatformOptions {
  client?: EntityClientConfig;
  meta?: MetaServiceConfig;
  auth?: AuthClientConfig;
}

let defaultClient: EntityClient | null = null;
let defaultMeta: MetaService | null = null;
let defaultAuthClient: AuthClient | null = null;

export function createPlatform(options: PlatformOptions = {}) {
  const clientConfig = options.client ?? {};
  const baseRequest = clientConfig.request ?? ((url: string, opts?: RequestInit) =>
    fetch(url, { ...opts, credentials: (opts?.credentials as RequestCredentials) ?? 'include' }));
  defaultAuthClient = new AuthClient({
    baseURL: clientConfig.baseURL,
    apiPrefix: clientConfig.apiPrefix,
    request: baseRequest,
    onUnauthorized: options.auth?.onUnauthorized,
    oauth2ClientId: options.auth?.oauth2ClientId,
    oauth2ClientSecret: options.auth?.oauth2ClientSecret,
    ...options.auth,
  });
  const requestWithToken = (url: string, opts: RequestInit = {}) => {
    const token = defaultAuthClient?.getToken();
    const headers = new Headers(opts.headers);
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }
    return baseRequest(url, { ...opts, headers });
  };
  defaultClient = new EntityClient({
    ...clientConfig,
    platformId: clientConfig.platformId,
    request: requestWithToken,
  });
  defaultMeta = new MetaService(defaultClient, options.meta);
  return { client: defaultClient, meta: defaultMeta, authClient: defaultAuthClient };
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
