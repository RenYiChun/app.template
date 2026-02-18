# Template Platform Backend

`template-platform-frontend` 对应的后端示例应用，基于 template-platform 提供 CRUD、Action、OpenAPI 文档。

## 启动

在项目根目录执行：

```bash
mvnw.cmd spring-boot:run -pl template-platform-sample-backend
```

- 接口前缀：`/api`
- OpenAPI 文档：`GET http://localhost:8080/api/docs`（JSON）
- 文档 UI：`http://localhost:8080/docs`（Scalar）

## 与前端联调

1. 启动本后端：`mvnw.cmd spring-boot:run -pl template-platform-sample-backend`（在项目根目录，端口 8080）
2. 启动 `template-platform-sample-ui`：`cd ..\template-platform-sample-ui && npm install && npm run dev`（端口 3000，proxy 到 8080）
3. 或业务前端：配置 `VITE_API_BASE_URL=http://localhost:8080`，`createPlatform({ client: { baseURL: 'http://localhost:8080', apiPrefix: '/api' } })`
4. 已启用 CORS，允许 `http://localhost:3000`、`http://127.0.0.1:3000` 跨域

## 示例实体

- `User`：pathSegment `users`，支持 CRUD、导出
- Action：`resetPassword`（`POST /api/users/{id}/resetPassword`）
