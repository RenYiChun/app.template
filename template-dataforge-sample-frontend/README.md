# Template Dataforge Sample UI

template-dataforge 可运行的前端应用，使用 `template-dataforge-headless` 库，提供登录、CRUD 等示例页面。

## 启动

```bash
npm install
npm run dev
```

访问 `http://localhost:3000`。API 通过 Vite proxy 转发到 `http://localhost:8080`，需先启动
`template-dataforge-sample-backend`（在项目根目录执行 `mvnw.cmd spring-boot:run -pl template-dataforge-sample-backend`）。

## 与后端联调

1. 启动后端：`mvnw.cmd spring-boot:run -pl template-dataforge-sample-backend`（端口 8080）
2. 启动本应用：`npm run dev`（端口 3000）
3. 默认账号：`admin` / `admin123`

## 接口验证脚本

在后端已启动时，可运行：

```bash
node scripts/verify-auth-e2e.mjs
```

验证 captcha、login、me、logout 全流程。
