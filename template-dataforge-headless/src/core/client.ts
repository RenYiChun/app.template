/**
 * 通用 EntityClient：按 pathSegment 调用 template-dataforge REST API
 */

import type {PagedResult, Result, SearchRequest} from './types.js';
import {SUCCESS_CODE} from './types.js';
import {ensureSlash, joinPath} from './utils.js';
import {AuthError, BusinessError, HttpError, NetworkError} from './errors.js';

export interface EntityClientConfig {
    /** API 基础 URL，如 https://example.com */
    baseURL?: string;
    /** API 前缀，默认空 */
    apiPrefix?: string;
    /** 自定义请求函数，用于注入 axios/fetch 或添加认证头 */
    request?: (url: string, options: RequestInit) => Promise<Response>;
    /** 平台 ID，用于多租户/多平台支持 */
    dataforgeId?: string | number;
}


export class EntityClient {
    private readonly baseURL: string;
    private readonly apiPrefix: string;
    private readonly dataforgeId?: string | number;
    private readonly requestFn: (url: string, options: RequestInit) => Promise<Response>;

    constructor(config: EntityClientConfig = {}) {
        this.baseURL = (config.baseURL ?? '').replace(/\/$/, '');
        this.apiPrefix = ensureSlash(config.apiPrefix ?? '') || '';
        this.dataforgeId = config.dataforgeId;
        const baseRequest = config.request ?? fetch.bind(globalThis);
        this.requestFn = (url, opts) =>
            baseRequest(url, {...opts, credentials: (opts?.credentials as RequestCredentials) ?? 'include'});
    }

    /**
     * 发起请求（使用内部配置的 requestFn，会自动处理 baseURL 和 credentials）
     * @param url 完整 URL 或 path
     * @param options 请求选项
     */
    async request(url: string, options: RequestInit = {}): Promise<Response> {
        // 如果是相对路径且不以 http 开头，自动拼接 baseURL
        const fullUrl = url.startsWith('http') ? url : this.url(url.replace(/^\//, ''));
        try {
            const response = await this.requestFn(fullUrl, options);

            if (!response.ok) {
                if (response.status === 401 || response.status === 403) {
                    throw new AuthError(response);
                }
                throw new HttpError(response);
            }

            return response;
        } catch (error: any) {
            if (error instanceof NetworkError || error instanceof HttpError || error instanceof BusinessError || error instanceof AuthError) {
                throw error;
            }
            // 如果是 fetch 抛出的原生错误（如网络问题），包装为 NetworkError
            if (error.name === 'TypeError' || error.message.includes('network') || error.message.includes('failed to fetch')) {
                throw new NetworkError(error.message);
            }
            throw error;
        }
    }

    /** 获取 OpenAPI 文档 URL，供 MetaService 使用 */
    public getDocsUrl(): string {
        //这里不拼接前缀，统一在url方法中拼接
        return joinPath(this.baseURL, '', 'docs');
    }

    /** 分页搜索 */
    async search<T = Record<string, unknown>>(
        entity: string,
        req: SearchRequest = {}
    ): Promise<PagedResult<T>> {
        const body: SearchRequest = {
            filters: req.filters ?? [],
            sort: req.sort ?? [],
            page: req.page ?? 0,
            size: req.size ?? 20,
        };
        const res = await this.requestFn(this.url(`${entity}/search`), {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(body),
        });
        const data = await this.handleResult<PagedResult<T>>(res);
        return data ?? {content: [], totalElements: 0, totalPages: 0, number: 0, size: 20};
    }

    /** 根据 ID 查询 */
    async get<T = Record<string, unknown>>(
        entity: string,
        id: string | number
    ): Promise<T | null> {
        const res = await this.requestFn(this.url(`${entity}/${id}`), {method: 'GET'});
        return await this.handleResult<T | null>(res);
    }

    /** 创建 */
    async create<T = Record<string, unknown>, R = T>(
        entity: string,
        body: Partial<T>
    ): Promise<R> {
        const res = await this.requestFn(this.url(entity), {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(body),
        });
        const data = await this.handleResult<R>(res);
        if (data == null) throw new Error('创建成功但无返回数据');
        return data;
    }

    /** 更新 */
    async update<T = Record<string, unknown>, R = T>(
        entity: string,
        id: string | number,
        body: Partial<T>
    ): Promise<R> {
        const res = await this.requestFn(this.url(`${entity}/${id}`), {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(body),
        });
        const data = await this.handleResult<R>(res);
        if (data == null) throw new Error('更新成功但无返回数据');
        return data;
    }

    /** 删除 */
    async delete(entity: string, id: string | number): Promise<void> {
        const res = await this.requestFn(this.url(`${entity}/${id}`), {method: 'DELETE'});
        await this.handleResult<null>(res);
    }

    /** 删除 */
    async deleteBatch(entity: string, ids: (string | number)[]): Promise<void> {
        if (!ids.length) return;
        const res = await this.requestFn(this.url(`${entity}/batch`), {
            method: 'DELETE',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(ids),
        });
        await this.handleResult<null>(res);
    }

    /** 更新 */
    async updateBatch<T = Record<string, unknown>>(
        entity: string,
        items: Array<{ id: string | number } & Partial<T>>
    ): Promise<T[]> {
        if (!items.length) return [];
        const res = await this.requestFn(this.url(`${entity}/batch`), {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(items),
        });
        const data = await this.handleResult<T[]>(res);
        return data ?? [];
    }

    /** 导出 Excel */
    async export(entity: string, req: SearchRequest = {}): Promise<Blob> {
        const body: SearchRequest = {
            filters: req.filters ?? [],
            sort: req.sort ?? [],
            page: req.page ?? 0,
            size: req.size ?? 10000,
        };
        const res = await this.requestFn(this.url(`${entity}/export`), {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(body),
        });
        if (!res.ok) {
            const err = await res.text();
            throw new Error(err || `导出失败: ${res.status}`);
        }
        return res.blob();
    }

    /** 执行实体 Action */
    async executeAction<T = any>(
        entityName: string,
        id: string | number | null | undefined,
        actionName: string,
        body?: any,
        method: 'GET' | 'POST' | 'PUT' | 'DELETE' = 'POST',
    ): Promise<T> {
        const path = id
            ? joinPath(entityName, String(id), '_action', actionName)
            : joinPath(entityName, '_action', actionName);
        const res = await this.requestFn(this.url(path), {
            method,
            headers: body ? {'Content-Type': 'application/json'} : undefined,
            body: body ? JSON.stringify(body) : undefined,
        });
        return this.handleResult<T>(res);
    }

    /**
     * 定义一个强类型的实体操作对象，简化后续调用
     * @param name 实体名称（URL path segment）
     */
    define<T = Record<string, unknown>>(name: string) {
        return {
            search: (req?: SearchRequest) => this.search<T>(name, req),
            get: (id: string | number) => this.get<T>(name, id),
            create: (body: Partial<T>) => this.create<T>(name, body),
            update: (id: string | number, body: Partial<T>) => this.update<T>(name, id, body),
            delete: (id: string | number) => this.delete(name, id),
            deleteBatch: (ids: (string | number)[]) => this.deleteBatch(name, ids),
            updateBatch: (items: Array<{ id: string | number } & Partial<T>>) => this.updateBatch<T>(name, items),
        };
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
        const result = (await this.json<Result<T>>(res)) as Result<T>;
        // 检查业务状态码
        if (result.code !== SUCCESS_CODE && result.code !== 200) {
            throw new BusinessError(result.code, result.message ?? `请求失败: ${res.status}`, result.data);
        }
        return result.data as T;
    }
}
