# 历史设计归档：Flow 限流与全局化方案

本文档已归档。它记录的是 Flow 资源限制从旧命名向当前实现演进时的设计稿，不再作为当前实现依据。

当前请改看：

- [Flow 配置参考](../../getting-started/config-reference.md)
- [监控指标使用指南](../../guides/metrics-guide.md)
- [Flow 流聚合使用指导](../../guides/flow-usage-guide.md)

归档原因：

- 仍大量使用 `consumer-concurrency`、`pending-consumer` 等旧配置键
- 指标名和当前 `FlowMetricNames` / 资源指标口径不一致
- 更适合作为历史方案追溯，而不是保留在活跃设计区
