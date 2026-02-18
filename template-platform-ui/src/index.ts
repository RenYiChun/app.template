export { default as EntityTable } from './components/EntityTable.vue';
export { default as EntitySearchBar } from './components/EntitySearchBar.vue';
export { default as EntityForm } from './components/EntityForm.vue';
export { default as EntityCrudPage } from './components/EntityCrudPage.vue';

export {
  registerEntityConfig,
  getEntityConfig,
  resolveColumns,
} from './config.js';

export type { EntityConfig, ColumnConfig } from './config.js';
