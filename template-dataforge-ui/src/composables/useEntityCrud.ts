import {computed, onMounted, onUnmounted, reactive, ref, toRefs, watch} from 'vue';
import {
    CrudState,
    EntityCrudManager,
    EntityMeta,
    FilterCondition,
    Op,
    SortOrder,
} from '@lrenyi/dataforge-headless';
import {ColumnConfig, resolveColumns, useDataforge} from '@lrenyi/dataforge-headless/vue';

/** 与 headless MasterDetailTreeMeta 一致，避免强依赖导出 */
type MasterDetailTreeMeta = NonNullable<NonNullable<EntityMeta['uiLayout']>['masterDetailTree']>;

/** 树节点（用于 el-tree）：id、label、children、原始数据 */
export interface TreeNode {
    id: string | number;
    label: string;
    children?: TreeNode[];
    raw?: Record<string, unknown>;
}

function buildTree(
    flat: Record<string, unknown>[],
    idField: string,
    parentField: string,
    labelField: string
): TreeNode[] {
    const idKey = idField || 'id';
    const parentKey = parentField || 'parentId';
    const labelKey = labelField || 'name';
    const map = new Map<string | number, TreeNode>();
    const roots: TreeNode[] = [];
    for (const item of flat) {
        const id = item[idKey] as string | number;
        const label = (item[labelKey] ?? id ?? '') as string;
        const node: TreeNode = { id, label, raw: item, children: [] };
        map.set(id, node);
    }
    for (const item of flat) {
        const id = item[idKey] as string | number;
        const parentId = item[parentKey] as string | number | null | undefined;
        const node = map.get(id)!;
        if (parentId === null || parentId === undefined || parentId === '') {
            roots.push(node);
        } else {
            const parent = map.get(parentId);
            if (parent) {
                if (!parent.children) parent.children = [];
                parent.children.push(node);
            } else {
                roots.push(node);
            }
        }
    }
    return roots;
}

function collectDescendantIds(node: TreeNode): (string | number)[] {
    const ids: (string | number)[] = [node.id];
    if (node.children?.length) {
        for (const c of node.children) {
            ids.push(...collectDescendantIds(c));
        }
    }
    return ids;
}

