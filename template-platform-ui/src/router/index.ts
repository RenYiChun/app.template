import { createRouter, createWebHistory } from 'vue-router';
import { useAuth } from '@lrenyi/template-platform-frontend/vue';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'Login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
    { path: '/', name: 'Home', component: () => import('../views/HomeView.vue') },
  ],
});

router.beforeEach(async (to, _from, next) => {
  const { user, refreshMe } = useAuth();
  if (to.meta.public) {
    next();
    return;
  }
  if (!user.value) {
    try {
      await refreshMe();
    } catch {
      // ignore
    }
  }
  if (!user.value) {
    next({ path: '/login', query: { redirect: to.fullPath } });
  } else {
    next();
  }
});

export default router;
