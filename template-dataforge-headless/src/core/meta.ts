/**
 * MetaService：拉取并解析 GET /api/docs 的 OpenAPI 文档
 */

import {Op, EntityMeta} from './types.js';
import type {EntityClient} from './client.js';

/** OpenAPI Schema 属性 */
export interface SchemaProperty {
    type?: string;
    format?: string;
    description?: string;
    enum?: string[];
    $ref?: string;
    required?: boolean;
}

/** 操作元数据（从 OpenAPI path item 解析） */
export interface OperationMeta {
    method: string;
    path: string;
    summary?: string;
    operationId?: string;
    permissions?: string[];
    queryableFields?: Record<string, { type: string; operators: Op[]; label?: string; order?: number }>;
}

/** Action 元数据 */
export interface ActionMeta {
    actionName: string;
    summary?: string;
    permissions?: string[];
}




const OP_LIST: Op[] = [Op.EQ, Op.NE, Op.LIKE, Op.GT, Op.GE, Op.LT, Op.LE, Op.IN];
const STRING_OPS: Op[] = [Op.EQ, Op.NE, Op.LIKE];
const NUM_OPS: Op[] = [Op.EQ, Op.NE, Op.GT, Op.GE, Op.LT, Op.LE, Op.IN];
const BOOL_OPS: Op[] = [Op.EQ, Op.NE];
const DATE_OPS: Op[] = [Op.EQ, Op.GT, Op.GE, Op.LT, Op.LE];

