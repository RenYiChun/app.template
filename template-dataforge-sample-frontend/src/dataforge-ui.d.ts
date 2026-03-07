/**
 * 本地开发时若 UI 包未生成类型，用此声明避免 TS 报错。
 * 正式构建前请先执行 template-dataforge-ui 的 build 以生成 dist/index.d.ts。
 */
declare module '@lrenyi/dataforge-ui';
