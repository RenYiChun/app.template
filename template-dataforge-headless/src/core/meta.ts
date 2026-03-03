/**
 * MetaService：拉取并解析元数据（支持多服务聚合与本地离线模式）
 */

import {EntityMeta, Op, ServiceConfig} from './types.js';
import type {EntityClient} from './client.js';
import {joinPath} from './utils.js';

export interface MetaServiceConfig {
    /** 缓存时间（毫秒），0 表示不缓存 */
    cacheTtlMs?: number;
    /** 服务列表配置 */
    services?: ServiceConfig[];
}

/** 表单 schema 中单个字段的属性（create/update schema 的 value 类型） */
export interface SchemaProperty {
    type?: string;
    description?: string;
    required?: boolean;
    enum?: string[];
    format?: string;
    [key: string]: unknown;
}

const STRING_OPS: Op[] = [Op.EQ, Op.NE, Op.LIKE];
const NUM_OPS: Op[] = [Op.EQ, Op.NE, Op.GT, Op.GE, Op.LT, Op.LE, Op.IN];
const BOOL_OPS: Op[] = [Op.EQ, Op.NE];
const DATE_OPS: Op[] = [Op.EQ, Op.GT, Op.GE, Op.LT, Op.LE];

const SYSTEM_FIELDS = new Set<string>([
    'createTime', 'updateTime', 'createBy', 'updateBy', 'deleted', 'version', 'remark'
]);

function opsForType(type: string): Op[] {
    const t = (type ?? 'string').toLowerCase();
    if (t.includes('string')) return STRING_OPS;
    if (t.includes('int') || t.includes('long') || t.includes('double') || t.includes('float') || t.includes('decimal') || t.includes('number')) return NUM_OPS;
    if (t.includes('bool')) return BOOL_OPS;
    if (t.includes('date') || t.includes('time') || t.includes('instant')) return DATE_OPS;
    return [Op.EQ, Op.NE, Op.IN];
}

export class MetaService {
    private readonly client: EntityClient;
    private readonly cacheTtlMs: number;
    private readonly services: ServiceConfig[];
    private cached: EntityMeta[] | null = null;
    private cachedAt = 0;

    constructor(client: EntityClient, config: MetaServiceConfig = {}) {
        this.client = client;
        this.cacheTtlMs = config.cacheTtlMs ?? 5 * 60 * 1000; // 默认 5 分钟
        this.services = config.services ?? [];
    }

    /** 拉取所有实体元数据 */
    async getEntities(): Promise<EntityMeta[]> {
        if (this.isCacheValid()) {
            return this.cached!;
        }

        const servicesToFetch = this.getServicesToFetch();
        const allEntities: EntityMeta[] = [];
        const serviceMap: Record<string, string> = {};

        for (const service of servicesToFetch) {
            const result = await this.fetchEntitiesForService(service);
            if (Array.isArray(result.entities)) {
                result.entities.forEach(e => {
                    e.serviceName = service.name;
                    this.adaptCompatibility(e);
                    serviceMap[e.pathSegment] = service.name;
                });
                allEntities.push(...result.entities);
            }
        }

        this.cached = allEntities;
        this.cachedAt = Date.now();
        this.client.registerServices(this.services);
        this.client.registerServiceMap(serviceMap);

        return allEntities;
    }

    private isCacheValid(): boolean {
        return !!(this.cached && this.cacheTtlMs > 0 && Date.now() - this.cachedAt < this.cacheTtlMs);
    }

    private getServicesToFetch(): ServiceConfig[] {
        return this.services.length > 0 ? this.services : [{
            name: 'default',
            baseUrl: '',
            default: true,
            metadata: {type: 'remote'}
        } as ServiceConfig];
    }

    private async fetchEntitiesForService(service: ServiceConfig): Promise<{entities: EntityMeta[]}> {
        try {
            const entities = service.metadata?.type === 'local'
                ? this.getLocalEntities(service)
                : await this.fetchRemoteEntities(service);
            return {entities};
        } catch (e) {
            console.error(`[MetaService] Failed to fetch metadata for service ${service.name}:`, e);
            return {entities: []};
        }
    }

    private getLocalEntities(service: ServiceConfig): EntityMeta[] {
        const data = service.metadata?.type === 'local' ? service.metadata.data : undefined;
        return Array.isArray(data) ? data : [];
    }

    private async fetchRemoteEntities(service: ServiceConfig): Promise<EntityMeta[]> {
        const url = this.buildMetadataUrl(service);
        const res = await this.client.request(url, {method: 'GET'});
        const json = await res.json();
        return Array.isArray(json) ? json : (json.data || []);
    }

    private buildMetadataUrl(service: ServiceConfig): string {
        if (!service.baseUrl) {
            return 'metadata/entities';
        }
        if (service.baseUrl.startsWith('http')) {
            return joinPath(service.baseUrl, 'metadata/entities');
        }
        if (service.baseUrl.startsWith('/')) {
            return 'metadata/entities';
        }
        return joinPath(service.baseUrl, 'metadata/entities');
    }

