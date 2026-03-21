# 使用本框架可获得的实实在在的好处

本文档从开发者视角列出使用 App Template 时仍然值得保留的收益，重点聚焦在安全基座、Flow 引擎与微服务集成。

---

## 一、开发效率类

### 1. 异常处理零样板

- **一行抛出即 404**：`throw new NotFoundException("订单", orderId)` 会自动映射为标准 HTTP 状态与统一响应结构。
- **Controller 无需 try-catch**：领域层抛语义异常，API 层统一完成状态码和 JSON 映射。
- **前端只处理一种结构**：错误响应统一收敛到 `Result<T>`。

### 2. 安全配置声明式

- **声明式放行**：`app.template.security.permit-urls.{appName}` 按应用名维护白名单。
- **双模认证可切换**：JWT 与 Opaque Token 通过配置切换，无需改业务代码。
- **统一 Coder**：密码编码与配置解密共用 `TemplateEncryptService`。

### 3. WebSocket 与 HTTP 认证模型统一

- 握手阶段可复用 OAuth2 Token。
- Token 来源和跨域行为可通过配置收紧。
- 业务侧可直接基于 `Principal` 做消息级鉴权。

### 4. 配置集中且带校验

- 所有配置通过 `@ConfigurationProperties` 绑定。
- 启动期会校验明显不合理的配置。
- 关键配置会输出摘要日志，便于排查环境覆盖问题。

---

## 二、运行稳定性与资源管理

### 5. Flow 流聚合引擎

- **背压控制**：满载时自动阻塞生产端，避免无界堆积。
- **公平调度**：多 Job 共享全局消费许可，避免单 Job 吞噬全部吞吐。
- **虚拟线程**：基于 Java 21 提升高并发场景下的线程利用率。
- **可插拔数据源与存储**：Kafka、NATS、分页 API 与多种存储模型可以按场景组合。

### 6. 统一缓存与线程治理

- 关键缓存统一走 Caffeine，并具备上限与 TTL。
- 线程池有命名前缀，定位与排障成本更低。
- Spring 关闭与 JVM hook 共同兜底资源释放。

### 7. 可选 Feign 重试

- 默认关闭，轻量项目零额外负担。
- 需要时通过配置启用重试次数与间隔。

---

## 三、可观测性

### 8. 请求追踪（MDC）

- `TraceFilter` 自动注入 `traceId`、`userId`、`requestPath`。
- 日志天然具备跨层串联能力。

### 9. 指标系统（Micrometer + Prometheus）

- 覆盖 Flow 引擎、安全认证和数据源接入等关键路径。
- 业务侧可通过 `AppMetrics` 追加自定义埋点。

### 10. 健康检查

- `FlowHealth` 与 Actuator 集成，支持多级健康状态聚合。
- 附带 Grafana 仪表板资源，便于快速落地监控。

---

## 四、微服务与调用

### 11. Feign 凭证透传与内部调用安全

- 内部 Feign 调用可自动携带认证上下文。
- `FeignClientErrorDecoder` 将远端异常收敛到统一异常契约。
- 内部调用放行支持来源网段限制，减少伪造请求头风险。

### 12. 模块化依赖

- `template-api`、`template-cloud`、`template-flow-sources` 可以按需引入。
- 各模块通过自动配置接入，避免业务项目手工拼装。

---

## 五、测试与开发体验

### 13. Flow 测试辅助

- `FlowTestSupport` 提供统一测试入口，减少样板初始化代码。
- `SimpleMeterRegistry` 让指标验证不污染外部监控系统。

### 14. 模块边界清晰

- `template-core` 不依赖 Web，可在批处理、定时任务等非 Web 场景复用。
- 依赖通过父 POM 管理，避免版本漂移。

---

## 六、综合对比（使用 vs 不使用）

| 能力 | 不用框架 | 使用本框架 |
|------|----------|------------|
| 异常返回格式 | 每个 Controller 手写映射 | 抛出语义异常，全局统一映射 |
| 缓存实现 | 容易混用且无界 | 统一 Caffeine，有界且可调 |
| 请求全链路追踪 | 需要自行注入 MDC | TraceFilter 自动注入 |
| 线程池管理 | 自建、自命名、易忘记释放 | 统一创建、命名、关闭 |
| 配置校验 | 靠人工约束 | 启动时自动校验并输出摘要 |
| Flow 流聚合 | 需要自己处理背压与调度 | 实现 FlowJoiner，引擎负责运行时复杂性 |
| 指标埋点 | 需要自行约定命名 | 内置核心指标并支持扩展 |
| 安全白名单 | 分散在 SecurityConfig | 声明式配置集中管理 |

---

## 七、相关文档

- [框架设计优势](architecture-advantages.md) — 设计原则与主要架构收益
- [加密与 Coder 设计说明](encryption-and-coder.md) — 密码与配置解密方案
- [JSON 处理器与框架切换能力](json-processor.md) — 全局切换 JSON 实现的方式
- [质量评分卡](../reference/quality-scorecard.md) — 当前工程质量与改进方向
- [Flow 流聚合使用指导](../guides/flow-usage-guide.md) — Flow 引擎使用示例
- [指标监控指南](../guides/metrics-guide.md) — 指标接入与 PromQL 示例
