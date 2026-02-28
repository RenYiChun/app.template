import {CrudState, FilterCondition, SearchRequest} from './types.js';
import {EntityClient} from './client.js';
import {MetaService} from './meta.js';

type StateChangeListener<T> = (state: CrudState<T>) => void;

export class EntityCrudManager<T extends { id: string | number }> {
    private subscribers: StateChangeListener<T>[] = [];
    private entityName: string;
    private entityClient: EntityClient;
    private metaService: MetaService;

    constructor(entityName: string, entityClient: EntityClient, metaService: MetaService) {
        this.entityName = entityName;
        this.entityClient = entityClient;
        this.metaService = metaService;

        this._state = {
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
        };
    }

    private _state: CrudState<T>;

    get state(): CrudState<T> {
        return this._state;
    }

    public get meta(): MetaService {
        return this.metaService;
    }

    subscribe(listener: StateChangeListener<T>): () => void {
        this.subscribers.push(listener);
        listener(this._state); // Immediately notify with current state
        return () => {
            this.subscribers = this.subscribers.filter((sub) => sub !== listener);
        };
    }

    async init(): Promise<void> {
        this.setState({loading: true, error: null});
        try {
            const meta = await this.metaService.getEntity(this.entityName);
            this.setState({meta, loading: false});
        } catch (error) {
            this.setState({error, loading: false});
            console.error('Failed to load entity meta:', error);
        }
    }

    async search(): Promise<void> {
        this.setState({loading: true, error: null, selectedIds: []});
        try {
            const searchRequest: SearchRequest = {
                filters: this._state.filters,
                sort: this._state.sort,
                page: this._state.page,
                size: this._state.size,
            };
            const pagedResult = await this.entityClient.search<T>(this.entityName, searchRequest);
            this.setState({
                items: pagedResult.content,
                pagedResult: pagedResult,
                loading: false,
            });
        } catch (error) {
            this.setState({error, loading: false});
            console.error('Failed to search entities:', error);
        }
    }

    /** 仅更新状态，不自动 search；由 UI 在合适时机调用 search()，避免一次操作多次请求 */
    setFilters(filters: FilterCondition[]): void {
        this.setState({filters, page: 0});
    }

    setPage(page: number): void {
        this.setState({page});
    }

    setSize(size: number): void {
        this.setState({size, page: 0});
    }

    setSelectedIds(selectedIds: (string | number)[]): void {
        this.setState({selectedIds});
    }

    async delete(): Promise<void> {
        if (this._state.selectedIds.length === 0) {
            console.warn('No items selected for deletion.');
            return;
        }

        this.setState({loading: true, error: null});
        try {
            await this.entityClient.deleteBatch(this.entityName, this._state.selectedIds);
            this.setState({selectedIds: []}); // Clear selected IDs after deletion
            await this.search(); // Refresh the list
        } catch (error) {
            this.setState({error, loading: false});
            console.error('Failed to delete entities:', error);
        }
    }

    async exportExcel(): Promise<Blob> {
        this.setState({loading: true, error: null});
        try {
            const searchRequest: SearchRequest = {
                filters: this._state.filters,
                sort: this._state.sort,
                page: 0, // Export all pages
                size: 10000, // Max size for export
            };
            const blob = await this.entityClient.export(this.entityName, searchRequest);
            this.setState({loading: false});
            return blob;
        } catch (error) {
            this.setState({error, loading: false});
            console.error('Failed to export entities:', error);
            throw error;
        }
    }

    resetFilters(): void {
        this.setState({filters: [], sort: [], page: 0});
        this.search();
    }

    private setState(newState: Partial<CrudState<T>>): void {
        this._state = {...this._state, ...newState};
        this.notifySubscribers();
    }

    private notifySubscribers(): void {
        this.subscribers.forEach((listener) => listener(this._state));
    }
}
