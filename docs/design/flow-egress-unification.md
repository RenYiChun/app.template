# Flow 消费出口统一到 handleEgress 的技术设计

## 一、背景与问题

当前 Flow 框架中，“数据离开存储”的处理路径分散在多处：

- Caffeine：驱逐、超时、替换、overflow、配对后清理等场景分别走 `handlePassiveFailure`、内联失败逻辑或局部处理。
- Queue：drain 的部分场景走 `handleEgress`，队列满拒绝与 shutdown 清空存在业务回调不一致。
- 结果是出口语义分裂，指标与进度统计容易出现口径差异，后续维护成本持续上升。

同时，`removalListener/compute` 等缓存上下文中若进入可重试路径，可能触发 `preRetry -> tryRequeue` 的再次入缓存，存在重入风险。

## 二、设计目标

- 将所有“消费出口”统一收敛到 `handleEgress(key, entry, reason, skipRetry)`。
- 用 `skipRetry` 显式区分“允许重试”和“禁止重试”的上下文，避免缓存回调中的重入风险。
- 统一业务消费回调模型、指标模型与 ProgressTracker 口径，降低多实现分叉。
- 保持 `releaseGlobalStorage` 职责在调用方，不在 `handleEgress` 隐式释放，避免资源职责交叉。

## 三、非目标

- 不在本阶段引入新的任务状态机。
- 不在本阶段改变 Flow 的背压策略与全局/每 Job 限流模型。
- 不在本阶段改变“Queue TIMEOUT 可重试重入”的既有语义约束。

## 四、核心设计

### 4.1 统一入口

在 `AbstractEgressFlowStorage` 扩展统一入口：

- `handleEgress(String key, FlowEntry<T> entry, EgressReason reason, boolean skipRetry)`
- 兼容入口：`handleEgress(key, entry, reason)` 默认委托到 `skipRetry=false`

统一行为：

- `skipRetry=true`：直接走被动出口（`handlePassiveFailure`），不进入重试分支。
- `skipRetry=false`：保留现有逻辑（重试或 finalizer），用于允许重入的场景。

### 4.2 消费原因模型

将 `FailureReason` 升级为 `EgressReason`，用于统一描述“数据为何被消费/离库”：

- 配对消费：`PAIR_MATCHED`
- 单条消费：`SINGLE_CONSUMED`
- 其余出口：`TIMEOUT`、`EVICTION`、`REPLACE`、`OVERFLOW_DROP_*`、`MISMATCH`、`REJECT`、`SHUTDOWN`、`CLEARED_AFTER_PAIR_SUCCESS`

约束：

- `PAIR_MATCHED` 对应双条消费回调。
- 其余原因均走单条消费回调，并带 reason 透传。

### 4.3 统一消费执行器

新增 `FlowEgressHandler`，只负责“消费执行”而非调度提交：

- `performPairConsumed(partner, entry)`：回调、指标、进度更新（同步）
- `performSingleConsumed(entry, reason)`：回调、指标、进度更新（同步）

边界：

- 不负责 `submit`、`claimLogic`、`signalRelease`。
- 由 `MatchedPairProcessor`、`FlowFinalizer`、`handlePassiveFailure` 在各自上下文调用。

### 4.4 FlowJoiner 回调模型收敛

目标回调收敛为两类：

- `onPairConsumed(T existing, T incoming, String jobId)`
- `onSingleConsumed(T item, String jobId, EgressReason reason)`

迁移建议（避免破坏性升级）：

- 先引入桥接默认实现，保留旧方法一段迁移周期。
- 文档与示例优先迁移到新回调。
- 在下一主版本删除旧回调。

## 五、存储实现改造方案

### 5.1 CaffeineFlowStorage

以下场景统一改为 `handleEgress(..., true)`：

- `onSlotRemoved` 的 `SHUTDOWN`
- `processEvictedSlot` 未匹配条目
- `handleOverflowDropped`（入缓存前丢弃）
- `REPLACE` 被替换条目
- `CLEARED_AFTER_PAIR_SUCCESS` 清理条目

并补齐 key 透传：

- `processEvictedSlot` 签名增加 `key`
- `handleOverflowDropped` 签名增加 `key`
- `CaffeinePairingContext` 的 overflow 回调改为包含 `key`

### 5.2 QueueFlowStorage

以下场景统一改为 `handleEgress(..., true)`：

- `doDeposit` 队列满拒绝：`REJECT`
- `drain` 中 launcher 缺失或异常兜底：`SHUTDOWN`
- `shutdown()` 清空剩余条目：`SHUTDOWN`

并保持资源职责：

- `deposit(false)` 的 `globalStorage` 回滚仍由 `FlowLauncher` 执行。
- Queue 拒绝场景只负责上报 `REJECT` 出口，不重复释放全局存储额度。

## 六、资源与依赖注入

在 `FlowLauncherFactory` 统一创建并注入 `FlowEgressHandler`：

- 注入到 `FlowFinalizer`
- 注入到 `FlowResourceContext`（供 `FlowLauncher` stopped 分支使用）
- 传入 Storage 工厂并下发至 `AbstractEgressFlowStorage` 子类
- 传入 `MatchedPairProcessor`/`CaffeinePairingContext` 所需上下文

## 七、指标与进度模型调整

- 指标注释及错误类型文案同步到新回调语义：
  - `onSuccess_failed -> onPairConsumed_failed`
  - `onConsume_failed -> onSingleConsumed_failed`
- `ProgressTracker.onPassiveEgress(FailureReason)` 升级为 `onPassiveEgress(EgressReason)`。
- `DefaultProgressTracker` 的 `passiveEgressByReason` 改为 `EgressReason` 维度。
- `FlowProgressSnapshot` 文档说明同步为“按消费原因统计”。

## 八、兼容性与发布策略

本设计涉及接口级变更，建议按两阶段发布：

### 阶段 A（兼容阶段）

- 引入 `EgressReason` 与 `FlowEgressHandler`
- `FlowJoiner` 新旧回调并存，默认桥接
- 引擎内部优先调用新回调，旧回调保留兼容

### 阶段 B（收敛阶段）

- 移除旧回调及旧文案
- 全量测试、文档与示例切换到新模型

## 九、测试与验收

必须覆盖以下场景：

- Caffeine：`EVICTION/TIMEOUT/REPLACE/OVERFLOW_DROP/CLEARED_AFTER_PAIR_SUCCESS`
- Queue：`REJECT`、`drain TIMEOUT`、`drain SHUTDOWN`、`shutdown()` 清空
- 配对路径：`PAIR_MATCHED` 与 `MISMATCH`
- 重试路径：`skipRetry=true` 时不触发重入，`skipRetry=false` 保持既有重试语义
- 指标与进度：active/passive 计数、reason 分桶、错误类型标签一致

验收标准：

- 所有离库场景均经 `handleEgress` 统一入口。
- 被动离库场景不再进入 retry 重入链路。
- 业务可在单条回调中基于 reason 做稳定分流处理。

## 十、风险与应对

- 接口变更风险：采用两阶段兼容迁移，避免一次性破坏。
- 计数口径风险：明确“唯一记账者”，防止重复计数。
- 资源回滚风险：保持 `releaseGlobalStorage` 职责清晰，重点回归 `REJECT` 与异常分支。

## 十一、相关文档

- [Flow 使用指南](../guides/flow-usage-guide.md)
- [Flow 多值模式指南](../guides/flow-multi-value-guide.md)
- [Flow 完成态收敛优化](flow-completion-isCompleted.md)
- [Flow 配置优化与全局化设计](flow-limits-globalization.md)
