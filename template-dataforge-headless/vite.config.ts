import {defineConfig} from 'vite';
import vue from '@vitejs/plugin-vue';
import dts from 'vite-plugin-dts';
import {dirname, resolve} from 'path';
import {fileURLToPath} from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

export default defineConfig({
    plugins: [
        vue(),
        dts({
            include: ['src/core/**/*', 'src/vue/**/*'],
            outDir: 'dist',
            rollupTypes: false,
        }),
    ],
    build: {
        outDir: 'dist',
        lib: {
            entry: {
                core: resolve(__dirname, 'src/core/index.ts'),
                vue: resolve(__dirname, 'src/vue/index.ts'),
            },
            formats: ['es'],
        },
        rollupOptions: {
            external: ['vue', 'element-plus'],
            output: {
                entryFileNames: '[name]/index.js',
                chunkFileNames: 'shared/[name].js',
                assetFileNames: 'assets/[name].[ext]',
            },
        },
        sourcemap: true,
    },
});
