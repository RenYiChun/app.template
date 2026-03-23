# App Template 架构优势

本文档只描述当前仓库仍然成立的架构优势，不再混入已下线主线或历史过渡方案。

## 1. 模块边界清晰

当前主线模块分工如下：

- `template-core`
  - 通用配置、工具、加密、JSON 抽象等基础能力
- `template-api`
  - 安全配置、RBAC、WebSocket、全局异常处理、审计等 API 层能力
- `template-flow`
  - Flow 流聚合引擎，负责运行模型、生命周期、资源治理、健康检查、指标桥接
- `template-flow-sources`
  - Kafka、NATS、分页 API 等 source 适配器
- `template-cloud`
  - Feign、凭证透传、OAuth2 token 获取等微服务调用能力
- `template-oauth2-service`
  - OAuth2 授权服务实现

收益：

- 业务方可以按需引入，而不是被迫吃下整套能力
- Flow 与 source 适配器边界明确，不需要为接入 source 改动引擎内核

## 2. Flow 作为独立主线能力

Flow 模块的价值不是 CRUD 脚手架，而是流聚合运行时：

- 按 `joinKey` 做多路数据汇聚
- 支持 push / pull 两种接入方式
- 支持配对模式与单条消费模式
- 支持可插拔 source provider
- 支持有界资源治理与背压控制

这使它更像一个可复用的运行时框架，而不是某个业务系统里的局部组件。

## 3. 资源治理优先

Flow 的核心设计取向是“先把资源边界站住”：

- 生产线程、生产在途、存储容量、消费线程、已离库未终结数量都可受控
- 满载时通过背压阻断继续扩张，而不是无限堆积
- Spring 容器关闭与内部资源关闭路径都有收口

收益：

- 降低高并发场景下 OOM 和线程失控风险
- 让框架具备可上线的最低工程条件

## 4. 运行模型与接入模型分离

FlowJoiner 只关心业务规则：

- 数据类型
- `joinKey`
- 是否需要配对
- 配对成功怎么处理
- 单条离场怎么处理

框架负责：

- source 拉取或推送驱动
- 生命周期与完成判定
- 指标与健康检查
- 资源限制与存储调度

这让业务接入面保持稳定，不必理解整套内部调度细节才能使用 Flow。

## 5. 接入能力与交付能力分离

当前仓库已经把“内核实现”和“框架交付面”拆开治理：

- `docs/getting-started`
  - 解决第一次接入和最小跑通
- `docs/guides`
  - 解决运行模型、配置、多值、指标、排障
- `docs/design`
  - 只保留仍有现实指导意义的设计说明
- `docs/design/archive`
  - 归档历史 Flow 设计稿，不再作为当前实现依据

这样可以避免历史方案继续污染接入文档。

## 6. 可验证而不是只靠口头保证

Flow 主线能力已经具备框架级验证链：

- 引擎核心测试
- 自动配置与 Actuator 健康桥接测试
- 生命周期与边界行为测试
- `template-flow-sources` provider 层测试

收益：

- 不是“代码很多”，而是“可以被验证地接入和交付”
- 发布判断不再依赖阅读源码猜行为

## 7. 当前使用建议

如果你的目标是接入或排障，不要先读历史设计稿。建议顺序：

1. [快速开始](../getting-started/quick-start.md)
2. [配置参考](../getting-started/config-reference.md)
3. [Flow 流聚合使用指导](../guides/flow-usage-guide.md)
4. [监控指标使用指南](../guides/metrics-guide.md)

如果你的目标是理解某个设计为什么这么做，再回看 `docs/design` 或 `docs/design/archive`。
