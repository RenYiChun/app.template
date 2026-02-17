import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 3000,
    host: true, // 监听所有网卡，避免 localhost 访问异常
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/oauth2': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/jwt': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
