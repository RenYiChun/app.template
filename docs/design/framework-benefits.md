# 使用本框架可获得的实实在在的好处

本文档从开发者视角列出使用 App Template 框架时能获得的具体收益，供选型与落地参考。

---

## 一、开发效率类

### 1. 实体即 API（Dataforge）

- **1 个注解生成完整 CRUD 接口**：在实体类上加 `@DataforgeEntity`，即可获得分页搜索、单条查询、创建、更新、删除、批量删除、批量更新、Excel 导出，无需手写 Controller。
- **零 Controller 代码**：不必写 `@RestController`、`@GetMapping` 等，路由和参数绑定由 `GenericEntityController` 统一处理。
- **编译期 DTO 生成**：CreateDTO、UpdateDTO、ResponseDTO 自动生成在实体包下的 `dto` 子包，用 `@DtoExcludeFrom(DtoType.CREATE)` 等排除敏感字段（如密码不出 ResponseDTO）。
- **按需开关接口**：`enableList`、`enableGet`、`enableCreate`、`enableUpdate`、`enableDelete`、`enableExport` 等可单独关闭，关闭的接口直接 404，OpenAPI 文档也只展示已启用的。
- **Action 扩展**：用 `@EntityAction` 声明扩展动作（如 `/api/users/{id}/resetPassword`），框架负责注册、鉴权、OpenAPI 生成。

### 2. 异常处理零样板

- **一行抛出即 404**：`throw new NotFoundException("订单", orderId)` → 自动 404 + 标准 `Result<T>`。
- **Controller 无需 try-catch**：领域层抛出语义异常，API 层 `GlobalExceptionHandler` 统一映射 HTTP 状态码和 JSON 结构。
- **前端只处理一种 Result 结构**：所有模块错误响应格式一致，`code`、`data`、`message` 统一。

### 3. 安全配置声明式

- **声明式放行**：`app.template.security.permit-urls.{appName}` 按应用名管理白名单，`/docs`、`/actuator/health` 等无需逐个写 `permitAll()`。
- **双模认证可配置切换**：JWT（本地公钥/远程 JWK）和 Opaque Token 通过配置切换，改配置即可，无需改代码。
- **RBAC 权限自动注册**：实体级 create/read/update/delete 权限自动注册到权限表，配合角色分配即用。
- **统一 Coder（密码 + 配置解密）**：密码 encode/matches 与配置 `aENC(...)` 解密共用一套 `TemplateEncryptService`，格式与 Spring Security 的 `{id}...` 一致，支持 bcrypt、RSA2048、PBKDF2 等多算法 SPI 扩展；无需同时引入 Jasypt 与 PasswordEncoder，详见 [加密与 Coder 设计说明](encryption-and-coder.md)。

### 4. WebSocket 认证与实时通道

- **与 HTTP 统一的认证模型**：握手阶段复用 OAuth2（Opaque Token / JWT），无需单独维护一套 WebSocket 认证逻辑。
- **Token 来源可配置**：优先从 Header `Authorization: Bearer` 取 token；可选从 query `access_token` 取。生产环境可通过 `app.template.websocket.allow-token-in-query-parameter=false` 禁止 query，避免 token 进入日志、Referer。
- **生产环境安全建议**：使用 **wss://** 加密传输；前端尽量通过 Header 传 token，与框架推荐一致。
- **消息级鉴权**：握手后 Principal 写入 Session，业务在 `TemplateWebSocketHandler` 的 `handleMessage` 等中可根据 `session.getPrincipal()` 及 authorities 做操作级鉴权，仅允许有权限用户执行敏感操作。
- **按路径注册**：实现 `TemplateWebSocketHandler` 并声明 `path()`，框架自动注册握手与 CORS，与现有安全配置一致。

### 5. 配置集中且带校验

- **类型安全**：所有配置通过 `@ConfigurationProperties` 绑定，IDE 自动补全。
- **JSON 框架可全局切换**：通过 `JsonProcessor` 抽象与 `app.template.web.json-processor-type` 配置，可在 Jackson、Gson 等实现间切换；业务代码统一注入 `JsonService` 或 `JsonProcessor`，切换实现时无需改业务逻辑。详见 [JSON 处理器与框架切换能力](json-processor.md)。
- **启动时校验**：`TemplateConfigProperties.validateConfig()` 会检测并发许可为 0、JWT 配置缺失等不合理配置，输出 WARN。
- **配置摘要日志**：启动时打印关键配置值，便于排查远程配置覆盖问题。

---

## 二、运行稳定性与资源管理

### 6. Flow 流聚合引擎

- **背压控制**：生产端感知缓存水位和消费许可，满载时自动阻塞，避免 OOM。
- **公平调度**：多 Job 共享全局消费许可，按 fair-share 分配，防止单 Job 饿死其他 Job。
- **虚拟线程**：充分利用 Java 21 虚拟线程，高并发下线程资源零浪费。
- **可插拔数据源**：Kafka、NATS、分页 API 等通过 `FlowSource` / `FlowSourceProvider` SPI 接入，业务只需实现 `FlowJoiner`。
- **两种存储模式**：CAFFEINE（Key-Value 配对/覆盖）和 QUEUE（FIFO 队列消费），按业务场景选。

### 7. 统一缓存策略

- **所有缓存有容量上限**：OAuth2 Token、RBAC 权限、Flow 引擎缓存均使用 Caffeine，杜绝无界 `ConcurrentHashMap` 导致 OOM。
- **统一 Caffeine API**：代码风格一致，可读性好。
- **TTL 与容量均可配置**：按业务调节。

