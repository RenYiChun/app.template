/**
 * Vue Router 配置：使用 Hash 模式以兼容通过 Spring Boot 提供静态资源时的 SPA 路由。
 */
import { createRouter, createWebHashHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import generatedRoutes from './generated'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/Home.vue'),
    meta: { title: '首页' }
  },
  ...generatedRoutes
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

export default router