    /** 根据 pathSegment 获取实体元数据 */
    async getEntity(pathSegment: string): Promise<EntityMeta | null> {
        const entities = await this.getEntities();
        return entities.find((e) => e.pathSegment === pathSegment) ?? null;
    }

    /**
     * 适配旧版 UI 组件所需的结构 (schemas, operations, queryableFields)
     * 基于新版的 fields 列表自动生成
     */
    private adaptCompatibility(meta: EntityMeta) {
        meta.name = meta.entityName; // 别名
        meta.schemas ??= {};
        meta.queryableFields ??= {};
        meta.operations ??= {};

        // Use backend provided schemas if available
        if (meta.schemas.pageResponse && meta.schemas.create && meta.schemas.update) {
            // Fill default operations if needed
            this.fillDefaultOperations(meta);
            return;
        }

        const pageResponseProps: Record<string, any> = {};
        const createProps: Record<string, any> = {};
        const updateProps: Record<string, any> = {};

        const strictPageResponse = new Set<string>();
        meta.fields?.forEach(f => {
            if (f.dtoIncludeTypes?.includes('PAGE_RESPONSE')) {
                strictPageResponse.add(f.name);
            }
        });
        const hasStrictPageResponse = strictPageResponse.size > 0;

        const compareFormFields = (a: { group?: string; groupOrder?: number; formOrder?: number }, b: typeof a) => {
            const ga = a.group ?? '';
            const gb = b.group ?? '';
            const c = ga.localeCompare(gb);
            if (c !== 0) return c;
            return (a.groupOrder ?? 0) - (b.groupOrder ?? 0) || (a.formOrder ?? 0) - (b.formOrder ?? 0);
        };

        const pageFields = (meta.fields ?? []).filter(f =>
            f.columnVisible !== false && (
                f.name === 'id' ||
                !!f.dtoIncludeTypes?.includes('PAGE_RESPONSE') ||
                (!hasStrictPageResponse && !SYSTEM_FIELDS.has(f.name))
            )
        ).sort((a, b) => (a.columnOrder ?? 0) - (b.columnOrder ?? 0));

        const formFields = (meta.fields ?? []).filter(f =>
            !f.readonly && !f.dtoReadOnly && f.name !== 'id' && f.name !== 'createTime' && f.name !== 'updateTime'
        ).sort(compareFormFields);

        for (const f of pageFields) {
            const jsType = this.mapToJsType(f.type ?? '');
            pageResponseProps[f.name] = { type: jsType, description: f.label, format: f.format };
        }

        for (const f of formFields) {
            const jsType = this.mapToJsType(f.type ?? '');
            const prop = {
                type: jsType,
                description: f.label,
                required: f.required || f.uiRequired,
                enum: f.allowedValues?.length ? f.allowedValues : undefined
            };
            createProps[f.name] = prop;
            updateProps[f.name] = prop;
        }

        const queryableFields = meta.queryableFields ??= {};
        meta.fields?.forEach(f => {
            if (f.searchable) {
                queryableFields[f.name] = {
                    type: f.type || 'String',
                    operators: opsForType(f.type || 'String'),
                    label: f.label,
                    order: f.searchOrder
                };
            }
        });

        meta.schemas.pageResponse = pageResponseProps;
        meta.schemas.create = createProps;
        meta.schemas.update = updateProps;

        // 4. Operations (填充默认操作)
        this.fillDefaultOperations(meta);
    }

    private fillDefaultOperations(meta: EntityMeta) {
        meta.operations ??= {};
        if (meta.listEnabled) meta.operations['search'] = {method: 'POST', path: 'search', summary: '分页搜索'};
        if (meta.getEnabled) meta.operations['get'] = {method: 'GET', path: '{id}', summary: '查询详情'};
        if (meta.createEnabled) meta.operations['create'] = {method: 'POST', path: '', summary: '创建'};
        if (meta.updateEnabled) meta.operations['update'] = {method: 'PUT', path: '{id}', summary: '更新'};
        if (meta.deleteEnabled) meta.operations['delete'] = {method: 'DELETE', path: '{id}', summary: '删除'};
        if (meta.deleteBatchEnabled) meta.operations['deleteBatch'] = {method: 'DELETE', path: 'batch', summary: '批量删除'};
        if (meta.updateBatchEnabled) meta.operations['updateBatch'] = {method: 'PUT', path: 'batch', summary: '批量更新'};
        if (meta.exportEnabled) meta.operations['export'] = {method: 'POST', path: 'export', summary: '导出'};
    }

    private mapToJsType(javaType: string): string {
        const t = (javaType || '').toLowerCase();
        if (t.includes('int') || t.includes('long') || t.includes('double') || t.includes('float') || t.includes('decimal') || t.includes('number')) {
            return 'number';
        }
        if (t.includes('bool')) {
            return 'boolean';
        }
        // Date, String, Enum -> string
        return 'string';
    }
}
