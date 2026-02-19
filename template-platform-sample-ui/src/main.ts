import { createApp } from 'vue';
import ElementPlus, { ElMessage } from 'element-plus';
import 'element-plus/dist/index.css';
import '@lrenyi/platform-ui/dist/index.css';
import './styles/main.css';
import './styles/platform-ui-overrides.css';
import App from './App.vue';
import router from './router';
import { createPlatform, PlatformError, NetworkError, BusinessError } from '@lrenyi/platform-headless/vue';

const platform = createPlatform({
  client: { baseURL: '', apiPrefix: '/api' },
  auth: {
    onUnauthorized: () => {
      router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } });
    },
  },
});

const app = createApp(App);

// 全局错误处理
app.config.errorHandler = (err, instance, info) => {
  console.error('Global Error Handler:', err, info);
  if (err instanceof NetworkError) {
    ElMessage.error('网络连接失败，请检查您的网络设置');
  } else if (err instanceof BusinessError) {
    ElMessage.error(`操作失败: ${err.message}`);
  } else if (err instanceof PlatformError) {
    ElMessage.error(err.message);
  } else {
    // 其他未知错误，开发环境下打印，生产环境可能只提示“系统错误”
    console.error(err);
  }
};

app.use(platform);
app.use(ElementPlus);
app.use(router);
app.mount('#app');
