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

/** 实体名 -> 刷新列表函数的注册表，供 CRUD 页挂载时注册、创建/更新成功后统一刷新 */
const crudRefreshRegistry = new Map<string, () => void>();

export interface DataforgeInstance {
    client: EntityClient;
    meta: MetaService;
    authClient: AuthClient | null;

    /** 注册某实体的列表刷新函数（由 EntityCrudPage 挂载时调用） */
    registerCrudRefresh(entityName: string, refresh: () => void): void;
    /** 注销某实体的列表刷新函数（由 EntityCrudPage 卸载时调用） */
    unregisterCrudRefresh(entityName: string): void;
    /** 刷新某实体的列表（创建/更新/删除成功后由业务调用，无需 ref） */
    refreshCrud(entityName: string): void;

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
        fetch(url, {...opts, credentials: (opts?.credentials as RequestCredentials) ?? 'include'}));

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
        const res = await baseRequest(url, {...opts, headers});

        // 如果返回 401，尝试刷新 token 并重试
        if (res.status === 401 && authClient.getRefreshToken()) {
            try {
                const newToken = await authClient.refresh();
                headers.set('Authorization', `Bearer ${newToken}`);
                return await baseRequest(url, {...opts, headers});
            } catch (e) {
                // 刷新失败，通知认证层并返回原始 401 响应
                console.warn('Token refresh failed:', e);
                options.auth?.onUnauthorized?.();
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

    const registerCrudRefresh = (entityName: string, refresh: () => void) => {
        crudRefreshRegistry.set(entityName, refresh);
    };
    const unregisterCrudRefresh = (entityName: string) => {
        crudRefreshRegistry.delete(entityName);
    };
    const refreshCrud = (entityName: string) => {
        crudRefreshRegistry.get(entityName)?.();
    };

    const instance: DataforgeInstance = {
        client,
        meta,
        authClient,
        registerCrudRefresh,
        unregisterCrudRefresh,
        refreshCrud,
        install(app: App) {
            app.provide(DataforgeSymbol, this);
        },
    };
    return instance;
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

export function getDataforge(): DataforgeInstance {
    if (!defaultClient || !defaultMeta) {
        throw new Error('Dataforge 未初始化，请先在应用入口调用 createDataforge()');
    }
    return {
        client: defaultClient,
        meta: defaultMeta,
        authClient: defaultAuthClient ?? null,
        registerCrudRefresh: (entityName: string, refresh: () => void) => crudRefreshRegistry.set(entityName, refresh),
        unregisterCrudRefresh: (entityName: string) => crudRefreshRegistry.delete(entityName),
        refreshCrud: (entityName: string) => crudRefreshRegistry.get(entityName)?.(),
        install() {
            // getDataforge() 返回的实例通常不用于 install
        },
    };
}
