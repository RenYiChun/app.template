import {defineConfig} from 'vite';
import vue from '@vitejs/plugin-vue';
import dts from 'vite-plugin-dts';
import {resolve} from 'path';

export default defineConfig({
    plugins: [
        vue(),
        dts({
            include: ['src/**/*'],
            outDir: 'dist',
            rollupTypes: false,
            tsconfigPath: resolve(__dirname, './tsconfig.json'),
        }),
    ],
    build: {
        lib: {
            entry: resolve(__dirname, 'src/index.ts'),
            name: 'DataforgeUI',
            fileName: 'index',
            formats: ['es', 'umd'],
        },
        rollupOptions: {
            external: ['vue', 'element-plus', '@lrenyi/dataforge-headless', 'vue-router'],
            output: {
                globals: {
                    vue: 'Vue',
                    'element-plus': 'ElementPlus',
                    '@lrenyi/dataforge-headless': 'DataforgeHeadless',
                    'vue-router': 'VueRouter',
                },
            },
        },
        sourcemap: true,
    },
});
