/**
 * Generated routes for entities and pages.
 * Import this in your main router configuration.
 */
import type { RouteRecordRaw } from 'vue-router';

const generatedRoutes: RouteRecordRaw[] = [
<#list entities as entity>
  // ${entity.displayName} 路由
  {
    path: '/${entity.simpleName?lower_case}',
    name: '${entity.simpleName}List',
    component: () => import('@/views/${entity.simpleName}List.vue'),
    meta: { title: '${entity.displayName}列表' }
  },
  {
    path: '/${entity.simpleName?lower_case}/create',
    name: '${entity.simpleName}Create',
    component: () => import('@/views/${entity.simpleName}Form.vue'),
    meta: { title: '新增${entity.displayName}' }
  },
  {
    path: '/${entity.simpleName?lower_case}/edit/:id',
    name: '${entity.simpleName}Edit',
    component: () => import('@/views/${entity.simpleName}Form.vue'),
    meta: { title: '编辑${entity.displayName}' }
  },
  {
    path: '/${entity.simpleName?lower_case}/:id',
    name: '${entity.simpleName}Detail',
    component: () => import('@/views/${entity.simpleName}Detail.vue'),
    meta: { title: '${entity.displayName}详情' }
  },
</#list>
<#list pages as page>
  // ${page.title} 路由
  {
    path: '${page.path}',
    name: '${page.simpleName}',
    component: () => import('@/views/${page.simpleName}.vue'),
    meta: { title: '${page.title}' }
  },
</#list>
];

export default generatedRoutes;
