import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { resolve } from 'path';

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: [
      {
        find: /^@lrenyi\/dataforge-headless\/vue$/,
        replacement: resolve(__dirname, '../template-dataforge-headless/src/vue/index.ts'),
      },
      {
        find: /^@lrenyi\/dataforge-headless$/,
        replacement: resolve(__dirname, '../template-dataforge-headless/src/core/index.ts'),
      },
      {
        find: /^@lrenyi\/dataforge-ui$/,
        replacement: resolve(__dirname, '../template-dataforge-ui/src/index.ts'),
      },
      {
        find: /^@\//,
        replacement: `${resolve(__dirname, 'src')}/`,
      },
    ],
  },
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
      '/docs': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
