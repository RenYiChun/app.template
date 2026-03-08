import {computed, onMounted, onUnmounted, reactive, toRefs, watch} from 'vue';
import {CrudState, EntityCrudManager, EntityMeta} from '@lrenyi/dataforge-headless';
import {ColumnConfig, resolveColumns, useDataforge} from '@lrenyi/dataforge-headless/vue';

export function useEntityCrud<T extends { id: string | number }>(entityName: string) {
    const dataforge = useDataforge();
    const {client, meta} = dataforge;

    const entityCrudManager = new EntityCrudManager<T>(entityName, client, meta);

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

    // 列配置：仅由后端 meta 解析（manager.init() 写入 crudState.meta），resolveColumns 以 meta 为主、config 仅覆盖
    const allColumns = computed<ColumnConfig[]>(() => {
        const meta = crudState.meta ?? crudState.entityMeta;
        return resolveColumns(entityName, meta) || [];
    });

    const displayColumns = computed<ColumnConfig[]>(() => {
        if (!allColumns.value) return [];
        return allColumns.value.filter(col => crudState.visibleColumnProps.includes(col.prop));
    });

    /** 元数据中是否暴露了批量删除：有则显示多选与批量删除按钮 */
    const canBatchDelete = computed(() => {
        const meta = crudState.meta ?? crudState.entityMeta;
        return !!meta?.operations?.['deleteBatch'];
    });
    /** 元数据中是否暴露了批量更新：有则可显示多选与批量更新入口 */
    const canBatchUpdate = computed(() => {
        const meta = crudState.meta ?? crudState.entityMeta;
        return !!meta?.operations?.['updateBatch'];
    });
    /** 是否启用表格多选（批量删除或批量更新任一存在即启用） */
    const selectable = computed(() => canBatchDelete.value || canBatchUpdate.value);

    watch(allColumns, (newVal) => {
        if (newVal && newVal.length > 0) {
            crudState.visibleColumnProps = newVal.map(col => col.prop);
        }
    }, {immediate: true});

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
        // 注册列表刷新，业务在创建/更新/删除成功后调用 dataforge.refreshCrud(entityName) 即可更新表格
        dataforge.registerCrudRefresh(entityName, entityCrudManager.search.bind(entityCrudManager));
    });

    onUnmounted(() => {
        dataforge.unregisterCrudRefresh(entityName);
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
        refreshMeta,
        setVisibleColumnProps,
        allColumns,
        displayColumns,
        canBatchDelete,
        canBatchUpdate,
        selectable,
    };
}
