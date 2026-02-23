/**
 * template-dataforge 前端 Core 层
 * 框架无关，可在任意前端项目中使用
 */

export { EntityCrudManager } from './EntityCrudManager.js';
export type { CrudState, EntityMeta } from './types.js';
export { AuthClient } from './authClient.js';
export type { AuthClientConfig, LoginRequest, CaptchaResult, AuthUser } from './authClient.js';
export { EntityClient } from './client.js';
export type { EntityClientConfig } from './client.js';
export { MetaService } from './meta.js';
export type {
  MetaServiceConfig,
  OperationMeta,
  ActionMeta,
  SchemaProperty,
} from './meta.js';
export type {
  Result,
  PagedResult,
  SearchRequest,
  FilterCondition,
  SortOrder,
  StorageProvider,
} from './types.js';
export { Op } from './types.js';
export { SUCCESS_CODE } from './types.js';
export * from './utils.js';
export * from './errors.js';
