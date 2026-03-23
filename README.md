# App Template

以 Spring Boot 为基础的模板仓库，当前主线能力聚焦在 Flow 流聚合框架。

## 当前主线

- `template-flow`：Flow 核心引擎，负责多子流聚合、背压、存储、生命周期、指标和健康检查
- `template-flow-sources`：Kafka、NATS、分页 API 等数据源适配器

其他模块仍在仓库中，但本仓库当前对外优先交付的是 Flow 能力，而不是大而全平台。

## Flow 适用场景

- 订单匹配、消息对齐、多源数据汇聚
- 同一业务键下的双流配对
- 单流覆盖消费或 FIFO 队列消费
- 需要显式背压、可观测、可控关闭的批量流任务

## Flow 已提供的能力

- 拉取模式和推送模式
- 两种存储模型：`LOCAL_BOUNDED`、`QUEUE`
- 全局与 per-job 背压限制
- Job 生命周期管理
- Micrometer 指标与 Actuator 健康检查桥接
- Kafka / NATS / 分页 API Source 适配器

## 最小接入入口

第一次接入 Flow，按下面顺序看：

1. [Flow 快速开始](docs/getting-started/quick-start.md)
2. [Flow 配置参考](docs/getting-started/config-reference.md)
3. [Flow 使用指导](docs/guides/flow-usage-guide.md)
4. [Flow 指标监控指南](docs/guides/metrics-guide.md)

## 构建

Windows:

```powershell
.\mvnw.cmd clean install
```

Unix:

```bash
./mvnw clean install
```

## 环境要求

- Java 21+
- Maven 3.6+

## 模块

- `template-core`：基础配置和通用能力
- `template-flow`：Flow 核心引擎
- `template-flow-sources`：Flow Source 适配器
- `template-api`：安全、审计、WebSocket 等 Web 侧能力
- `template-cloud`：Feign、OAuth2 token 透传
- `template-oauth2-service`：OAuth2 授权服务

## License

Apache License 2.0
