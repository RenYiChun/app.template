# App Template 框架设计优势

## 设计原则

App Template 遵循以下核心设计原则：

- **KISS（Keep It Simple, Stupid）**：优先选择最简单可行的方案，避免过度抽象
- **SOLID**：每个模块单一职责，通过接口解耦，支持扩展而非修改
- **约定优于配置**：提供合理默认值，零配置即可运行；高级场景通过 `app.template.*` 统一配置入口调节
- **可选增强**：韧性、缓存、监控等横切能力均为可开关特性，不强绑定到业务代码

---

## 一、模块化与职责分离

```
template-core          领域核心（Flow 引擎、异常体系、配置、工具）
  └─ template-api      表现层（安全、WebSocket、全局异常处理、请求追踪）
       ├─ template-cloud            微服务能力（Feign、负载均衡、可选重试）
       ├─ template-dataforge        实体驱动业务平台（CRUD、权限、审计）
       └─ template-oauth2-service   OAuth2 授权服务器
template-flow-sources  可插拔数据源（Kafka / NATS / 分页 API）
```

**优势体现：**

- 业务方按需引入模块，不会引入不需要的依赖
- 各模块通过 `AutoConfiguration.imports` 注册自动配置，零侵入接入
- 核心层（template-core）不依赖任何 Web 框架，可在非 Web 场景中使用

---

## 二、统一异常响应契约

框架提供三层异常处理机制：

| 层级 | 实现 | 职责 |
|------|------|------|
| 领域层 | `TemplateException` 及子类 | 定义错误码和语义，与 HTTP 无关 |
| API 层 | `GlobalExceptionHandler` | 统一映射为 `Result<T>` + HTTP 状态码 |
| 模块层 | `DataforgeExceptionHandler` 等 | 模块级特殊处理，优先级高于全局 |

**接入方收益：**

- 抛出 `new NotFoundException("订单", orderId)` 即自动返回 404 + 标准 JSON
- 所有模块的错误响应格式一致，前端只需处理一种 `Result` 结构
- 无需在每个 Controller 中写 try-catch

**异常类继承关系：**

```
TemplateException (errorCode + message)
├── NotFoundException       → 404
├── BadRequestException     → 400
├── ForbiddenException      → 403
├── ServiceException        → 500
└── HttpStatusException     → 自定义状态码（Dataforge 模块扩展）
```

---

## 三、统一缓存策略

所有模块的缓存统一使用 Caffeine，遵循以下基线规范：

| 场景 | 实现 | TTL | 容量上限 |
|------|------|-----|---------|
| OAuth2 Token 缓存 | `OauthUtilService` | 50 分钟 | 64 条 |
| RBAC 权限缓存 | `DefaultUserPermissionResolver` | 可配置（分钟） | 1024 条 |
| Flow 引擎缓存 | `CaffeineFlowStorage` | 可配置（毫秒） | 可配置 |
**JSON 框架可全局切换**：`JsonProcessor` 抽象使 OAuth2、JWT、认证失败等框架输出统一经 `JsonService`，切换 Jackson/Gson 等实现时业务无感知，详见 [JSON 处理器与框架切换能力](json-processor.md)。

**优势体现：**

- 所有缓存都有容量上限，杜绝无界增长导致的 OOM
- 统一使用 Caffeine API，代码可读性好，性能有保证
- TTL 和容量均可通过配置调节

---

## 四、可观测性体系

### 4.1 指标系统（Metrics）

基于 Micrometer + Prometheus，覆盖四个维度：

- **Flow 引擎**：吞吐量、延迟分布、背压、损耗率、信号量利用率
- **HTTP 安全**：认证失败（按原因）、OAuth2 Token 签发/失败
- **Dataforge**：CRUD 操作耗时和错误率
- **数据源**：Kafka/NATS 接收速率、拉取延迟

业务方可通过 `AppMetrics` 工具类快速埋点自定义指标。

### 4.2 请求追踪（Tracing）

`TraceFilter` 自动为每个 HTTP 请求注入 MDC 上下文：

- `traceId`：请求唯一标识（支持从上游透传 `X-Trace-Id` 请求头）
- `userId`：当前认证用户
- `requestPath`：请求路径

