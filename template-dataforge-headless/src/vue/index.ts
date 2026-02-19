/**
 * template-platform 前端 Vue 层
 */

export { AuthClient, EntityClient, MetaService, PlatformError, NetworkError, HttpError, BusinessError, AuthError } from '../core/index.js';
export type {
  AuthClientConfig,
  EntityClientConfig,
  LoginRequest,
  CaptchaResult,
  AuthUser,
  EntityMeta,
  SearchRequest,
  FilterCondition,
  SortOrder,
  PagedResult,
  Result,
  StorageProvider,
} from '../core/index.js';
export { createPlatform, getPlatform, usePlatform } from './createPlatform.js';
export { useAuth } from './composables/useAuth.js';
export { useEntityCrud } from './composables/useEntityCrud.js';
export { useEntityMeta } from './composables/useEntityMeta.js';
export {
  registerEntityConfig,
  getEntityConfig,
  resolveColumns,
} from './config.js';
export type { EntityConfig, ColumnConfig } from './config.js';
export type { UseEntityCrudOptions } from './composables/useEntityCrud.js';
