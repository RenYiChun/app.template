import {ActionMeta} from './meta.js';

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

export type EntityMeta = {
    name: string;
    displayName: string;
    pluralName: string;
    pathSegment: string;
    description?: string;
    properties: { [key: string]: any };
    operations?: { [key: string]: any };
    /** create/update: 表单；pageResponse: 列表项（表格列）；detail: 单条详情（GET by id） */
    schemas?: {
        create?: any;
        update?: any;
        pageResponse?: Record<string, any>;
        detail?: Record<string, any>;
        [key: string]: any
    };
    queryableFields?: Record<string, { type: string; operators: Op[]; label?: string; order?: number }>;
    actions?: ActionMeta[];
    exportEnabled?: boolean;
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
    // 可以根据需要添加其他状态，例如表单数据、编辑模式等
}