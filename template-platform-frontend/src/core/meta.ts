/**
 * MetaService：拉取并解析 GET /api/docs 的 OpenAPI 文档
 */

import type { Op } from './types.js';
import type { EntityClient } from './client.js';

/** OpenAPI Schema 属性 */
export interface SchemaProperty {
  type?: string;
  format?: string;
  description?: string;
  enum?: string[];
  $ref?: string;
}

/** 操作元数据（从 OpenAPI path item 解析） */
export interface OperationMeta {
  method: string;
  path: string;
  summary?: string;
  operationId?: string;
  permissions?: string[];
  queryableFields?: Record<string, { type: string; operators: Op[] }>;
}

/** Action 元数据 */
export interface ActionMeta {
  actionName: string;
  summary?: string;
  permissions?: string[];
}

/** 实体元数据（前端解析后的结构） */
export interface EntityMeta {
  pathSegment: string;
  displayName: string;
  operations: Record<string, OperationMeta>;
  actions: ActionMeta[];
  schemas: {
    create?: Record<string, SchemaProperty>;
    update?: Record<string, SchemaProperty>;
    response?: Record<string, SchemaProperty>;
  };
  queryableFields?: Record<string, { type: string; operators: Op[] }>;
}

const OP_LIST: Op[] = ['eq', 'ne', 'like', 'gt', 'gte', 'lt', 'lte', 'in'];
const STRING_OPS: Op[] = ['eq', 'ne', 'like'];
const NUM_OPS: Op[] = ['eq', 'ne', 'gt', 'gte', 'lt', 'lte', 'in'];
const BOOL_OPS: Op[] = ['eq', 'ne'];
const DATE_OPS: Op[] = ['eq', 'gt', 'gte', 'lt', 'lte'];

function opsForType(type: string): Op[] {
  const t = (type ?? 'string').toLowerCase();
  if (t.includes('string')) return STRING_OPS;
  if (t.includes('int') || t.includes('long') || t.includes('number')) return NUM_OPS;
  if (t.includes('bool')) return BOOL_OPS;
  if (t.includes('date') || t.includes('time') || t.includes('instant')) return DATE_OPS;
  return ['eq', 'ne', 'in'];
}

export interface MetaServiceConfig {
  /** 缓存时间（毫秒），0 表示不缓存 */
  cacheTtlMs?: number;
}

export class MetaService {
  private readonly client: EntityClient;
  private readonly cacheTtlMs: number;
  private cached: OpenApiDoc | null = null;
  private cachedAt = 0;

  constructor(client: EntityClient, config: MetaServiceConfig = {}) {
    this.client = client;
    this.cacheTtlMs = config.cacheTtlMs ?? 5 * 60 * 1000; // 默认 5 分钟
  }

  /** 拉取 OpenAPI 文档 */
  async fetch(): Promise<OpenApiDoc> {
    if (this.cached && this.cacheTtlMs > 0 && Date.now() - this.cachedAt < this.cacheTtlMs) {
      return this.cached;
    }
    const docsUrl = this.client.getDocsUrl();
    const res = await fetch(docsUrl);
    if (!res.ok) throw new Error(`拉取 OpenAPI 失败: ${res.status} ${docsUrl}`);
    const doc = (await res.json()) as OpenApiDoc;
    this.cached = doc;
    this.cachedAt = Date.now();
    return doc;
  }

  /** 解析所有实体元数据 */
  async getEntities(): Promise<EntityMeta[]> {
    const doc = await this.fetch();
    return this.parseEntities(doc);
  }

  /** 根据 pathSegment 获取实体元数据 */
  async getEntity(pathSegment: string): Promise<EntityMeta | null> {
    const entities = await this.getEntities();
    return entities.find((e) => e.pathSegment === pathSegment) ?? null;
  }

