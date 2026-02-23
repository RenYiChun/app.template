import { ref, reactive, onMounted, onUnmounted, toRefs, computed, watch } from 'vue';
import { EntityCrudManager, CrudState, EntityClient, MetaService, EntityMeta } from '@lrenyi/dataforge-headless';
import { useDataforge } from '@lrenyi/dataforge-headless/vue';
import { getEntityConfig, resolveColumns, ColumnConfig } from '../../../template-dataforge-headless/src/vue/config.js';

export function useEntityCrud<T extends { id: string | number }>(entityName: string) {
  const { client, meta } = useDataforge();

  const entityCrudManager = new EntityCrudManager<T>(entityName, client as EntityClient, meta as MetaService);

  const crudState = reactive<CrudState<T> & {
    entityMeta: EntityMeta | null;
    metaLoading: boolean;
    allColumns: ColumnConfig[];
    visibleColumnProps: string[];
    displayColumns: ColumnConfig[];
  }>({
    items: [],
    pagedResult: null,
    meta: null, // This will be replaced by entityMeta
    filters: [],
    sort: [],
    page: 0,
    size: 10,
    selectedIds: [],
    loading: false,
    error: null,
    entityMeta: null,
    metaLoading: false,
    allColumns: [],
    visibleColumnProps: [],
    displayColumns: [],
  });

  let unsubscribe: (() => void) | undefined;

  const refreshMeta = async () => {
    crudState.metaLoading = true;
    try {
      crudState.entityMeta = await entityCrudManager.meta.getEntity(entityName);
    } catch (e) {
      console.error(`[useEntityCrud] Failed to load entity meta for ${entityName}:`, e);
      crudState.entityMeta = null;
    } finally {
      crudState.metaLoading = false;
    }
  };

  const setVisibleColumnProps = (props: string[]) => {
    crudState.visibleColumnProps = props;
  };

  // Computed properties for columns
  const allColumns = computed<ColumnConfig[]>(() => {
    // Use props.columns if provided, otherwise resolve from meta
    // For now, we'll assume props.columns is handled by the consuming component
    // and use resolveColumns here.
    return resolveColumns(entityName, crudState.entityMeta) || [];
  });

  const displayColumns = computed<ColumnConfig[]>(() => {
    if (!allColumns.value) return [];
    return allColumns.value.filter(col => crudState.visibleColumnProps.includes(col.prop));
  });

  watch(allColumns, (newVal) => {
    if (newVal && newVal.length > 0) {
      crudState.visibleColumnProps = newVal.map(col => col.prop);
    }
  }, { immediate: true });

  watch(() => crudState.entityMeta, (newVal) => {
    if (newVal) {
      // Update allColumns when entityMeta changes
      crudState.allColumns = allColumns.value;
      crudState.displayColumns = displayColumns.value;
    }
  });


  onMounted(() => {
    unsubscribe = entityCrudManager.subscribe((newState) => {
      Object.assign(crudState, newState);
    });
    refreshMeta(); // Load meta on mount
    entityCrudManager.init();
    entityCrudManager.search();
  });

  onUnmounted(() => {
    unsubscribe?.();
  });

  return {
    ...toRefs(crudState),
    setFilters: entityCrudManager.setFilters.bind(entityCrudManager),
    setPage: entityCrudManager.setPage.bind(entityCrudManager),
    setSize: entityCrudManager.setSize.bind(entityCrudManager),
    setSelectedIds: entityCrudManager.setSelectedIds.bind(entityCrudManager),
    delete: entityCrudManager.delete.bind(entityCrudManager),
    search: entityCrudManager.search.bind(entityCrudManager),
    init: entityCrudManager.init.bind(entityCrudManager),
    exportExcel: entityCrudManager.exportExcel.bind(entityCrudManager),
    resetFilters: entityCrudManager.resetFilters.bind(entityCrudManager),
    refreshMeta, // Expose refreshMeta
    setVisibleColumnProps, // Expose setVisibleColumnProps
    allColumns: computed(() => crudState.allColumns), // Expose as computed ref
    displayColumns: computed(() => crudState.displayColumns), // Expose as computed ref
  };
}
