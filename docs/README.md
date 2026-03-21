# App Template 文档

## 快速入门

- [快速开始](getting-started/quick-start.md) - 最小配置与项目创建
- [配置参考](getting-started/config-reference.md) - 完整配置选项

## 使用指南

- [Flow 流聚合](guides/flow-usage-guide.md) - 多路数据聚合与背压
- [监控指标](guides/metrics-guide.md) - Micrometer + Prometheus

## 设计说明

- [架构优势](design/architecture-advantages.md) - 模块化、异常契约、配置治理
- [选型收益](design/framework-benefits.md) - 开发效率与稳定性
- [加密与 Coder](design/encryption-and-coder.md) - 密码与配置解密
- [JSON 处理器](design/json-processor.md) - 可切换 JSON 实现
- [Flow 分层背压与受控超时存储](design/flow-layered-backpressure.md) - 让缓存参与背压链路，受控延期超时驱逐
- [Flow 分层背压实现蓝图](design/flow-layered-backpressure-implementation-blueprint.md) - 类、字段、方法签名与伪代码级落地约束
- [Flow 完成态收敛优化](design/flow-completion-isCompleted.md) - isCompleted 轮询与并发风险控制
- [Flow 消费出口统一设计](design/flow-egress-unification.md) - 统一 handleEgress 出口与迁移策略

## 参考

- [质量评分卡](reference/quality-scorecard.md)
- [安全审计报告](reference/security-audit-report.md)

## 资源

- [Grafana 仪表板](resources/grafana-dashboard.json)
- [OAuth2 请求示例](resources/http-client/oauth2.http)
