# 使用本框架的实际收益

本文档只保留当前仓库中仍然成立的收益，不再承诺源码里不存在的能力。

## 1. 接入成本更低

- 异常处理、统一响应、安全配置和 Flow 运行时能力已经模块化，业务项目按需引入即可。
- 配置集中在 `app.template.*`，并带启动期校验，降低了靠人工约束配置的风险。
- Flow 与 `template-flow-sources` 的边界清晰，接 source 不需要改引擎内核。

## 2. Flow 运行时能力可直接复用

如果业务需要做按 key 汇聚、配对或单条消费，不需要自己重写这一层运行时：

- 支持 push / pull 两种进入方式
- 支持有界存储与背压控制
- 支持多 source provider
- 支持生命周期管理、健康检查和指标桥接

这类收益不是“少写几行工具代码”，而是少维护一套容易失控的并发与资源治理逻辑。

## 3. 运行稳定性更可控

- 线程池、资源关闭、存储边界和背压语义都由框架统一收口。
- Flow 对资源耗尽、停止、完成、被动离场等边界行为有明确模型。
- source 侧已经补到 Kafka / NATS / Paged 的最小可用品质，不再只是源码片段。

## 4. 可观测性更完整

- Flow 已接上 Actuator health bridge，可通过 `/actuator/health` 观察状态。
- Flow 与 sources 都有 Micrometer 指标接入点，可直接接 Prometheus。
- `displayName` / `metricJobId` 能改善任务级看板的可读性。

## 5. 工程化验证更扎实

- `template-flow` 已形成自动配置测试、集成测试、存储边界测试。
- `template-flow-sources` 已形成 provider 层测试和 source 行为测试。
- `FlowTestSupport` 可复用于模块内测试，降低构造样板。

## 6. 更适合当前仓库主线

当前仓库已经不再围绕 CRUD 平台设计，真正值得保留的主线能力是：

- 安全基础能力
- Flow 流聚合运行时
- Flow source 适配能力

从这个角度看，框架的核心收益不是“大而全”，而是把这些主线能力做成可接入、可验证、可交付的稳定模块。

## 7. 相关文档

- [架构优势](architecture-advantages.md)
- [Flow Job 显示名能力说明](flow-job-display-name.md)
- [快速开始](../getting-started/quick-start.md)
- [配置参考](../getting-started/config-reference.md)
- [Flow 流聚合使用指导](../guides/flow-usage-guide.md)
- [监控指标使用指南](../guides/metrics-guide.md)
