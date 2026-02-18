/**
 * 通用 EntityClient：按 pathSegment 调用 template-platform REST API
 */

import type { Result, SearchRequest, PagedResult } from './types.js';
import { SUCCESS_CODE } from './types.js';
import { joinPath, ensureSlash } from './utils.js';

export interface EntityClientConfig {
  /** API 基础 URL，如 https://example.com */
  baseURL?: string;
  /** API 前缀，默认 /api */
  apiPrefix?: string;
  /** 自定义请求函数，用于注入 axios/fetch 或添加认证头 */
  request?: (url: string, options: RequestInit) => Promise<Response>;
  /** 平台 ID，用于多租户/多平台支持 */
  platformId?: string | number;
}


export class EntityClient {
  private readonly baseURL: string;
  private readonly apiPrefix: string;
  private readonly platformId?: string | number;
  private readonly requestFn: (url: string, options: RequestInit) => Promise<Response>;

  constructor(config: EntityClientConfig = {}) {
    this.baseURL = (config.baseURL ?? '').replace(/\/$/, '');
    this.apiPrefix = ensureSlash(config.apiPrefix ?? '/api') || '';
    this.platformId = config.platformId;
    const baseRequest = config.request ?? fetch.bind(globalThis);
    this.requestFn = (url, opts) =>
      baseRequest(url, { ...opts, credentials: (opts?.credentials as RequestCredentials) ?? 'include' });
  }

  /**
   * 发起请求（使用内部配置的 requestFn，会自动处理 baseURL 和 credentials）
   * @param url 完整 URL 或 path
   * @param options 请求选项
   */
  async request(url: string, options: RequestInit = {}): Promise<Response> {
    // 如果是相对路径且不以 http 开头，自动拼接 baseURL
    const fullUrl = url.startsWith('http') ? url : this.url(url.replace(/^\//, ''));
    return this.requestFn(fullUrl, options);
  }

  /** 获取 OpenAPI 文档 URL，供 MetaService 使用 */
  getDocsUrl(): string {
    return joinPath(this.baseURL, this.apiPrefix, 'docs');
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
    if (result.code !== SUCCESS_CODE && result.code !== 200) {
      const err = new Error(result.message ?? `请求失败: ${res.status}`);
      (err as Error & { code?: number; response?: Response }).code = result.code;
      (err as Error & { code?: number; response?: Response }).response = res;
      throw err;
    }
    return result.data as T;
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
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    const data = await this.handleResult<PagedResult<T>>(res);
    return data ?? { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 };
  }

  /** 根据 ID 查询 */
  async get<T = Record<string, unknown>>(
    entity: string,
    id: string | number
  ): Promise<T | null> {
    const res = await this.requestFn(this.url(`${entity}/${id}`), { method: 'GET' });
    const data = await this.handleResult<T | null>(res);
    return data;
  }

  /** 创建 */
  async create<T = Record<string, unknown>>(
    entity: string,
    body: Record<string, unknown>
  ): Promise<T> {
    const res = await this.requestFn(this.url(entity), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    const data = await this.handleResult<T>(res);
    if (data == null) throw new Error('创建成功但无返回数据');
    return data;
  }

  /** 更新 */
  async update<T = Record<string, unknown>>(
    entity: string,
    id: string | number,
    body: Record<string, unknown>
  ): Promise<T> {
    const res = await this.requestFn(this.url(`${entity}/${id}`), {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    const data = await this.handleResult<T>(res);
    if (data == null) throw new Error('更新成功但无返回数据');
    return data;
  }

  /** 删除 */
  async delete(entity: string, id: string | number): Promise<void> {
    const res = await this.requestFn(this.url(`${entity}/${id}`), { method: 'DELETE' });
    await this.handleResult<null>(res);
  }

  /** 批量删除 */
  async deleteBatch(entity: string, ids: (string | number)[]): Promise<void> {
    if (!ids.length) return;
    const res = await this.requestFn(this.url(`${entity}/batch`), {
      method: 'DELETE',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(ids),
    });
    await this.handleResult<null>(res);
  }

  /** 批量更新 */
  async updateBatch<T = Record<string, unknown>>(
    entity: string,
    items: Array<{ id: string | number } & Record<string, unknown>>
  ): Promise<T[]> {
    if (!items.length) return [];
    const res = await this.requestFn(this.url(`${entity}/batch`), {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
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
      headers: { 'Content-Type': 'application/json' },
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
      body: body ? JSON.stringify(body) : undefined,
    });
    return this.handleResult<T>(res);
  }
}
