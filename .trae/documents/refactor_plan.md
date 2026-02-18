# template-platform-headless 重构计划

本计划旨在解决 `template-platform-headless` 库中剩余的设计不足，重点关注错误处理和类型系统，并为未来的 UI 解耦做准备。

## 目标

提升库的健壮性、类型安全性和可维护性，使其更符合 "Headless" 的定位。

## 待解决问题

1. **错误处理粗糙**: 仅抛出简单 Error 对象，缺乏结构化。
2. **类型系统局限**: 过度依赖 `Record<string, unknown>`，缺乏类型推断辅助。
3. **UI 耦合**: 核心库包含 Element Plus 组件（暂不在此次迭代中完全拆包，但会优化结构）。

## 执行阶段

### 阶段一：结构化错误处理 (High Priority)

建立统一的错误处理机制，让前端能区分网络错误、业务错误和认证错误。

1. **创建错误类定义 (`src/core/errors.ts`)**

   * `PlatformError`: 基类。

   * `NetworkError`: 网络连通性问题。

   * `HttpError`: HTTP 状态码错误 (4xx, 5xx)，包含 `status`, `statusText`, `response`。

   * `BusinessError`: 后端业务逻辑错误 (Result.code !== 0)。

   * `AuthError`: 认证失败 (401/403)。

2. **改造** **`EntityClient`** **请求流**

   * 修改 `requestFn` 和 `handleResult`。

   * 捕获 `fetch` 异常并包装为 `NetworkError`。

   * 检查 HTTP Status，非 200 抛出 `HttpError`。

   * 检查 Response Body `code`，非 0 抛出 `BusinessError`。

3. **更新** **`AuthClient`**

   * 适配新的错误类型，确保 `onUnauthorized` 逻辑依然有效。

### 阶段二：类型系统增强 (Medium Priority)

优化泛型支持，减少 `any` 和 `unknown` 的使用。

1. **优化** **`EntityClient`** **方法签名**

   * 为 `search`, `get`, `create`, `update` 提供更好的泛型约束。

   * 引入 `EntityDef<T>` 概念，允许用户定义实体类型。

2. **添加类型辅助工具**

   * `defineEntity<T>(name: string)`: 返回一个强类型的操作对象，避免每次调用都传泛型。

### 阶段三：验证与适配

确保现有 Sample UI 能正常运行并受益于新的改进。

1. **更新 Sample UI**

   * 适配新的错误类（例如在拦截器或 try-catch 中打印更详细的错误日志）。

   * 在 `UserList.vue` 或 `DeptList.vue` 中尝试使用新的类型辅助工具（可选）。

## 交付物

* 修改后的 `src/core/errors.ts`

* 更新后的 `src/core/client.ts`

* 更新后的 `src/core/types.ts`

* 验证通过的 Sample UI

## 下一步 (Out of Scope)

* 完全剥离 UI 组件到 `@lrenyi/platform-ui-element` 包中。

