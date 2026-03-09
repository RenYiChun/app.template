# App Template

面向 Spring Boot 的快速开发模板库，提供 OAuth2 安全认证、实体驱动 CRUD、流聚合引擎与微服务能力，按需引入、零侵入接入。

## 设计目标

- **开发效率**：注解即 API、异常零样板、声明式安全，减少重复 CRUD 与配置
- **运行稳定**：背压控制、有界缓存、双重关闭保障，避免 OOM 与资源泄漏
- **可观测**：traceId、指标、健康检查开箱即用，便于运维排障
- **原则**：KISS、SOLID、约定优于配置，横切能力可开关、按需引入

## 适用场景

- 个人/小团队快速搭建后台系统，需要 OAuth2、RBAC、审计
- 微服务架构下的资源服务或 API 网关后端
- 需要多路数据流按 Key 汇聚、对齐、消费的业务（订单匹配、消息拉齐等）
- 实体驱动 CRUD 的后台（管理台、运营台、数据中台）

## 特性

- **OAuth2 认证与授权**：基于 Spring Authorization Server
- **安全与 RBAC**：JWT、Opaque Token 双模、RBAC 权限模型、声明式白名单
- **微服务支持**：Feign 客户端、凭证透传、审计日志
- **Flow 流聚合引擎**：多路数据按 joinKey 汇聚，背压控制、虚拟线程、可插拔存储（Caffeine/Queue）
- **Flow 数据源**：Kafka、NATS、分页 API 适配器
- **实体驱动 Dataforge**：注解生成 CRUD、Excel 导出、OpenAPI、Action 扩展
- **WebSocket**：复用 OAuth2 认证、消息级鉴权
- **可观测**：traceId 追踪、Micrometer/Prometheus 指标、Flow 健康检查
- **全局异常**：语义异常→HTTP 状态码，统一 Result 响应格式

## 环境要求

- Java 21+
- Maven 3.6+

## 技术栈

| 类别   | 技术                                                                 |
|------|--------------------------------------------------------------------|
| 基础框架 | Spring Boot __SPRING_BOOT__、Spring Cloud __SPRING_CLOUD__          |
| 安全   | Spring Security、Spring Authorization Server、OAuth2 JOSE、Nimbus JWT |
| 微服务  | OpenFeign、Spring Cloud LoadBalancer                                |
| 缓存与流 | Caffeine、Spring Data Redis、Kafka、NATS                              |
| 可观测  | Micrometer、Prometheus、Actuator                                     |
| 数据   | Spring Data JPA、Apache POI（Excel 导出）                               |
| 其他   | Jackson、Lombok、Commons Lang3/Text/Codec                            |

## 模块

### 后端模块

| 模块                                | 说明                            |
|-----------------------------------|-------------------------------|
| template-api                      | 审计、RBAC、WebSocket、安全配置、全局异常处理 |
| template-core                     | 工具类、加密服务、JSON 抽象、配置属性         |
| template-flow                     | Flow 流聚合引擎                    |
| template-flow-sources             | Kafka、NATS、分页数据源适配器           |
| template-cloud                    | Feign、OAuth2 Token 获取         |
| template-oauth2-service           | OAuth2 授权服务器实现                |
| template-dataforge                | 实体驱动 CRUD、权限、OpenAPI          |
| template-dataforge-model          | 领域实体、审计服务                     |
| template-dataforge-processor      | 编译期注解处理                       |
| template-dataforge-sample-backend | 示例后端应用                        |

### 前端模块

| 模块                                 | 说明                       |
|------------------------------------|--------------------------|
| template-dataforge-headless        | Dataforge 前端核心库（框架无关）    |
| template-dataforge-ui              | Vue 3 + Element Plus 组件库 |
| template-dataforge-sample-frontend | 示例前端应用                   |

## 构建

```bash
.\mvnw.cmd clean install
```

（Windows 使用 `mvnw.cmd`；Unix 使用 `./mvnw`）

## 文档

完整文档索引见 [docs/README.md](docs/README.md)。主要入口：

- [快速开始](docs/getting-started/quick-start.md)
- [配置参考](docs/getting-started/config-reference.md)
- [框架选型收益](docs/design/framework-benefits.md)
- [架构优势](docs/design/architecture-advantages.md)
- [Dataforge 实体 UI 布局元数据设计](docs/design/dataforge-entity-ui-layout.md)
- [Flow 流聚合使用指导](docs/guides/flow-usage-guide.md)
- [指标监控指南](docs/guides/metrics-guide.md)
- [质量评分卡](docs/reference/quality-scorecard.md)

## License

Apache License 2.0 - see [LICENSE](LICENSE)
