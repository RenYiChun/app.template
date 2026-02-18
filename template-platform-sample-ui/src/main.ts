import { createApp } from 'vue';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import './styles/main.css';
import './styles/entity-crud.css';
import App from './App.vue';
import router from './router';
import { createPlatform } from '@lrenyi/platform-headless/vue';

createPlatform({
  client: { baseURL: '', apiPrefix: '/api' },
  auth: {
    onUnauthorized: () => {
      router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } });
    },
  },
});

const app = createApp(App);
app.use(ElementPlus);
app.use(router);
app.mount('#app');