function opsForType(type: string): Op[] {
    const t = (type ?? 'string').toLowerCase();
    if (t.includes('string')) return STRING_OPS;
    if (t.includes('int') || t.includes('long') || t.includes('number')) return NUM_OPS;
    if (t.includes('bool')) return BOOL_OPS;
    if (t.includes('date') || t.includes('time') || t.includes('instant')) return DATE_OPS;
    return [Op.EQ, Op.NE, Op.IN];
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
        // Add timestamp to prevent caching
        const urlWithTs = docsUrl + (docsUrl.includes('?') ? '&' : '?') + 't=' + Date.now();

        // 使用 EntityClient.request 保持统一的请求行为（如 baseURL 处理）
        // 但 Docs 通常是公开的，如果 Token 失效可能导致 401。
        // 如果返回 401，我们抛出特定错误供上层处理（例如跳转登录），而不是直接操作 window.location
        const res = await this.client.request(urlWithTs, {method: 'GET'});

        if (res.status === 401) {
            throw new Error('Unauthorized: 无法获取 OpenAPI 文档，请检查登录状态');
        }
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
                if (tag && !tagToPathSegment.has(tag)) {
                    tagToPathSegment.set(tag, segment);
                }
                const pathSeg = (tag ? tagToPathSegment.get(tag) : undefined) ?? segment;

                let meta: EntityMeta;
                const existingMeta = entityBySegment.get(pathSeg);
                if (existingMeta) {
                    meta = existingMeta;
                } else {
                    const pluralName = displayName.endsWith('s') ? displayName : displayName + 's';
                    meta = {
                        name: pathSeg,
                        displayName,
                        pluralName,
                        pathSegment: pathSeg, // Add pathSegment here
                        properties: {},
                        operations: {},
                        actions: [],
                        schemas: {},
                        queryableFields: undefined,
                    };
                    entityBySegment.set(pathSeg, meta);
                }

                // 尝试从 schemas 中找到对应的实体定义，填充 properties
                if (Object.keys(meta.properties).length === 0) {
                    const possibleSchemaNames = [displayName, pathSeg, tag].filter(Boolean) as string[];
                    for (const schemaName of possibleSchemaNames) {
                        const schema = schemas[schemaName];
                        if (schema?.properties) {
                            const requiredSet = new Set<string>((schema.required ?? []) as string[]);
                            const props = schema.properties as Record<string, SchemaProperty>;
                            const enriched: Record<string, SchemaProperty> = {};
                            for (const [k, v] of Object.entries(props)) {
                                enriched[k] = {...v, required: requiredSet.has(k)};
                            }
                            meta.properties = enriched;
                            break;
                        }
                    }
                }

                const operationId = operation.operationId ?? `${pathSeg}_${method}`;
                const opIdParts = operationId.split('_');
                const last = opIdParts[opIdParts.length - 1];
                const knownOps = ['search', 'get', 'create', 'update', 'delete', 'deleteBatch', 'updateBatch', 'export'];
                const actionIdx = parts.indexOf('_action');
                const actionFromPath = actionIdx >= 0 ? parts[actionIdx + 1] : undefined;
                const hasActionInPath = actionIdx >= 0 || path.includes('/_action/');
                const isAction =
                    hasActionInPath ||
                    path.includes('{actionName}') ||
                    (opIdParts.length > 2 && !knownOps.includes(last));
                const actionName =
                    (actionFromPath && !actionFromPath.startsWith('{') ? actionFromPath : undefined) ?? last;

                if (isAction) {
                    if (!meta.actions!.some((a) => a.actionName === actionName)) {
                        meta.actions!.push({
                            actionName,
                            summary: operation.summary,
                            permissions: (operation as OpenApiOperation & {
                                'x-permissions'?: string | string[]
                            })['x-permissions'] as string[] | undefined,
                        });
                    }
                } else {
                    const opMeta: OperationMeta = {
                        method: method.toUpperCase(),
                        path,
                        summary: operation.summary,
                        operationId,
                        permissions: (operation as OpenApiOperation & {
                            'x-permissions'?: string | string[]
                        })['x-permissions'] as string[] | undefined,
                    };
                    const qf = (operation as OpenApiOperation & {
                        'x-queryable-fields'?: Record<string, {
                            type: string;
                            operators: string[];
                            label?: string;
                            order?: number
                        }>
                    })['x-queryable-fields'];
                    if (qf) {
                        opMeta.queryableFields = {};
                        for (const [f, v] of Object.entries(qf)) {
                            opMeta.queryableFields[f] = {
                                type: v?.type ?? 'string',
                                operators: (v?.operators ?? []) as Op[],
                                label: v?.label,
                                order: v?.order,
                            };
                        }
                    }

                    // 如果是 export 操作，且有 queryableFields，则尝试将其复制给 search 操作
                    if (last === 'export' && opMeta.queryableFields) {
                        // 查找对应的 search 操作
                    let searchOp = meta.operations!['search'];
                    if (!searchOp) {
                        // 如果 search 操作不存在（可能是因为 methodName 映射问题），尝试查找与 export 同路径但方法为 POST 的操作作为 search
                        // 这里的假设是：search 和 export 通常在同一层级，或者 search 是列表页的默认查询接口
                        // 但在 GenericEntityController 中，search 是 /{entity}/search，export 是 /{entity}/export
                        // 如果我们找不到 'search' key，可能是 operationId 解析问题
                        // 暂时只处理 key 为 'search' 的情况，因为这是标准约定
                    }

                    if (searchOp && (!searchOp.queryableFields || Object.keys(searchOp.queryableFields).length === 0)) {
                        searchOp.queryableFields = {...opMeta.queryableFields};
                        // 同时更新 entity 级别的 queryableFields，确保 UI 能读到
                        meta.queryableFields = searchOp.queryableFields;
                    }

                    // 如果 entity 级别还没有 queryableFields，直接使用 export 的配置作为默认
                    if (!meta.queryableFields) {
                        meta.queryableFields = {...opMeta.queryableFields};
                    }
                }

                meta.operations![last] = opMeta;
                if (opMeta.queryableFields) meta.queryableFields = opMeta.queryableFields;
                if (last === 'export') meta.exportEnabled = true;
            }

            // 解析请求体 schema：仅 create/update 的 requestBody 写入 schemas，避免把 search 的请求体（filters/sort/page/size）误当作 response 列
            const reqBody = operation.requestBody as {
                content?: { 'application/json'?: { schema?: { $ref?: string } } }
            } | undefined;
            const schemaRef = reqBody?.content?.['application/json']?.schema?.$ref;
            if (schemaRef && (last === 'create' || last === 'update' || last === 'updateBatch')) {
                const schemaName = schemaRef.replace('#/components/schemas/', '');
                const schema = schemas[schemaName];
                if (schema?.properties) {
                    const requiredSet = new Set<string>((schema.required ?? []) as string[]);
                    const props = schema.properties as Record<string, SchemaProperty>;
                    const enriched: Record<string, SchemaProperty> = {};
                    for (const [k, v] of Object.entries(props)) {
                        enriched[k] = {...v, required: requiredSet.has(k)};
                    }
                    meta.schemas![last === 'create' ? 'create' : 'update'] = enriched;
                }
            }

            const resp = operation.responses?.['200'] as {
                content?: { 'application/json'?: { schema?: { $ref?: string; properties?: Record<string, unknown> } } }
            } | undefined;
            const respSchema = resp?.content?.['application/json']?.schema;
            const respRef = respSchema?.$ref;
            if (respRef || respSchema?.properties) {
                const schemaName = respRef?.replace('#/components/schemas/', '');
                const schema = schemaName ? (schemas[schemaName] as OpenApiSchema) : undefined;
                const props = schema?.properties;
                if (last === 'search') {
                    // 列表接口：若为 PagedResult，用 content.items 的 schema 作为 pageResponse（表格列）
                    const contentSchema = props && typeof props === 'object' && props.content && typeof (props.content as any) === 'object'
                        ? (props.content as { items?: { $ref?: string } })?.items?.$ref
                        : undefined;
                    const itemRef = contentSchema?.replace('#/components/schemas/', '');
                    const itemSchema = itemRef ? (schemas[itemRef] as OpenApiSchema) : undefined;
                    if (itemSchema?.properties) {
                        meta.schemas!.pageResponse = itemSchema.properties as Record<string, SchemaProperty>;
                    } else if (props && typeof props === 'object') {
                        meta.schemas!.pageResponse = props as Record<string, SchemaProperty>;
                    }
                } else if (last === 'get') {
                    // 详情接口：200 响应 schema 作为 detail（单条展示/表单回显）
                    if (props && typeof props === 'object') {
                        meta.schemas!.detail = props as Record<string, SchemaProperty>;
                    }
                }
            }
            }
        }

        // 补充 queryableFields 的默认 operators
        for (const meta of entityBySegment.values()) {
            if (meta.queryableFields) {
                for (const [f, v] of Object.entries(meta.queryableFields)) {
                    const fieldMeta = v as { type: string; operators: Op[]; label?: string; order?: number };
                    if (!fieldMeta.operators?.length) fieldMeta.operators = opsForType(fieldMeta.type);
                }
            }
        }

        // 若 properties 为空但有 schemas.pageResponse，用其填充 properties，便于列解析与元数据页展示
        for (const meta of entityBySegment.values()) {
            if (Object.keys(meta.properties).length === 0 && meta.schemas?.pageResponse && typeof meta.schemas.pageResponse === 'object') {
                meta.properties = { ...meta.schemas.pageResponse } as Record<string, SchemaProperty>;
            }
            if (Object.keys(meta.properties).length === 0) {
                console.warn(
                    '[MetaService] entity pathSegment=%s has empty properties → list columns will be empty. ' +
                    'Cause: backend GET /api/docs returned empty schema for list item (PageResponseDTO not found at runtime). ' +
                    'Fix: mvn clean compile -pl template-dataforge-sample-backend so annotation processor generates *PageResponseDTO.',
                    meta.pathSegment
                );
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
    required?: string[];
}

interface OpenApiOperation {
    tags?: string[];
    summary?: string;
    operationId?: string;
    requestBody?: unknown;
    responses?: Record<string, unknown>;
}
