/**
 * EntityConfigRegistry：实体级配置覆盖
 */

import type { EntityMeta } from '@lrenyi/platform-headless';

export interface ColumnConfig {
  prop: string;
  label?: string;
  width?: number | string;
  sortable?: boolean;
  formatter?: (value: unknown, row?: Record<string, unknown>) => string | unknown;
}

export interface EntityConfig {
  displayName?: string;
  columns?: ColumnConfig[];
  /** 可搜索的字段列表，不传则用 meta.queryableFields */
  searchFields?: string[];
}

const registry = new Map<string, EntityConfig>();

export function registerEntityConfig(pathSegment: string, config: EntityConfig): void {
  registry.set(pathSegment, { ...registry.get(pathSegment), ...config });
}

export function getEntityConfig(pathSegment: string): EntityConfig | undefined {
  return registry.get(pathSegment);
}

/** 合并 meta 与 config，生成最终列配置 */
export function resolveColumns(
  pathSegment: string,
  meta: EntityMeta | null
): ColumnConfig[] {
  const config = registry.get(pathSegment);
  if (config?.columns?.length) return config.columns;

  if (!meta?.schemas?.response) return [];
  const props = meta.schemas.response;
  return Object.keys(props).map((prop) => ({
    prop,
    label: prop,
    ...config?.columns?.find((c) => c.prop === prop),
  }));
}
