export { default as EntityTable } from './components/EntityTable.vue';
export { default as EntitySearchBar } from './components/EntitySearchBar.vue';
export { default as EntityForm } from './components/EntityForm.vue';
export { default as EntityCrudPage } from './components/EntityCrudPage.vue';
export { default as EntityMetadataPage } from './components/EntityMetadataPage.vue';

export {
  registerEntityConfig,
  getEntityConfig,
  resolveColumns,
} from '@lrenyi/platform-headless/vue';

export type { EntityConfig, ColumnConfig } from '@lrenyi/platform-headless/vue';
