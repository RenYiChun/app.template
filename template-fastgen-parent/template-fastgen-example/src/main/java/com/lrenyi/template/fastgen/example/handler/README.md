# @Page 后端扩展点

当在 `@Page` 上配置了 `apiPath` 时，框架会生成：

- **XxxPageRequest**：表单请求 DTO（`com.lrenyi.page` 包）
- **XxxPageHandler**：接口，业务实现扩展点
- **XxxPageController**：REST 接口，委托给 Handler（若存在）

## 使用方式

1. 执行一次完整构建，生成 Handler 接口与 Controller：
   ```bash
   mvn process-classes
   ```

2. 复制示例实现并重命名为 `.java`：
   ```bash
   cp LoginPageHandlerImpl.java.example LoginPageHandlerImpl.java
   ```

3. 再次编译：
   ```bash
   mvn compile
   ```

4. 在 `LoginPageHandlerImpl` 中实现真实登录逻辑（查库、校验、发 JWT 等）。

未提供 Handler 实现时，Controller 仍会响应请求并返回默认 `{ "success": true }`。
