import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'Login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
    {
      path: '/',
      name: 'Home',
      component: () => import('../views/HomeView.vue'),
      children: [
        {
          path: '',
          redirect: '/system/users',
        },
        {
          path: 'system/users',
          name: 'UserManagement',
          component: () => import('../views/system/UserList.vue'),
        },
        {
          path: 'system/roles',
          name: 'RoleManagement',
          component: () => import('../views/system/RoleList.vue'),
        },
        {
          path: 'system/departments',
          name: 'DepartmentManagement',
          component: () => import('../views/system/DeptList.vue'),
        },
        {
          path: 'system/dicts',
          name: 'DictionaryManagement',
          component: () => import('../views/system/DictList.vue'),
        },
        {
          path: 'system/operation-logs',
          name: 'OperationLogManagement',
          component: () => import('../views/system/OperationLogList.vue'),
          meta: { public: true },
        },
        {
          path: 'system/metadata',
          name: 'MetadataViewer',
          component: () => import('../views/system/MetadataList.vue'),
        },
      ]
    },
  ],
});

router.beforeEach(async (to, _from, next) => {
  const authStore = useAuthStore();
  
  if (to.meta.public || to.path === '/login') {
    next();
    return;
  }
  
  if (!authStore.user) {
    try {
      await authStore.refreshMe();
    } catch {
      // ignore
    }
  }
  
  if (!authStore.user) {
    next({ path: '/login', query: { redirect: to.fullPath } });
  } else {
    next();
  }
});

export default router;
