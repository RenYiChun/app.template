/**
 * template-dataforge 前端 Core 层
 * 框架无关，可在任意前端项目中使用
 */

export {AssociationCache} from './associationCache.js';
export type {AssociationCacheClient, AssociationCacheOptions} from './associationCache.js';
export {EntityCrudManager} from './EntityCrudManager.js';
export type {
    ActionMeta,
    CrudState,
    EntityMeta,
    EntityUiLayoutMeta,
    MasterDetailTreeMeta,
    OperationMeta,
} from './types.js';
export {AuthClient} from './authClient.js';
export type {AuthClientConfig, LoginRequest, CaptchaResult, AuthUser} from './authClient.js';
export {EntityClient} from './client.js';
export type {EntityClientConfig} from './client.js';
export {MetaService} from './meta.js';
export type {MetaServiceConfig, SchemaProperty} from './meta.js';
export type {
    Result,
    PagedResult,
    SearchRequest,
    FilterCondition,
    SortOrder,
    StorageProvider,
    EntityOption,
    TreeNode,
} from './types.js';
export {
    Op,
    SUCCESS_CODE,
    DATAFORGE_ERROR_CODES,
    DATAFORGE_ERROR_MESSAGES,
    getDataforgeErrorMessage,
} from './types.js';
export * from './utils.js';
export * from './errors.js';