  private parseEntities(doc: OpenApiDoc): EntityMeta[] {
    const paths = doc.paths ?? {};
    const schemas = (doc.components?.schemas ?? {}) as Record<string, OpenApiSchema>;
    const tags = (doc.tags ?? []) as Array<{ name: string; description?: string }>;
    const tagToPathSegment = new Map<string, string>();
    for (const t of tags) {
      const desc = t.description ?? '';
      const match = desc.match(/pathSegment:\s*(\S+)/);
      if (match) tagToPathSegment.set(t.name, match[1].trim());
      else tagToPathSegment.set(t.name, t.name);
    }

    const entityBySegment = new Map<string, EntityMeta>();

    for (const [path, pathItem] of Object.entries(paths)) {
      if (typeof pathItem !== 'object') continue;
      const parts = path.replace(/^\//, '').split('/').filter(Boolean);
      const segment = parts[0] === 'api' ? parts[1] : parts[0];
      if (!segment || segment.startsWith('{')) continue;

      for (const [method, op] of Object.entries(pathItem)) {
        if (method === 'parameters' || typeof op !== 'object') continue;
        const operation = op as OpenApiOperation;
        const opTags = operation.tags ?? [];
        const tag = opTags[0];
        const displayName = tag ?? segment;
        const pathSeg = tagToPathSegment.get(tag ?? '') ?? segment;

        let meta = entityBySegment.get(pathSeg);
        if (!meta) {
          meta = {
            pathSegment: pathSeg,
            displayName,
            operations: {},
            actions: [],
            schemas: {},
            queryableFields: undefined,
          };
          entityBySegment.set(pathSeg, meta);
        }

        const operationId = operation.operationId ?? `${pathSeg}_${method}`;
        const parts = operationId.split('_');
        const last = parts[parts.length - 1];
        const isAction = path.includes('{actionName}') || (parts.length > 2 && !['search', 'get', 'create', 'update', 'delete', 'deleteBatch', 'updateBatch', 'export'].includes(last));

        if (isAction) {
          meta.actions.push({
            actionName: last,
            summary: operation.summary,
            permissions: (operation as OpenApiOperation & { 'x-permissions'?: string | string[] })['x-permissions'] as string[] | undefined,
          });
        } else {
          const opMeta: OperationMeta = {
            method: method.toUpperCase(),
            path,
            summary: operation.summary,
            operationId,
            permissions: (operation as OpenApiOperation & { 'x-permissions'?: string | string[] })['x-permissions'] as string[] | undefined,
          };
          const qf = (operation as OpenApiOperation & { 'x-queryable-fields'?: Record<string, { type: string; operators: string[] }> })['x-queryable-fields'];
          if (qf) {
            opMeta.queryableFields = {};
            for (const [f, v] of Object.entries(qf)) {
              opMeta.queryableFields[f] = {
                type: v?.type ?? 'string',
                operators: (v?.operators ?? []) as Op[],
              };
            }
          }
          meta.operations[last] = opMeta;
          if (opMeta.queryableFields) meta.queryableFields = opMeta.queryableFields;
        }

        // 解析 schema 引用
        const reqBody = operation.requestBody as { content?: { 'application/json'?: { schema?: { $ref?: string } } } } | undefined;
        const schemaRef = reqBody?.content?.['application/json']?.schema?.$ref;
        if (schemaRef) {
          const schemaName = schemaRef.replace('#/components/schemas/', '');
          const schema = schemas[schemaName];
          if (schema?.properties) meta.schemas[last === 'create' ? 'create' : last === 'update' || last === 'updateBatch' ? 'update' : 'response'] = schema.properties as Record<string, SchemaProperty>;
        }

        const resp = operation.responses?.['200'] as { content?: { 'application/json'?: { schema?: { $ref?: string } } } } | undefined;
        const respRef = resp?.content?.['application/json']?.schema?.$ref;
        if (respRef && !meta.schemas.response) {
          const schemaName = respRef.replace('#/components/schemas/', '');
          const schema = schemas[schemaName];
          if (schema?.properties) meta.schemas.response = schema.properties as Record<string, SchemaProperty>;
        }
      }
    }

    // 补充 queryableFields 的默认 operators
    for (const meta of entityBySegment.values()) {
      if (meta.queryableFields) {
        for (const [f, v] of Object.entries(meta.queryableFields)) {
          if (!v.operators?.length) v.operators = opsForType(v.type);
        }
      }
    }

    return Array.from(entityBySegment.values());
  }
}

/** OpenAPI 文档结构（简化） */
interface OpenApiDoc {
  openapi?: string;
  paths?: Record<string, Record<string, unknown>>;
  components?: { schemas?: Record<string, unknown> };
  tags?: unknown[];
}

interface OpenApiSchema {
  type?: string;
  properties?: Record<string, unknown>;
}

interface OpenApiOperation {
  tags?: string[];
  summary?: string;
  operationId?: string;
  requestBody?: unknown;
  responses?: Record<string, unknown>;
}
