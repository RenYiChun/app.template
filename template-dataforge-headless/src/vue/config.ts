/**
 * EntityConfigRegistry：实体级配置覆盖
 */

import type {EntityMeta} from '../core';

export interface ColumnConfig {
    prop: string;
    label?: string;
    width?: number | string;
    minWidth?: number;
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

/** 列配置由后端元数据解析；注册表仅作覆盖（如 label、formatter）。表格列顺序严格按 meta.fields 的 columnOrder，仅包含在 pageResponse 中存在的字段。 */
export function resolveColumns(
    pathSegment: string,
    meta: EntityMeta | null
): ColumnConfig[] {
    const props = meta?.properties ?? meta?.schemas?.pageResponse;
    if (!props || typeof props !== 'object') return [];

    const config = registry.get(pathSegment);
    const propsObj = props as Record<string, unknown>;
    let keys: string[];
    if (meta?.fields?.length) {
        const sortedFields = [...meta.fields].sort((a, b) => (a.columnOrder ?? 999) - (b.columnOrder ?? 999));
        keys = sortedFields.map((f) => f.name).filter((name) => Object.prototype.hasOwnProperty.call(propsObj, name));
        const keySet = new Set(keys);
        const extra = Object.keys(propsObj).filter((k) => !keySet.has(k));
        keys = keys.concat(extra);
    } else {
        keys = Object.keys(propsObj);
    }
    return keys.map((prop) => {
        const configCol = config?.columns?.find((c) => c.prop === prop);
        const metaField = meta?.fields?.find((f) => f.name === prop);
        const metaLabel = (props as Record<string, { description?: string }>)[prop]?.description;
        const base: ColumnConfig = {
            prop,
            label: configCol?.label ?? metaLabel ?? prop,
            width: metaField?.columnWidth && (metaField.columnWidth > 0 || metaField.columnWidth === -1) ? metaField.columnWidth : undefined,
            sortable: metaField?.columnSortable,
        };
        return {...base, ...configCol};
    });
}
