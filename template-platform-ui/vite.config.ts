import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import dts from 'vite-plugin-dts';
import { resolve } from 'path';

export default defineConfig({
  plugins: [
    vue(),
    dts({
      include: ['src/**/*'],
      outDir: 'dist',
      rollupTypes: true,
    }),
  ],
  build: {
    lib: {
      entry: resolve(__dirname, 'src/index.ts'),
      name: 'PlatformUI',
      fileName: 'index',
      formats: ['es', 'umd'],
    },
    rollupOptions: {
      external: ['vue', 'element-plus', '@lrenyi/platform-headless'],
      output: {
        globals: {
          vue: 'Vue',
          'element-plus': 'ElementPlus',
          '@lrenyi/platform-headless': 'PlatformHeadless',
        },
      },
    },
    sourcemap: true,
  },
});
