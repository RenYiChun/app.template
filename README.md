# App Template

A quick-to-use template library focusing on Spring Boot Security and OAuth2.

## Features

- **OAuth2 认证与授权**：基于 Spring Authorization Server
- **安全与 RBAC**：JWT、Opaque Token、RBAC 权限模型
- **微服务支持**：Feign 客户端、审计日志
- **Flow 流聚合引擎**：多路数据按 joinKey 汇聚，支持双流对齐与队列消费
- **可插拔存储**：Caffeine（本地）、Queue、Kafka、NATS、分页数据源

## Requirements

- Java 21+
- Maven 3.6+

## Modules

| Module | Description |

|--------|-------------|
| template-api | 审计、RBAC、WebSocket、安全配置 |
| template-core | Flow 引擎、工具类、加密服务 |
| template-cloud | Feign、OAuth2 Token 获取 |
| template-oauth2-service | OAuth2 授权服务器实现 |
| template-flow-sources | Kafka、NATS、分页数据源适配器 |

## Build

```bash
.\mvnw.cmd clean install
```

（Windows 使用 `mvnw.cmd`；Unix 使用 `./mvnw`）

## Documentation

- [框架设计优势](docs/architecture-advantages.md)
- [质量评分卡](docs/quality-scorecard.md)
- [指标监控指南](docs/metrics-guide.md)
- [Flow 流聚合使用指导](docs/flow-usage-guide.md)
- [最小配置文档](docs/最小配置文档.md)
- [详细配置教程](docs/详细配置教程.md)

## License

Apache License 2.0 - see [LICENSE](LICENSE)
