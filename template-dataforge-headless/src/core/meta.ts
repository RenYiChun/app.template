/**
 * MetaService：拉取并解析元数据（支持多服务聚合与本地离线模式）
 */

import {EntityMeta, MetaServiceConfig as IMetaServiceConfig, Op, ServiceConfig} from './types.js';
import type {EntityClient} from './client.js';
import {joinPath} from './utils.js';

export interface MetaServiceConfig {
    /** 缓存时间（毫秒），0 表示不缓存 */
    cacheTtlMs?: number;
    /** 服务列表配置 */
    services?: ServiceConfig[];
}

const STRING_OPS: Op[] = [Op.EQ, Op.NE, Op.LIKE];
const NUM_OPS: Op[] = [Op.EQ, Op.NE, Op.GT, Op.GE, Op.LT, Op.LE, Op.IN];
const BOOL_OPS: Op[] = [Op.EQ, Op.NE];
const DATE_OPS: Op[] = [Op.EQ, Op.GT, Op.GE, Op.LT, Op.LE];

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
        if (this.cached && this.cacheTtlMs > 0 && Date.now() - this.cachedAt < this.cacheTtlMs) {
            return this.cached;
        }

        const allEntities: EntityMeta[] = [];
        const serviceMap: Record<string, string> = {};

        // 如果没有配置 services，尝试使用默认行为（单体后端）
        const servicesToFetch = this.services.length > 0 ? this.services : [{
            name: 'default',
            baseUrl: '',
            default: true,
            metadata: {type: 'remote'}
        } as ServiceConfig];

        for (const service of servicesToFetch) {
            try {
                let entities: EntityMeta[] = [];
                if (service.metadata?.type === 'local') {
                    // 本地离线数据
                    entities = (service.metadata.data as any[]) || [];
                } else {
                    // 远程获取
                    // 构造 URL，绕过 client.url() 的自动路由（因为此时 map 未建立）
                    let url = 'metadata/entities';
                    if (service.baseUrl) {
                        if (service.baseUrl.startsWith('http')) {
                            // 绝对路径
                            url = joinPath(service.baseUrl, 'metadata/entities');
                        } else {
                            // 相对路径，client.request 会自动处理 prefix
                            // 但为了避免 client.apiPrefix 干扰，如果 service.baseUrl 已经包含 prefix，
                            // 我们可能需要小心。
                            // 假设 service.baseUrl 是完整的 path prefix (e.g. /api/system)
                            // 而 client.baseURL 是 host (e.g. http://localhost:8080)
                            // client.apiPrefix 是默认 prefix (e.g. /api)
                            
                            // 如果 service.baseUrl 存在，我们直接用它作为 path
                            // Fix: 如果 service.baseUrl 与默认 apiPrefix 相同，则只传递 path 部分，避免重复拼接
                            // 简单的判断：如果 baseUrl 是相对路径，我们只取 metadata/entities，让 client 自动处理 prefix
                            if (service.baseUrl.startsWith('/')) {
                                url = 'metadata/entities';
                            } else {
                                url = joinPath(service.baseUrl, 'metadata/entities');
                            }
                        }
                    } else {
                        // 使用默认 apiPrefix
                        url = 'metadata/entities';
                    }

                    const res = await this.client.request(url, {method: 'GET'});
                    const json = await res.json();
                    // 兼容 Result<T> 包装
                    entities = Array.isArray(json) ? json : (json.data || []);
                }

                if (Array.isArray(entities)) {
                    entities.forEach(e => {
                        e.serviceName = service.name;
                        this.adaptCompatibility(e);
                        // 注册映射：pathSegment -> serviceName
                        serviceMap[e.pathSegment] = service.name;
                    });
                    allEntities.push(...entities);
                }
            } catch (e) {
                console.error(`[MetaService] Failed to fetch metadata for service ${service.name}:`, e);
            }
        }

        this.cached = allEntities;
        this.cachedAt = Date.now();

        // 注册到 Client，启用多服务路由
        this.client.registerServices(this.services);
        this.client.registerServiceMap(serviceMap);

        return allEntities;
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
        meta.schemas = meta.schemas || {};
        meta.queryableFields = meta.queryableFields || {};
        meta.operations = meta.operations || {};

        const pageResponseProps: Record<string, any> = {};
        const createProps: Record<string, any> = {};
        const updateProps: Record<string, any> = {};

        if (meta.fields) {
            meta.fields.forEach(f => {
                const jsType = this.mapToJsType(f.type);
                
                // 1. PageResponse (表格列)
                if (f.columnVisible !== false) {
                    pageResponseProps[f.name] = {
                        type: jsType,
                        description: f.label,
                        format: f.format
                    };
                }

                // 2. Create/Update Schema (表单)
                // 排除只读和系统字段
                if (!f.readonly && !f.dtoReadOnly && f.name !== 'id' && f.name !== 'createTime' && f.name !== 'updateTime') {
                    const prop = { 
                        type: jsType, 
                        description: f.label, 
                        required: f.required || f.uiRequired,
                        enum: f.allowedValues?.length ? f.allowedValues : undefined
                    };
                    createProps[f.name] = prop;
                    updateProps[f.name] = prop;
                }

                // 3. QueryableFields (搜索配置)
                if (f.searchable) {
                    meta.queryableFields![f.name] = {
                        type: f.type || 'String', // 使用原始类型以便 opsForType 识别日期等
                        operators: opsForType(f.type || 'String'),
                        label: f.label,
                        order: f.searchOrder
                    };
                }
            });
        }

        meta.schemas.pageResponse = pageResponseProps;
        meta.schemas.create = createProps;
        meta.schemas.update = updateProps;

        // 4. Operations (填充默认操作)
        if (meta.listEnabled) meta.operations['search'] = { method: 'POST', path: 'search', summary: '分页搜索' };
        if (meta.getEnabled) meta.operations['get'] = { method: 'GET', path: '{id}', summary: '查询详情' };
        if (meta.createEnabled) meta.operations['create'] = { method: 'POST', path: '', summary: '创建' };
        if (meta.updateEnabled) meta.operations['update'] = { method: 'PUT', path: '{id}', summary: '更新' };
        if (meta.deleteEnabled) meta.operations['delete'] = { method: 'DELETE', path: '{id}', summary: '删除' };
        if (meta.exportEnabled) meta.operations['export'] = { method: 'POST', path: 'export', summary: '导出' };
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
