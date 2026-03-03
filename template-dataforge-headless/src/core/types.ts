export interface ActionMeta {
    actionName: string;
    summary?: string;
    permissions?: string[];

    [key: string]: any;
}

/** 操作元数据（EntityMeta.operations 中每项的类型） */
export interface OperationMeta {
    method?: string;
    path?: string;
    summary?: string;
    [key: string]: any;
}

export type Result<T> = {
    code: number;
    message: string;
    data: T;
};

export type PagedResult<T> = {
    content: T[];
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
};

export type SearchRequest = {
    filters?: FilterCondition[];
    sort?: SortOrder[];
    page?: number;
    size?: number;
};

export type FilterCondition = {
    field: string;
    op: Op;
    value: any;
};

export type SortOrder = {
    field: string;
    direction: 'asc' | 'desc';
};

export enum Op {
    EQ = 'EQ', // 等于
    NE = 'NE', // 不等于
    GT = 'GT', // 大于
    GE = 'GE', // 大于等于
    LT = 'LT', // 小于
    LE = 'LE', // 小于等于
    LIKE = 'LIKE', // 模糊匹配
    NLIKE = 'NLIKE', // 不模糊匹配
    IN = 'IN', // 包含
    NIN = 'NIN', // 不包含
    IS_NULL = 'IS_NULL', // 为空
    IS_NOT_NULL = 'IS_NOT_NULL', // 不为空
    BETWEEN = 'BETWEEN', // 在两者之间
    NBETWEEN = 'NBETWEEN', // 不在两者之间
}

export interface StorageProvider {
    getItem(key: string): string | null;

    setItem(key: string, value: string): void;

    removeItem(key: string): void;
}

export type MetadataSource =
    | { type: 'remote'; url?: string }
    | { type: 'local'; data: any[] };

export interface ServiceConfig {
    name: string;
    baseUrl: string;
    default?: boolean;
    metadata?: MetadataSource;
}

export interface FieldMeta {
    name: string;
    label: string;
    type: string;
    columnName?: string;
    primaryKey?: boolean;
    required?: boolean;
    nullable?: boolean;
    queryable?: boolean;
    searchOrder?: number;
    exportExcluded?: boolean;
    description?: string;
    columnOrder?: number;
    formOrder?: number;
    group?: string;
    groupOrder?: number;
    columnVisible?: boolean;
    columnResizable?: boolean;
    columnSortable?: boolean;
    columnFilterable?: boolean;
    columnAlign?: 'LEFT' | 'CENTER' | 'RIGHT';
    columnWidth?: number;
    columnMinWidth?: number;
    columnFixed?: 'LEFT' | 'RIGHT' | 'NONE';
    component?: string;
    placeholder?: string;
    tips?: string;
    uiRequired?: boolean;
    readonly?: boolean;
    disabled?: boolean;
    hidden?: boolean;
    regex?: string;
    regexMessage?: string;
    minLength?: number;
    maxLength?: number;
    minValue?: number;
    maxValue?: number;
    dictCode?: string;
    enumOptions?: string[];
    enumLabels?: string[];
    searchable?: boolean;
    searchType?: string;
    searchComponent?: string;
    searchDefaultValue?: string;
    format?: string;
    foreignKey?: boolean;
    referencedEntity?: string;
    referencedField?: string;
    displayField?: string;
    valueField?: string;

    [key: string]: any;
}

export type EntityMeta = {
    // 基础信息
    entityName: string;
    name: string; // alias for entityName for compatibility
    displayName: string;
    pluralName?: string;
    pathSegment: string;
    tableName?: string;
    description?: string;

    // 字段列表 (核心)
    fields: FieldMeta[];

    // 功能开关
    crudEnabled?: boolean;
    listEnabled?: boolean;
    getEnabled?: boolean;
    createEnabled?: boolean;
    updateEnabled?: boolean;
    deleteEnabled?: boolean;
    updateBatchEnabled?: boolean;
    deleteBatchEnabled?: boolean;
    exportEnabled?: boolean;
    exportTemplate?: string;

    // DTO Info
    dtoCreate?: string;
    dtoUpdate?: string;
    dtoResponse?: string;
    dtoPageResponse?: string;

    // 服务标识 (前端注入)
    serviceName?: string;

    // 兼容旧版 UI 的字段 (由 MetaService 转换生成)
    properties?: { [key: string]: any };
    operations?: { [key: string]: any };
    schemas?: {
        create?: any;
        update?: any;
        pageResponse?: Record<string, any>;
        detail?: Record<string, any>;
        [key: string]: any
    };
    queryableFields?: Record<string, { type: string; operators: Op[]; label?: string; order?: number }>;
    actions?: ActionMeta[];

    [key: string]: any;
};

export const SUCCESS_CODE = 200;

export interface CrudState<T> {
    items: T[];
    pagedResult: PagedResult<T> | null;
    meta: EntityMeta | null;
    filters: FilterCondition[];
    sort: SortOrder[];
    page: number;
    size: number;
    selectedIds: (string | number)[];
    loading: boolean;
    error: unknown;
}
