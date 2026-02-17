/**
 * template-platform 前端 Core 层
 * 框架无关，可在任意前端项目中使用
 */

export { AuthClient } from './authClient.js';
export type { AuthClientConfig, LoginRequest, CaptchaResult, AuthUser } from './authClient.js';
export { EntityClient } from './client.js';
export type { EntityClientConfig } from './client.js';
export { MetaService } from './meta.js';
export type {
  MetaServiceConfig,
  EntityMeta,
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
  Op,
} from './types.js';
export { SUCCESS_CODE } from './types.js';
