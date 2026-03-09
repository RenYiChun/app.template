/**
 * 关联数据缓存：对 getOptions、getTree、batchLookup 结果做内存缓存，并支持按实体失效。
 * 业务层在实体更新/删除后调用 invalidateEntity 或 invalidateOptions/invalidateTree/invalidateBatchLookup。
 */

import type { EntityOption, PagedResult, TreeNode } from './types.js';

export interface AssociationCacheClient {
    getOptions(
        entityName: string,
        params?: { query?: string; page?: number; size?: number; sort?: string }
    ): Promise<PagedResult<EntityOption>>;
    getTree(
        entityName: string,
        params?: { parentId?: string | null; maxDepth?: number; includeDisabled?: boolean }
    ): Promise<TreeNode[]>;
    batchLookup(
        entityName: string,
        ids: string | (string | number)[],
        options?: { fields?: string }
    ): Promise<Record<string | number, { id: unknown; label: string; [key: string]: unknown }>>;
}

export interface AssociationCacheOptions {
    /** 缓存条目 TTL（毫秒），默认 5 分钟 */
    ttlMs?: number;
    /** 每个缓存类型最大条目数（options/tree/batch 分别计算），默认 200 */
    maxEntries?: number;
}

const DEFAULT_TTL_MS = 5 * 60 * 1000;
const DEFAULT_MAX_ENTRIES = 200;

interface CacheEntry<T> {
    data: T;
    expiresAt: number;
}

function stableJson(obj: unknown): string {
    if (obj == null) return '';
    return JSON.stringify(obj, Object.keys(obj as object).sort());
}

function sortedIdsKey(ids: string | (string | number)[]): string {
    const arr = Array.isArray(ids) ? ids.map(String).sort() : [String(ids)];
    return arr.join(',');
}

export class AssociationCache implements AssociationCacheClient {
    private readonly client: AssociationCacheClient;
    private readonly ttlMs: number;
    private readonly maxEntries: number;
    private readonly optionsCache = new Map<string, CacheEntry<PagedResult<EntityOption>>>();
    private readonly treeCache = new Map<string, CacheEntry<TreeNode[]>>();
    private readonly batchCache = new Map<string, CacheEntry<Record<string | number, { id: unknown; label: string; [key: string]: unknown }>>>();

    constructor(client: AssociationCacheClient, options: AssociationCacheOptions = {}) {
        this.client = client;
        this.ttlMs = options.ttlMs ?? DEFAULT_TTL_MS;
        this.maxEntries = options.maxEntries ?? DEFAULT_MAX_ENTRIES;
    }

    async getOptions(
        entityName: string,
        params?: { query?: string; page?: number; size?: number; sort?: string }
    ): Promise<PagedResult<EntityOption>> {
        const key = `opt:${entityName}:${stableJson(params ?? {})}`;
        const hit = this.optionsCache.get(key);
        if (hit && hit.expiresAt > Date.now()) return hit.data;
        const data = await this.client.getOptions(entityName, params);
        this.setWithEvict(this.optionsCache, key, data);
        return data;
    }

    async getTree(
        entityName: string,
        params?: { parentId?: string | null; maxDepth?: number; includeDisabled?: boolean }
    ): Promise<TreeNode[]> {
        const key = `tree:${entityName}:${stableJson(params ?? {})}`;
        const hit = this.treeCache.get(key);
        if (hit && hit.expiresAt > Date.now()) return hit.data;
        const data = await this.client.getTree(entityName, params);
        this.setWithEvict(this.treeCache, key, data);
        return data;
    }

    async batchLookup(
        entityName: string,
        ids: string | (string | number)[],
        options?: { fields?: string }
    ): Promise<Record<string | number, { id: unknown; label: string; [key: string]: unknown }>> {
        const idStr = sortedIdsKey(ids);
        if (!idStr) return {};
        const key = `batch:${entityName}:${idStr}:${options?.fields ?? ''}`;
        const hit = this.batchCache.get(key);
        if (hit && hit.expiresAt > Date.now()) return hit.data;
        const data = await this.client.batchLookup(entityName, ids, options);
        this.setWithEvict(this.batchCache, key, data);
        return data;
    }

    private setWithEvict<T>(map: Map<string, CacheEntry<T>>, key: string, data: T): void {
        if (map.size >= this.maxEntries) {
            const firstKey = map.keys().next().value;
            if (firstKey != null) map.delete(firstKey);
        }
        map.set(key, { data, expiresAt: Date.now() + this.ttlMs });
    }

    /** 失效某实体的全部 options 缓存 */
    invalidateOptions(entityName: string): void {
        for (const k of this.optionsCache.keys()) {
            if (k.startsWith(`opt:${entityName}:`)) this.optionsCache.delete(k);
        }
    }

    /** 失效某实体的全部 tree 缓存 */
    invalidateTree(entityName: string): void {
        for (const k of this.treeCache.keys()) {
            if (k.startsWith(`tree:${entityName}:`)) this.treeCache.delete(k);
        }
    }

    /** 失效某实体的 batch-lookup 缓存；若传入 ids 则只失效包含这些 id 的条目 */
    invalidateBatchLookup(entityName: string, ids?: (string | number)[]): void {
        if (ids == null || ids.length === 0) {
            for (const k of this.batchCache.keys()) {
                if (k.startsWith(`batch:${entityName}:`)) this.batchCache.delete(k);
            }
            return;
        }
        const idSet = new Set(ids.map(String));
        for (const k of this.batchCache.keys()) {
            if (!k.startsWith(`batch:${entityName}:`)) continue;
            const part = k.slice(`batch:${entityName}:`.length);
            const idsInKey = part.split(':')[0];
            const keysHaveId = idsInKey.split(',').some((id) => idSet.has(id));
            if (keysHaveId) this.batchCache.delete(k);
        }
    }

    /** 失效某实体的所有关联缓存（options + tree + batch-lookup） */
    invalidateEntity(entityName: string): void {
        this.invalidateOptions(entityName);
        this.invalidateTree(entityName);
        this.invalidateBatchLookup(entityName);
    }
}
