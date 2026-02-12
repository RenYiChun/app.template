/**
 * Generated from @Domain ${entity.simpleName}.
 * API methods for ${entity.displayName}.
 */
import axios from 'axios';
import type { ${entity.simpleName} } from '@/types/${entity.simpleName?lower_case}';

/**
 * 分页查询${entity.displayName}列表。
 */
export function list(page: number, size: number) {
  return axios.get<${entity.simpleName}[]>('/api/${entity.simpleName?lower_case}', {
    params: { page, size }
  });
}

/**
 * 根据 ID 查询${entity.displayName}。
 */
export function get(id: number) {
  return axios.get<${entity.simpleName}>(`/api/${entity.simpleName?lower_case}/${'$'}{id}`);
}

/**
 * 新增${entity.displayName}。
 */
export function create(data: Partial<${entity.simpleName}>) {
  return axios.post<${entity.simpleName}>('/api/${entity.simpleName?lower_case}', data);
}

/**
 * 更新${entity.displayName}。
 */
export function update(id: number, data: Partial<${entity.simpleName}>) {
  return axios.put<${entity.simpleName}>(`/api/${entity.simpleName?lower_case}/${'$'}{id}`, data);
}

/**
 * 删除${entity.displayName}。
 */
export function remove(id: number) {
  return axios.delete(`/api/${entity.simpleName?lower_case}/${'$'}{id}`);
}
