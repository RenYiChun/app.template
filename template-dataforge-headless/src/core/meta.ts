/**
 * MetaService：拉取并解析 GET /api/docs 的 OpenAPI 文档
 */

import {EntityMeta, Op} from './types.js';
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
            const match = /pathSegment:\s*(\S+)/.exec(desc);
            if (match) tagToPathSegment.set(t.name, match[1].trim());
        }

        const entityBySegment = new Map<string, EntityMeta>();

        for (const [path, pathItem] of Object.entries(paths)) {
            if (typeof pathItem !== 'object') continue;
            this.parsePathItem(path, pathItem, tagToPathSegment, entityBySegment, schemas);
        }

        this.postProcessEntities(entityBySegment);

        return Array.from(entityBySegment.values());
    }

    private parsePathItem(
        path: string,
        pathItem: Record<string, unknown>,
        tagToPathSegment: Map<string, string>,
        entityBySegment: Map<string, EntityMeta>,
        schemas: Record<string, OpenApiSchema>
    ) {
        const parts = path.replace(/^\//, '').split('/').filter(Boolean);
        const segment = parts[0] === 'api' ? parts[1] : parts[0];
        if (!segment || segment.startsWith('{')) return;

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

            let meta = entityBySegment.get(pathSeg);
            if (!meta) {
                meta = this.createEntityMeta(pathSeg, displayName);
                entityBySegment.set(pathSeg, meta);
            }

            this.ensureProperties(meta, schemas, displayName, pathSeg, tag);
            this.processOperation(meta, operation, method, path, parts, schemas);
        }
    }

    private createEntityMeta(pathSeg: string, displayName: string): EntityMeta {
        const pluralName = displayName.endsWith('s') ? displayName : displayName + 's';
        return {
            name: pathSeg,
            displayName,
            pluralName,
            pathSegment: pathSeg,
            properties: {},
            operations: {},
            actions: [],
            schemas: {},
            queryableFields: undefined,
        };
    }

    private ensureProperties(meta: EntityMeta, schemas: Record<string, OpenApiSchema>, displayName: string, pathSeg: string, tag?: string) {
        if (Object.keys(meta.properties).length > 0) return;
        const possibleSchemaNames = [displayName, pathSeg, tag].filter(Boolean) as string[];
        for (const schemaName of possibleSchemaNames) {
            const schema = schemas[schemaName];
            if (schema?.properties) {
                const requiredSet = new Set<string>(schema.required ?? []);
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

    private processOperation(
        meta: EntityMeta,
        operation: OpenApiOperation,
        method: string,
        path: string,
        parts: string[],
        schemas: Record<string, OpenApiSchema>
    ) {
        const operationId = operation.operationId ?? `${meta.pathSegment}_${method}`;
        const opIdParts = operationId.split('_');
        const last = opIdParts.at(-1) ?? '';
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
            this.addAction(meta, actionName, operation);
        } else {
            this.addStandardOperation(meta, last, method, path, operationId, operation);
        }

        this.processRequestBody(meta, last, operation, schemas);
        this.processResponse(meta, last, operation, schemas);
    }

    private addAction(meta: EntityMeta, actionName: string, operation: OpenApiOperation) {
        if (!meta.actions!.some((a) => a.actionName === actionName)) {
            meta.actions!.push({
                actionName,
                summary: operation.summary,
                permissions: (operation as OpenApiOperation & {
                    'x-permissions'?: string | string[]
                })['x-permissions'] as string[] | undefined,
            });
        }
    }

    private addStandardOperation(
        meta: EntityMeta,
        last: string,
        method: string,
        path: string,
        operationId: string,
        operation: OpenApiOperation
    ) {
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

        this.handleExportOperation(meta, last, opMeta);

        meta.operations![last] = opMeta;
        if (opMeta.queryableFields) meta.queryableFields = opMeta.queryableFields;
        if (last === 'export') meta.exportEnabled = true;
    }

    private handleExportOperation(meta: EntityMeta, last: string, opMeta: OperationMeta) {
        if (last === 'export' && opMeta.queryableFields) {
            let searchOp = meta.operations!['search'];
            if (searchOp && (!searchOp.queryableFields || Object.keys(searchOp.queryableFields).length === 0)) {
                searchOp.queryableFields = {...opMeta.queryableFields};
                meta.queryableFields = searchOp.queryableFields;
            }
            meta.queryableFields ??= {...opMeta.queryableFields};
        }
    }

    private processRequestBody(meta: EntityMeta, last: string, operation: OpenApiOperation, schemas: Record<string, OpenApiSchema>) {
        const reqBody = operation.requestBody as {
            content?: { 'application/json'?: { schema?: { $ref?: string } } }
        } | undefined;
        const schemaRef = reqBody?.content?.['application/json']?.schema?.$ref;
        if (schemaRef && (last === 'create' || last === 'update' || last === 'updateBatch')) {
            const schemaName = schemaRef.replace('#/components/schemas/', '');
            const schema = schemas[schemaName];
            if (schema?.properties) {
                const requiredSet = new Set<string>(schema.required ?? []);
                const props = schema.properties;
                const enriched: Record<string, SchemaProperty> = {};
                for (const [k, v] of Object.entries(props)) {
                    enriched[k] = {...(typeof v === 'object' && v !== null ? v : {}), required: requiredSet.has(k)};
                }
                meta.schemas![last === 'create' ? 'create' : 'update'] = enriched;
            }
        }
    }

    private processResponse(meta: EntityMeta, last: string, operation: OpenApiOperation, schemas: Record<string, OpenApiSchema>) {
        const resp = operation.responses?.['200'] as {
            content?: {
                'application/json'?: { schema?: { $ref?: string; properties?: Record<string, unknown> } }
            }
        } | undefined;
        const respSchema = resp?.content?.['application/json']?.schema;
        const respRef = respSchema?.$ref;

        if (!respRef && !respSchema?.properties) {
            return;
        }

        const schemaName = respRef?.replace('#/components/schemas/', '');
        const schema = schemaName ? schemas[schemaName] : undefined;
        const props = schema?.properties;

        if (last === 'search') {
            this.processSearchResponse(meta, props, schemas);
        } else if (last === 'get') {
            this.processGetResponse(meta, props);
        }
    }

    private processSearchResponse(
        meta: EntityMeta,
        props: Record<string, unknown> | undefined,
        schemas: Record<string, OpenApiSchema>
    ) {
        const contentSchema = props && typeof props === 'object' && props.content && typeof (props.content as any) === 'object'
            ? (props.content as { items?: { $ref?: string } })?.items?.$ref
            : undefined;
        const itemRef = contentSchema?.replace('#/components/schemas/', '');
        const itemSchema = itemRef ? schemas[itemRef] : undefined;

        if (itemSchema?.properties) {
            meta.schemas!.pageResponse = itemSchema.properties as Record<string, SchemaProperty>;
        } else if (props && typeof props === 'object') {
            meta.schemas!.pageResponse = props as Record<string, SchemaProperty>;
        }
    }

    private processGetResponse(meta: EntityMeta, props: Record<string, unknown> | undefined) {
        if (props && typeof props === 'object') {
            meta.schemas!.detail = props as Record<string, SchemaProperty>;
        }
    }

    private postProcessEntities(entityBySegment: Map<string, EntityMeta>) {
        for (const meta of entityBySegment.values()) {
            this.fillDefaultOperators(meta);
            this.fillDefaultProperties(meta);
        }
    }

    private fillDefaultOperators(meta: EntityMeta) {
        if (meta.queryableFields) {
            for (const v of Object.values(meta.queryableFields)) {
                const fieldMeta = v as { type: string; operators: Op[]; label?: string; order?: number };
                if (!fieldMeta.operators?.length) fieldMeta.operators = opsForType(fieldMeta.type);
            }
        }
    }

    private fillDefaultProperties(meta: EntityMeta) {
        if (Object.keys(meta.properties).length === 0 && meta.schemas?.pageResponse && typeof meta.schemas.pageResponse === 'object') {
            meta.properties = {...meta.schemas.pageResponse} as Record<string, SchemaProperty>;
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
