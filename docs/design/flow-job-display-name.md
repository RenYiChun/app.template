# Flow Job 显示名能力说明

本文档描述的是当前已经落地的 Flow job 显示名能力，不是待实现方案。

## 1. 解决的问题

Flow 运行时内部仍以 `jobId` 作为业务标识，但在监控和日志观察面上，原始 `jobId` 可能过长或不可读。  
当前实现允许在启动任务时额外传入 `displayName`，把它作为指标标签和日志展示名使用。

目标是：

- 不改变业务侧真正的 `jobId`
- 提升监控面可读性
- 保持未配置时的兼容行为

## 2. 当前行为

当前实现已经支持：

- `FlowManager.createLauncher(jobId, displayName, ...)`
- `FlowJoinerEngine.run(jobId, displayName, ...)`
- `FlowJoinerEngine.startPush(jobId, displayName, ...)`

运行时规则：

- 内部管理、停止、注销仍按原始 `jobId` 处理
- 指标标签优先使用 `displayName`
- 日志输出通过 `FlowLogHelper.formatJobContext(jobId, displayName)` 同时保留业务 `jobId`
- 未传 `displayName` 时，监控与日志继续使用原始 `jobId`

## 3. 实现位置

核心实现点：

- [FlowManager.java](../../template-flow/src/main/java/com/lrenyi/template/flow/manager/FlowManager.java)
  - 维护 `jobIdToDisplayName`
  - 创建 launcher 时解析 `metricJobId`
- [FlowJoinerEngine.java](../../template-flow/src/main/java/com/lrenyi/template/flow/engine/FlowJoinerEngine.java)
  - 对外暴露带 `displayName` 的 `run` / `startPush` 重载
- [FlowLauncher.java](../../template-flow/src/main/java/com/lrenyi/template/flow/internal/FlowLauncher.java)
  - 持有 `metricJobId`
- [DefaultProgressTracker.java](../../template-flow/src/main/java/com/lrenyi/template/flow/internal/DefaultProgressTracker.java)
  - 记录并暴露用于指标标签的 `metricJobId`
- [FlowLogHelper.java](../../template-flow/src/main/java/com/lrenyi/template/flow/util/FlowLogHelper.java)
  - 统一日志展示格式

## 4. 使用方式

```java
engine.run("order-match-20250323-001", "订单匹配", joiner, total, flowConfig);

engine.startPush("reconcile-push-001", "推送对账", joiner, total, flowConfig);
```

如果不需要展示名，仍可以继续使用原有重载：

```java
engine.run("order-match-20250323-001", joiner, total, flowConfig);
```

## 5. 边界与注意事项

- `displayName` 只用于展示，不应用来承载唯一业务主键。
- 不要把高基数字段放进 `displayName`，例如订单号、请求号、用户 ID。
- 适合放入 `displayName` 的是稳定、低基数、便于看板识别的名字，例如“订单匹配”“账单对齐”“推送回流”。

## 6. 当前判断

这项能力已经属于当前主线的一部分，应该保留在活跃设计区。  
它解释的是当前设计取舍和对外观察口径，而不是历史迁移过程。
