/**
 * template-platform 前端 Vue 层
 */

export { EntityClient, MetaService } from '../core/index.js';
export type {
  EntityMeta,
  SearchRequest,
  FilterCondition,
  SortOrder,
  PagedResult,
  Result,
} from '../core/index.js';
export { createPlatform, getPlatform } from './createPlatform.js';
export { useEntityCrud } from './composables/useEntityCrud.js';
export { useEntityMeta } from './composables/useEntityMeta.js';
export {
  registerEntityConfig,
  getEntityConfig,
  resolveColumns,
} from './config.js';
export { default as EntityTable } from './components/EntityTable.vue';
export { default as EntitySearchBar } from './components/EntitySearchBar.vue';
export { default as EntityForm } from './components/EntityForm.vue';
export { default as EntityCrudPage } from './components/EntityCrudPage.vue';
export type { EntityConfig, ColumnConfig } from './config.js';
export type { UseEntityCrudOptions } from './composables/useEntityCrud.js';
