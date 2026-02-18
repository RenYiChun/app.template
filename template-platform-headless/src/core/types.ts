/**
 * 与 template-platform 后端一一对应的类型定义
 */

/** 过滤操作符 */
export type Op = 'eq' | 'ne' | 'like' | 'gt' | 'gte' | 'lt' | 'lte' | 'in';

/** 单条过滤条件 */
export interface FilterCondition {
  field: string;
  op: Op;
  value: unknown;
}

/** 排序项 */
export interface SortOrder {
  field: string;
  dir: 'asc' | 'desc';
}

/** 搜索/导出请求体 */
export interface SearchRequest {
  filters?: FilterCondition[];
  sort?: SortOrder[];
  page?: number;
  size?: number;
}

/** 分页结果 */
export interface PagedResult<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** 后端统一响应包装 */
export interface Result<T> {
  code: number;
  data: T | null;
  message?: string;
}

/** 成功状态码 */
export const SUCCESS_CODE = 0;
