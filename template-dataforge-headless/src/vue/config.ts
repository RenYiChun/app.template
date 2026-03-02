/**
 * EntityConfigRegistry：实体级配置覆盖
 */

import type {EntityMeta} from '../core';

export interface ColumnConfig {
    prop: string;
    label?: string;
    width?: number | string;
    sortable?: boolean;
    formatter?: (value: unknown, row?: Record<string, unknown>) => string;
}

export interface EntityConfig {
    displayName?: string;
    columns?: ColumnConfig[];
    /** 可搜索的字段列表，不传则用 meta.queryableFields */
    searchFields?: string[];
}

const registry = new Map<string, EntityConfig>();

export function registerEntityConfig(pathSegment: string, config: EntityConfig): void {
    registry.set(pathSegment, {...registry.get(pathSegment), ...config});
}

export function getEntityConfig(pathSegment: string): EntityConfig | undefined {
    return registry.get(pathSegment);
}

/** 列配置由后端元数据解析；注册表仅作覆盖（如 label、formatter）。表格列用实体字段：优先 meta.properties（实体 schema），避免用列表响应的 PagedResult 或 search 请求体。列顺序按 meta.fields 的 columnOrder。 */
export function resolveColumns(
    pathSegment: string,
    meta: EntityMeta | null
): ColumnConfig[] {
    const props = meta?.properties ?? meta?.schemas?.pageResponse;
    if (!props || typeof props !== 'object') return [];

    const config = registry.get(pathSegment);
    const keys = Object.keys(props);
    if (meta?.fields?.length) {
        const orderMap = new Map(meta.fields.map((f, i) => [f.name, f.columnOrder ?? i]));
        keys.sort((a, b) => (orderMap.get(a) ?? 999) - (orderMap.get(b) ?? 999));
    }
    return keys.map((prop) => {
        const configCol = config?.columns?.find((c) => c.prop === prop);
        const metaField = meta?.fields?.find((f) => f.name === prop);
        const metaLabel = (props as Record<string, { description?: string }>)[prop]?.description;
        const base: ColumnConfig = {
            prop,
            label: configCol?.label ?? metaLabel ?? prop,
            width: metaField?.columnWidth && metaField.columnWidth > 0 ? metaField.columnWidth : undefined,
            sortable: metaField?.columnSortable,
        };
        return {...base, ...configCol};
    });
}
