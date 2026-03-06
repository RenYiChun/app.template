# Flow 完成态收敛优化设计（isCompleted 轮询方案）

## 一、背景与问题

当前 Flow 任务完成依赖 `DefaultProgressTracker` 内部的完成判定逻辑：当 Source 已结束且生产、存储、消费均收敛时，任务进入完成态并触发注销/指标等副作用。

线上已出现问题：部分数据路径不经过既有的完成判定触发点，导致任务一直不结束，业务侧只能通过 `completionFuture` 或外部 stop 感知结束，但在特定分支会形成“永不完成”的悬挂。

本设计文档将完成态优化方案固化为统一的技术设计，并集成到项目现有文档体系中。

## 二、设计目标

- 将“完成态查询”标准化为 `isCompleted()` 轮询接口，替代对 `completionFuture` 的依赖。
- 保持完成判定由框架内部统一计算与收敛，业务侧不直接调用内部 `checkCompletion()`。
- 避免“触发点缺失导致不结束”，同时控制“条件短暂满足导致提前结束”的并发风险。
- 完成副作用（指标上报、自动注销等）保持幂等，仅发生一次。

## 三、非目标

- 不保证旧的 `getCompletionFuture()` 对外 API 继续可用（属于破坏性变更，需业务侧迁移）。
- 不引入全新的任务生命周期状态机；仍沿用现有 `FlowManager/FlowLauncher/ProgressTracker` 体系。

## 四、术语与核心计数

为便于讨论，以下名称与 `FlowProgressSnapshot`/`ProgressTracker` 对齐：

- `productionAcquired`：进入系统的原始请求量。
- `productionReleased`：成功入库到 Storage 的量。
- `inStorage`：当前仍在 Storage 中的数据量（如 Caffeine size）。
- `activeConsumers`：已获取消费许可、处于业务生命周期中的数量。
- `terminated`：数据彻底离开框架并释放资源的累计数量（物理终结）。
- `activeEgress/passiveEgress`：业务成功出口/被动出口（超时、驱逐、替换等）。
- `sourceFinished`：Source 已结束（Pull 模式已拉尽/Push 模式已封口并确认无在途 push）。

## 五、完成条件（Completion Condition）

完成条件沿用并明确为一条统一的、可重复计算的布尔表达式：

- `sourceFinished == true`
- `inStorage == 0`
- `activeConsumers == 0`
- `inProduction <= 0`，其中 `inProduction = productionAcquired - productionReleased`
- `pendingConsumer <= 0`，其中 `pendingConsumer = productionReleased - inStorage - activeConsumers - terminated`

其中 `pendingConsumer` 用于刻画“已入库但尚未进入可解释的终结路径”的缺口，避免因统计不一致而误判完成。

## 六、对外 API 与调用方式

### 6.1 ProgressTracker

对外提供：

- `boolean isCompleted()`
- `boolean isCompletionConditionMet()`
- `FlowProgressSnapshot getSnapshot()`

语义约束：

- `isCompleted()` 允许在“条件满足但尚未触发完成动作”的情况下，内部触发一次幂等完成收敛，然后返回最终完成态。
- `isCompletionConditionMet()` 为纯计算接口，仅反映当前条件是否满足，不产生副作用。

### 6.2 FlowLauncher

对外提供：

- `boolean isCompleted()`

语义：

- `FlowLauncher.isCompleted()` 作为业务侧轮询入口，委托给 tracker 的 `isCompleted()`。

### 6.3 FlowInlet（Push 模式）

对外提供：

- `boolean isCompleted()`

语义：

- Push 调用方在不直接持有 `FlowLauncher` 的情况下可轮询 `FlowInlet.isCompleted()`。

## 七、触发机制与兜底策略

完成收敛的触发应同时具备“事件驱动”与“轮询兜底”：

### 7.1 事件触发点

- `onGlobalTerminated(...)`：数据终结时触发完成检测。
- `onPassiveEgress(...)`：发生被动出口（TIMEOUT/EVICTION/REPLACE/SHUTDOWN 等）时触发完成检测，并将其计入 `terminated`。
- `markSourceFinished(...)`：Source 结束时置位，并允许完成检测继续推进。

### 7.2 轮询兜底

- 业务侧轮询 `FlowLauncher.isCompleted()` 或 `FlowInlet.isCompleted()`。
- `isCompleted()` 内部会在未完成时调用完成检测，从而弥补某些路径遗漏事件触发导致的“不结束”。

## 八、并发风险与控制

### 8.1 风险：提前完成（markSourceFinished 与并发 push 交叠）

Push 模式下可能出现窗口期：

- 业务线程调用 `markSourceFinished()` 的瞬间，仍有并发 `push(item)` 正在执行但尚未走到 `productionAcquired` 计数。
- 若此窗口内各计数短暂满足完成条件，可能提前触发完成动作，导致后续 push 进入异常状态或数据丢失。

### 8.2 控制：封口 + 在途排空

Push 入口引入两阶段封口语义：

- `markSourceFinished()` 先进入“封口中”状态，拒绝新的 `push`。
- 等待在途 push（已进入 push 但尚未完成 launch 的窗口）清零后，进入“封口完成”，再将 `sourceFinished` 置位并允许完成收敛。

该策略确保完成条件在 Source 结束语义上严格成立，避免“短暂满足”误判。

## 九、完成动作（Try Complete）与幂等

当完成条件满足时触发完成动作，动作包含但不限于：

- 锁定 `endTimeMillis`（仅一次）。
- 记录完成指标（如 `JOB_COMPLETED`）。
- 触发 `FlowManager.unregister(jobId)`（在非 stopped 情况下）。

幂等约束：

- 完成动作必须通过锁与结束标记确保仅执行一次。
- 即使多个触发点同时命中，最多发生一次“真正完成”副作用。

## 十、业务侧轮询建议

业务侧不再依赖 `completionFuture`，统一按以下模板轮询：

- `while (!launcher.isCompleted()) { sleep(interval); if (timeout) break; }`
- interval 建议 50ms~200ms，避免 busy-spin。
- 超时后可读取 `ProgressTracker.getSnapshot()` 做诊断（inStorage、activeConsumers、pendingConsumer、passiveEgressByReason 等）。

## 十一、测试策略

需要覆盖以下类别用例：

- Pull 模式：单流/多流，完成态收敛且 snapshot 指标一致。
- Push 模式：正常完成、stop 退出、替换/过期等被动出口场景。
- 并发风险：并发 push 与 markSourceFinished 交叠时不提前完成，且封口后拒绝新 push。
- 幂等性：完成副作用仅一次（指标/注销不重复）。

## 十二、与现有文档的关系

- 本文档归类为设计说明，放置于 `docs/design/`。
- Flow 的使用方式与代码示例仍以使用指南为准（`docs/guides/flow-usage-guide.md`）。