### 8. 线程与资源生命周期

- **双重关闭保障**：Spring 容器关闭时主动 shutdown，JVM hook 兜底防遗漏。
- **线程命名规范**：Flow 等模块的线程池均有统一前缀，线程 dump 一眼定位来源。
- **Flow 移除执行器**：有界队列 + CallerRunsPolicy，防止任务堆积 OOM。

### 9. 可选 Feign 重试

- 默认关闭，不拖累轻量项目；需要时通过 `app.template.feign.retry.*` 开启，可配置重试次数和间隔。

---

## 三、可观测性

### 10. 请求追踪（MDC）

- **traceId 自动注入**：`TraceFilter` 为每个 HTTP 请求注入 `traceId`（支持上游 `X-Trace-Id` 透传）。
- **userId、requestPath 入 MDC**：日志 pattern 用 `%X{traceId}` 即可串联同一请求的全链路日志。

### 11. 指标系统（Micrometer + Prometheus）

- **Flow 引擎指标**：job started/completed/stopped、生产/消费量、背压、损耗率（TIMEOUT/EVICTION/REJECT 等）、信号量利用率。
- **安全指标**：认证失败（按原因）、OAuth2 Token 签发/失败。
- **Dataforge 指标**：CRUD 操作耗时和错误率。
- **数据源指标**：Kafka/NATS 接收速率、拉取延迟。
- **业务自定义指标**：`AppMetrics` 提供 `count()`、`recordTime()`、`gauge()` 等，一行代码埋点。

### 12. 健康检查

- **Flow 引擎健康**：`FlowHealth` 与 Actuator 集成，支持 HEALTHY / DEGRADED / UNHEALTHY 聚合。
- **Prometheus + Grafana**：提供 `grafana-dashboard.json`，可直接导入使用。

---

## 四、微服务与调用

### 13. Feign 凭证透传与内部调用安全

- 内部 Feign 调用自动携带认证上下文（Token 等），支持 OAuth2 Client Credentials。
- **FeignClientErrorDecoder**：将 Feign 4xx/5xx 异常映射为 `TemplateException`，与框架异常契约一致。
- **内部调用放行**：带 `X-Internal-Call: true` 的请求可配置为免认证；通过 `app.template.feign.internal-call-allowed-ip-patterns`（CIDR）限制仅内网来源生效，防止客户端伪造该头绕过认证，Docker/K8s 下配置集群网段即可。

### 14. 模块化依赖

- **按需引入**：template-api、template-cloud、template-dataforge、template-flow-sources 等按需选，不引入不需要的依赖。
- **自动配置**：通过 `AutoConfiguration.imports` 注册，零侵入接入。

---

## 五、测试与开发体验

### 15. Flow 测试辅助

- **FlowTestSupport**：`createEngine()`、`defaultFlowConfig()`、`cleanup()`，避免测试里重复写初始化逻辑。
- **SimpleMeterRegistry**：测试不真实上报指标，不污染 Prometheus。

### 16. 模块边界清晰

- **template-core 无 Web 依赖**：可在非 Web 场景（批处理、定时任务）复用 Flow 引擎、异常体系、配置等。
- **依赖收敛**：Enforcer 约束依赖传递，避免依赖地狱。

---

## 六、综合对比（使用 vs 不使用）

| 能力 | 不用框架 | 使用本框架 |
|------|----------|------------|
| 实体 CRUD API | 手写 Controller、Service、DTO | 1 个注解 + 继承 BaseEntity |
| 异常返回格式 | 每个 Controller try-catch + 手动封装 | 抛出语义异常，全局统一映射 |
| 缓存实现 | 自己选 Caffeine/ConcurrentHashMap/Redis 混用，易出现无界 Map | 统一 Caffeine，全部有界 + TTL |
| 请求全链路追踪 | 自己写 Filter 注入 MDC | TraceFilter 自动注入 traceId |
| 线程池管理 | 自己创建、命名、忘记关闭 | 统一创建、命名、shutdown hook |
| 配置校验 | 手动或忽略 | 启动时 validateConfig + 摘要 |
| Flow 流聚合 | 自己实现背压、公平调度、多流对齐 | 实现 FlowJoiner，引擎负责其余 |
| 指标埋点 | 自己接 Micrometer、定义命名规范 | 内置 Flow/安全/CRUD 等指标，业务用 AppMetrics 扩展 |
| 安全白名单 | 在 SecurityConfig 里多处 permitAll() | 声明式 permit-urls 配置 |
| WebSocket 认证 | 自己写握手校验、token 解析、与 OAuth2 脱节 | 复用 OAuth2 握手校验，Header/query 可配置，Principal 入 Session 支持消息级鉴权 |

---

## 七、相关文档

- [框架设计优势](architecture-advantages.md) — 设计原则与九大设计优势
- [加密与 Coder 设计说明](encryption-and-coder.md) — 加密方案与 Jasypt/Spring 的差异及设计优势
- [JSON 处理器与框架切换能力](json-processor.md) — 全局切换 JSON 框架的设计与用法
- [质量评分卡](../reference/quality-scorecard.md) — 各维度评分与改进建议
- [Flow 流聚合使用指导](../guides/flow-usage-guide.md) — Flow 引擎使用示例
- [指标监控指南](../guides/metrics-guide.md) — 指标接入与 PromQL 示例
