/**
 * Generated routes for entities and pages.
 * Import this in your main router configuration.
 */
import type { RouteRecordRaw } from 'vue-router';

const generatedRoutes: RouteRecordRaw[] = [
  // 用户 路由
  {
    path: '/user',
    name: 'UserList',
    component: () => import('@/views/UserList.vue'),
    meta: { title: '用户列表' }
  },
  {
    path: '/user/create',
    name: 'UserCreate',
    component: () => import('@/views/UserForm.vue'),
    meta: { title: '新增用户' }
  },
  {
    path: '/user/edit/:id',
    name: 'UserEdit',
    component: () => import('@/views/UserForm.vue'),
    meta: { title: '编辑用户' }
  },
  {
    path: '/user/:id',
    name: 'UserDetail',
    component: () => import('@/views/UserDetail.vue'),
    meta: { title: '用户详情' }
  },
  // 登录页 路由
  {
    path: '/login',
    name: 'LoginPage',
    component: () => import('@/views/LoginPage.vue'),
    meta: { title: '登录页' }
  },
];

export default generatedRoutes;
