/**
 * useEntityCrud：实体 CRUD 逻辑 Composables
 */

import {ref} from 'vue';
import type {EntityClient, FilterCondition, SearchRequest, SortOrder} from '../../core';

export interface UseEntityCrudOptions {
    initialPageSize?: number;
    initialSort?: SortOrder[];
    onError?: (err: Error) => void;
}

export function useEntityCrud<T = Record<string, unknown>>(
    client: EntityClient,
    entity: string,
    options: UseEntityCrudOptions = {}
) {
    const {
        initialPageSize = 20,
        initialSort = [],
        onError,
    } = options;

    const items = ref<T[]>([]);
    const total = ref(0);
    const loading = ref(false);
    const filters = ref<FilterCondition[]>([]);
    const sort = ref<SortOrder[]>(initialSort);
    const page = ref(0);
    const size = ref(initialPageSize);

    const handleError = (err: unknown) => {
        const e = err instanceof Error ? err : new Error(String(err));
        onError?.(e);
        throw e;
    };

    const search = async (overrides?: Partial<SearchRequest>) => {
        loading.value = true;
        try {
            const req: SearchRequest = {
                filters: overrides?.filters ?? filters.value,
                sort: overrides?.sort ?? sort.value,
                page: overrides?.page ?? page.value,
                size: overrides?.size ?? size.value,
            };
            const result = await client.search<T>(entity, req);
            items.value = result.content ?? [];
            total.value = result.totalElements ?? 0;
        } catch (e) {
            handleError(e);
        } finally {
            loading.value = false;
        }
    };

    const resetFilters = () => {
        filters.value = [];
        sort.value = initialSort;
        page.value = 0;
    };

    const getOne = async (id: string | number): Promise<T | null> => {
        try {
            return await client.get<T>(entity, id);
        } catch (e) {
            handleError(e);
        }
        return null;
    };

    const create = async (body: Partial<T>): Promise<T> => {
        return await client.create<T>(entity, body);
    };

    const update = async (
        id: string | number,
        body: Partial<T>
    ): Promise<T> => {
        return await client.update<T>(entity, id, body);
    };

    const remove = async (id: string | number): Promise<void> => {
        await client.delete(entity, id);
    };

    const removeBatch = async (ids: (string | number)[]): Promise<void> => {
        if (ids.length === 0) return;
        await client.deleteBatch(entity, ids);
    };

    const updateBatch = async (
        itemsToUpdate: Array<{ id: string | number } & Partial<T>>
    ): Promise<T[]> => {
        // Note: client.updateBatch needs to be implemented in client.ts if it's missing,
        // or we simulate it here. Assuming client.ts has updateBatch as per previous check.
        // However, I recall client.ts having updateBatch but maybe I missed it in the last read?
        // Let's assume client.ts has it or I will add it.
        // Actually, looking at client.ts read earlier, it DOES have updateBatch.
        return client.updateBatch<T>(entity, itemsToUpdate as any);
    };

    const exportExcel = async (req?: SearchRequest): Promise<Blob> => {
        return client.export(entity, req ?? {filters: filters.value, sort: sort.value, page: 0, size: 10000});
    };

    const executeAction = async <T = Record<string, unknown>>(
        id: string | number,
        actionName: string,
        body?: Record<string, unknown>
    ): Promise<T> => {
        return client.executeAction<T>(entity, id, actionName, body);
    };

    return {
        // 列表状态
        items,
        total,
        loading,
        filters,
        sort,
        page,
        size,
        // 列表操作
        search,
        resetFilters,
        // 单条操作
        getOne,
        create,
        update,
        remove,
        removeBatch,
        updateBatch,
        // 导出 & Action
        exportExcel,
        executeAction,
    };
}