export function useEntityCrud<T extends { id: string | number }>(entityName: string) {
    const dataforge = useDataforge();
    const {client, meta} = dataforge;

    const entityCrudManager = new EntityCrudManager<T>(entityName, client, meta);

    const treeData = ref<TreeNode[]>([]);
    const treeLoading = ref(false);
    const treeSelectedKey = ref<string | number | null>(null);
    /** 树表模式下由树选择产生的过滤条件 */
    const treeFilter = ref<FilterCondition | null>(null);
    /** 树表模式下用户搜索表单的过滤条件，与 treeFilter 合并后传给 manager */
    const userFilters = ref<FilterCondition[]>([]);

    const crudState = reactive<CrudState<T> & {
        entityMeta: EntityMeta | null;
        metaLoading: boolean;
        allColumns: ColumnConfig[];
        visibleColumnProps: string[];
        displayColumns: ColumnConfig[];
    }>({
        items: [],
        pagedResult: null,
        meta: null,
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

    const uiLayout = computed(() => crudState.entityMeta?.uiLayout);
    const isMasterDetailTree = computed(
        () => (uiLayout.value?.mode === 'masterDetailTree' && !!uiLayout.value?.masterDetailTree)
    );
    const masterDetailTreeConfig = computed<MasterDetailTreeMeta | null>(() =>
        isMasterDetailTree.value ? (uiLayout.value!.masterDetailTree ?? null) : null
    );

    /** rootSelectionMode=none 时未选树节点则不允许查询/导出/分页请求右表数据 */
    const listBlockedByTreeSelection = computed(() => {
        const mdt = masterDetailTreeConfig.value;
        return !!(
            mdt?.rootSelectionMode === 'none' &&
            (treeSelectedKey.value === null || treeSelectedKey.value === undefined)
        );
    });

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

    const allColumns = computed<ColumnConfig[]>(() => {
        const meta = crudState.meta ?? crudState.entityMeta;
        return resolveColumns(entityName, meta) || [];
    });

    const displayColumns = computed<ColumnConfig[]>(() => {
        if (!allColumns.value) return [];
        return allColumns.value.filter(col => crudState.visibleColumnProps.includes(col.prop));
    });

    const canBatchDelete = computed(() => {
        const meta = crudState.meta ?? crudState.entityMeta;
        return !!meta?.operations?.['deleteBatch'];
    });
    const canBatchUpdate = computed(() => {
        const meta = crudState.meta ?? crudState.entityMeta;
        return !!meta?.operations?.['updateBatch'];
    });
    const selectable = computed(() => canBatchDelete.value || canBatchUpdate.value);

    function mergeFiltersWithTree(formFilters: FilterCondition[]): FilterCondition[] {
        const mdt = masterDetailTreeConfig.value;
        if (!mdt || !treeFilter.value) return formFilters;
        const relationField = mdt.relationField || '';
        const withoutRelation = formFilters.filter(f => f.field !== relationField);
        return [...withoutRelation, treeFilter.value];
    }

    function setFilters(filters: FilterCondition[]) {
        userFilters.value = filters ?? [];
        const merged = mergeFiltersWithTree(userFilters.value);
        entityCrudManager.setFilters(merged);
    }

    function applyTreeSelection(node: TreeNode | null, triggerSearch = true) {
        treeSelectedKey.value = node?.id ?? null;
        const mdt = masterDetailTreeConfig.value;
        if (!mdt || !node) {
            treeFilter.value = null;
        } else {
            const relationField = mdt.relationField || 'id';
            if (mdt.includeDescendants) {
                const ids = collectDescendantIds(node);
                treeFilter.value = {field: relationField, op: Op.IN, value: ids};
            } else {
                treeFilter.value = {field: relationField, op: Op.EQ, value: node.id};
            }
        }
        const merged = mergeFiltersWithTree(userFilters.value);
        entityCrudManager.setFilters(merged);
        if (triggerSearch) {
            entityCrudManager.search();
        }
    }

    async function loadTreeData(autoSelectRoot = false, triggerSearchOnAutoSelect = false) {
        const mdt = masterDetailTreeConfig.value;
        if (!mdt?.treeEntity) return false;
        treeLoading.value = true;
        try {
            const sort = mdt.treeSortField
                ? [{field: mdt.treeSortField, direction: 'asc' as const}]
                : [];
            const pageSize = 2000;
            let page = 0;
            let allContent: Record<string, unknown>[] = [];
            let totalElements = 0;
            do {
                const res = await client.search<Record<string, unknown>>(mdt.treeEntity, {
                    filters: [],
                    sort,
                    page,
                    size: pageSize,
                });
                const content = res?.content ?? [];
                totalElements = res?.totalElements ?? 0;
                allContent = allContent.concat(content);
                if (content.length < pageSize || allContent.length >= totalElements) {
                    break;
                }
                page += 1;
            } while (allContent.length < totalElements);

            if (allContent.length < totalElements) {
                console.warn(
                    `[useEntityCrud] Tree data may be truncated: got ${allContent.length}, total ${totalElements}. includeDescendants may be wrong.`
                );
            }
            const idField = mdt.treeIdField || 'id';
            const parentField = mdt.treeParentField || 'parentId';
            const labelField = mdt.treeLabelField || 'name';
            treeData.value = buildTree(allContent, idField, parentField, labelField);

            if (autoSelectRoot && treeSelectedKey.value == null && treeData.value.length > 0) {
                applyTreeSelection(treeData.value[0], triggerSearchOnAutoSelect);
                return true;
            }
            return false;
        } catch (e) {
            console.error('[useEntityCrud] Failed to load tree data:', e);
            treeData.value = [];
            return false;
        } finally {
            treeLoading.value = false;
        }
    }

    function onTreeSelect(node: TreeNode | null) {
        applyTreeSelection(node, true);
    }

    function resetFilters() {
        userFilters.value = [];
        (entityCrudManager as EntityCrudManager<T> & { setSort(sort: SortOrder[]): void }).setSort([]);
        entityCrudManager.setPage(0);
        if (listBlockedByTreeSelection.value) {
            entityCrudManager.setFilters([]);
            return;
        }
        const merged = mergeFiltersWithTree([]);
        entityCrudManager.setFilters(merged);
        entityCrudManager.search();
    }

    async function search() {
        if (listBlockedByTreeSelection.value) return;
        return entityCrudManager.search();
    }

    async function exportExcel(): Promise<Blob> {
        if (listBlockedByTreeSelection.value) {
            return Promise.reject(new Error('请先选择左侧树节点后再导出'));
        }
        return entityCrudManager.exportExcel();
    }

    watch(allColumns, newVal => {
        if (newVal?.length) crudState.visibleColumnProps = newVal.map(c => c.prop);
    }, {immediate: true});

    watch(() => crudState.entityMeta, newVal => {
        if (newVal) {
            crudState.allColumns = allColumns.value;
            crudState.displayColumns = displayColumns.value;
        }
    });

    onMounted(() => {
        unsubscribe = entityCrudManager.subscribe(newState => {
            Object.assign(crudState, newState);
        });
        refreshMeta().then(async () => {
            await entityCrudManager.init();
            if (isMasterDetailTree.value) {
                const mdt = masterDetailTreeConfig.value;
                if (mdt?.rootSelectionMode === 'none') {
                    await loadTreeData(false, false);
                    return;
                }
                const autoSelected = await loadTreeData(true, true);
                if (autoSelected) {
                    return;
                }
            }
            entityCrudManager.search();
        });
        // 注册列表刷新，业务在创建/更新/删除成功后调用 dataforge.refreshCrud(entityName) 即可更新表格
        dataforge.registerCrudRefresh(entityName, entityCrudManager.search.bind(entityCrudManager));
    });

    onUnmounted(() => {
        dataforge.unregisterCrudRefresh(entityName);
        unsubscribe?.();
    });

    return {
        ...toRefs(crudState),
        setFilters,
        setPage: entityCrudManager.setPage.bind(entityCrudManager),
        setSize: entityCrudManager.setSize.bind(entityCrudManager),
        setSelectedIds: entityCrudManager.setSelectedIds.bind(entityCrudManager),
        delete: entityCrudManager.delete.bind(entityCrudManager),
        search,
        init: entityCrudManager.init.bind(entityCrudManager),
        exportExcel,
        resetFilters,
        refreshMeta,
        setVisibleColumnProps,
        allColumns,
        displayColumns,
        canBatchDelete,
        canBatchUpdate,
        selectable,
        isMasterDetailTree,
        masterDetailTreeConfig,
        treeData,
        treeLoading,
        treeSelectedKey,
        onTreeSelect,
        listBlockedByTreeSelection,
    };
}