日志 pattern 中引用 `%X{traceId}` 即可在日志中关联同一请求的所有操作。

### 4.3 健康检查（Health）

自定义 `FlowHealth` 系统与 Spring Boot Actuator 集成，支持 `HEALTHY / DEGRADED / UNHEALTHY` 三级状态聚合。

---

## 五、Flow 引擎设计

Flow 引擎是框架的核心差异化能力，解决多路数据流按 Key 汇聚、配对、消费的通用场景。

**关键设计决策：**

- **背压控制**：生产端感知缓存水位和消费许可，满载时自动阻塞，避免 OOM
- **公平调度**：多 Job 共享全局消费许可，按 fair-share 分配，防止单个 Job 饿死其他 Job
- **虚拟线程**：充分利用 Java 21 虚拟线程，高并发下线程资源零浪费
- **可插拔存储**：Caffeine（Key-Value 配对）和 Queue（先进先出消费）两种模式，适配不同业务场景
- **可插拔数据源**：通过 `FlowSource` / `FlowSourceProvider` SPI 接入任意数据源

---

## 六、安全体系

- **双模认证**：JWT（本地公钥 / 远程 JWK）和 Opaque Token 两种模式，通过配置切换
- **声明式放行**：`app.template.security.permit-urls` 按应用名分组管理白名单
- **RBAC 权限**：实体级 CRUD 和 Action 权限自动注册到权限表，支持动态角色分配
- **Feign 透传**：内部服务调用自动携带认证凭证，支持 OAuth2 Client Credentials 模式
- **统一 Coder（密码 + 配置解密）**：`TemplateEncryptService` 同时承担密码 encode/matches 与配置 `aENC(...)` 解密，格式与 Spring Security `{id}...` 一致，支持多算法 SPI；区别于 Jasypt 只做配置、Spring 只做密码，详见 [加密与 Coder 设计说明](encryption-and-coder.md)

---

## 七、线程与资源管理

| 资源 | 管理方式 | 关闭策略 |
|------|---------|---------|
| Flow 消费执行器 | `FlowResourceRegistry` | Spring 容器关闭 + JVM hook 兜底 |
| Flow 缓存移除执行器 | 有界 `ThreadPoolExecutor` + `CallerRunsPolicy` | 随 Registry 关闭 |

**优势体现：**

- 所有线程池有命名前缀（`prod-`、`flow-storage-egress`、`flow-removal-` 等），线程 dump 一眼定位
- 双重关闭保障：Spring 容器关闭时主动 shutdown，JVM hook 兜底防遗漏
- 有界队列 + 背压策略，防止任务堆积 OOM

---

## 八、配置治理

- **集中管理**：所有配置通过 `@ConfigurationProperties` 类型安全绑定，IDE 自动补全
- **启动校验**：`TemplateConfigProperties.validateConfig()` 检测不合理配置（如并发许可为 0），输出 WARN 日志
- **配置摘要**：启动时打印关键配置值，便于排查远程配置源覆盖问题
- **分层前缀**：`app.template.*`（框架级）、`app.dataforge.*`（业务平台级），互不干扰

---

## 九、韧性能力（可选）

Feign 客户端支持通过配置启用重试：

```yaml
app:
  template:
    feign:
      retry:
        enabled: true
        max-attempts: 3
        period: 100
        max-period: 1000
```

默认关闭，不影响轻量级项目。后续可按需扩展熔断、限流能力。

---

## 与优化前对比

| 维度 | 优化前 | 优化后 |
|------|--------|--------|
| 异常处理 | 仅 Dataforge 有 Handler，其他模块无覆盖 | 全局 Handler + 模块级 Handler 分层 |
| 缓存 | 4 种实现混用，部分无边界 | 统一 Caffeine，全部有容量上限 |
| 日志 | `@Slf4j` 和 `LoggerFactory` 混用，无追踪 | 统一 `@Slf4j`，MDC 自动注入 traceId |
| 线程管理 | 全局线程池无关闭逻辑，无命名 | 双重关闭保障，命名规范 |
| 配置 | 无校验，远程覆盖可能导致异常 | 启动校验 + 摘要日志 |
| 韧性 | 无 | 可选 Feign 重试 |
| 指标 | 无 | Micrometer + Prometheus 全链路覆盖 |
