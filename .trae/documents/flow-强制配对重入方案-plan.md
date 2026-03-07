# Flow 强制配对重入方案（Plan）

## 1. 目标与边界

* 目标场景：仅在 `needMatched=true` 的配对模式下，对“未配对即离开缓存”的数据执行重入。

* 重入触发：仅 `TIMEOUT`、`EVICTION`。

* 终止条件：达到最大重试次数后停止重入，进入 `onFailed(..., reason)`。

* 非目标：不改变非配对模式语义，不改变 `isMatched` 判断逻辑，不引入无限重试。

* 新增前置条件：仅“被标记为可重试”的数据允许重入；未标记数据直接走失败出口。

## 2. 配置设计

* 在 `TemplateConfigProperties.Flow.Limits.PerJob` 新增重试配置：

  * `mustMatchRetryEnabled`（默认 `false`）

  * `mustMatchRetryMaxTimes`（默认 `3`，必须 `>=0`）

  * `mustMatchRetryBackoffMill`（默认 `0`，必须 `>=0`；用于控制每次重入前等待毫秒数，0 表示立即重入）

  * `mustMatchRetryOnEviction`（默认 `true`）

  * `mustMatchRetryOnTimeout`（默认 `true`）

* 原因映射约定（用于回答“包括哪些原因”）：

  * `mustMatchRetryOnTimeout` 控制 `FailureReason.TIMEOUT`（如 Caffeine 过期、业务定义超时）。

  * `mustMatchRetryOnEviction` 控制 `FailureReason.EVICTION`（如 Caffeine 容量淘汰 SIZE、显式按容量策略驱逐）。

  * `REPLACE`、`MISMATCH`、`REJECT`、`SHUTDOWN` 不参与重入开关，始终不重入。

* 校验规则：

  * `mustMatchRetryEnabled=true` 时，`mustMatchRetryMaxTimes>=1`。

  * `mustMatchRetryBackoffMill>=0`。

* 配置生效范围：per-job，随 `registration.getFlow()` 走每个 job 配置。

## 3. 核心语义定义

* 计数口径：`retryRemaining` 表示“剩余可重入次数”，初始化为 `maxTimes` 或 `0`。

* 初始化判定（仅一次，避免每次离库都判断）：

  * 在 `FlowEntry` 创建时判断一次：`joiner.needMatched()==true && joiner.isRetryable(item, jobId)==true`。

  * 若为可重试数据：将 `retryRemaining` 初始化为 `maxTimes`。

  * 若不可重试数据：将 `retryRemaining` 初始化为 `0`（后续直接失败，不走重入）。

* 重入判定：

  * 原因满足配置开关（timeout/eviction）。

  * `retryRemaining > 0`。

  * job 未停止（`FlowManager.isStopped(jobId)==false`）。

* 重入前必须先做“二次配对检查”（防止重复入缓存）：

  * 对同一 `joinKey` 进入条带锁（复用现有 key stripe）。

  * 在锁内先查是否已有候选配对项；若存在则直接执行 `isMatched + onSuccess/onFailed(MISMATCH)`，不走重入。

  * 仅当锁内确认“当前无可配对项”时，才执行回灌缓存。

* 结果语义：

  * 允许重入：不调用 `onFailed`，仅回灌缓存等待下一次配对。

  * 不允许重入：调用 `onFailed(..., 原因)`，并进入现有被动出口统计。

## 4. 数据结构与扩展点

* `FlowEntry` 增加字段与方法（采用剩余次数模型）：

  * `int retryRemaining`

  * `void initRetryRemaining(int maxTimes)`（仅初始化一次）

  * `boolean tryConsumeOneRetry()`（CAS 方式：>0 时减 1 并返回 true）

  * `int getRetryRemaining()`

* 新增重试编排器（建议新类）：

  * `com.lrenyi.template.flow.internal.MatchRetryCoordinator`

  * 职责：统一做“是否可重入 + 重入执行 + 指标记录 + 回退到失败出口”。

* 扩展 `FlowJoiner` 接口（数据可重试标志）：

  * 新增默认方法：`boolean isRetryable(T item, String jobId)`。

  * 仅在 `FlowEntry` 创建时调用一次（不在每次离库时重复调用，降低开销）。

  * 业务侧可基于数据字段实现（例如：`item.retryableFlag==true`、白名单类型、来源渠道）。

## 5. 执行路径改造

### 5.1 Caffeine 路径

* 触发点：`CaffeineFlowStorage.onEntryRemoved(...)`。

* 当前行为是直接走终结/失败；改造为：

  1. 判断 `cause` 映射的失败原因；
  2. 调用 `MatchRetryCoordinator.tryRequeue(entry, reason, launcher)`；
  3. 若返回 `true`：执行重入并结束当前移除回调；
  4. 若返回 `false`：保留当前失败与统计逻辑。

* 注意：必须保持与 `claimLogic` 协同，避免“驱逐与配对并发双处理”；二次配对检查必须在同一 key 锁域内完成。

### 5.2 Queue 路径

* 不支持：Queue 存储不启用强制配对重入（按你的要求）。

* 约束：当 `needMatched=true` 且开启重入功能时，强制要求 storageType 使用 Caffeine（启动期校验失败则拒绝启动），避免语义不一致。

### 5.3 重入方式

* 重入不通过 `FlowJoinerEngine` 重新投递，避免污染生产侧计数。

* 直接回灌到对应 `FlowStorage` 的“安全入库通道”：

  * 新增内部方法：`requeue(FlowEntry<T> entry)`（仅框架内部可用）。

  * 该通道复用现有容量控制与全局 storage 许可逻辑，避免绕过限流。

* 若配置了 `mustMatchRetryBackoffMill>0`，通过 `storageEgressExecutor` 延迟重入。

### 5.4 重入时的“先匹配后回灌”逻辑（关键）

* 重入不是直接 `put` 回缓存，必须先做一次匹配尝试，流程固定为：

  1. 基于 `joinKey` 命中同一条带锁（与正常配对同一把 key 锁）。
  2. 在锁内读取当前缓存候选项：

     * 若存在候选项：直接执行 `isMatched(existing, incoming)`。

       * `true`：立即走 `onSuccess`，并按成功路径终结，不再重入。

       * `false`：两条走 `onFailed(..., MISMATCH)`，不再重入（避免脏数据互相重试）。

     * 若不存在候选项：才允许执行重入回灌。
  3. 回灌成功后退出；回灌失败（容量/许可）按现有失败回退逻辑处理。

* 一致性要求：

  * 该流程必须与现有 `findAndRemovePartner` 使用同一并发控制策略，防止“已被新数据配对但旧驱逐回调又重入”的竞态。

## 6. 计数与进度一致性

* 重入属于“生命周期延长”，不应重复记作新生产数据：

  * 不增加 `productionAcquired`。

  * 不增加 `productionReleased`。

* 仅在最终失败时记被动出口；重入过程可新增重试指标，不记失败计数。

* 确保 `FlowProgressSnapshot.getPendingConsumerCount()` 在重入场景无负数/漂移。

## 7. 指标与可观测性

* 新增计数器：

  * `app.template.flow.match.retry.attempted`（jobId, reason）

  * `app.template.flow.match.retry.succeeded`（jobId, reason）

  * `app.template.flow.match.retry.exhausted`（jobId, reason）

* 指标用途说明：

  * `retry.attempted`：发生“准备重入”的次数，用来判断该 job 是否频繁出现未配对离库。

  * `retry.succeeded`：真正“完成回灌”的次数，用来判断重入机制是否在生效。

  * `retry.exhausted`：达到最大重试后仍失败的次数，用来识别无效重入/脏数据/热 key。

  * 关键比值：
    `retry.succeeded / retry.attempted` 低，说明可回灌成功率差；
    `retry.exhausted / retry.attempted` 高，说明重试多数无收益，需调低次数或排查上游数据质量。

* 新增 gauge（可选）：

  * `app.template.flow.match.retry.inflight`（jobId）

* `retry.inflight` 用途：观察当前有多少条数据处于“等待重入执行”状态，防止重入队列堆积。

* 日志规范：

  * `jobId`、`joinKey`、`reason`、`retryRemaining`，便于定位热 key。

## 8. 风险控制与防护

* 防无限循环：`maxTimes` 强约束 + backoff（可选）+ 最终失败出口。

* 防雪崩：backoff 与限流共用，避免瞬时重入风暴。

* 防资源泄漏：重入失败时必须走统一失败出口，确保引用计数与许可释放闭环。

* 防语义歧义：重入仅对 `needMatched=true` 生效，其他模式不触发。

## 9. 兼容与发布策略

* 默认关闭（`mustMatchRetryEnabled=false`），灰度启用。

* 分阶段发布：

  1. 先上线指标与日志（不启用重入）；
  2. 小流量开启 `maxTimes=1` 观察；
  3. 再提升到目标次数与 backoff。

## 10. 测试计划

* 单元测试：

  * 重入判定矩阵（enabled/disabled、reason 开关、maxTimes 边界）。

  * `isRetryable=true/false` 分支：仅标记可重试的数据进入重入。

  * `retryRemaining` 递减到 0 的封顶行为。

  * backoff=0 与 >0 的执行路径。

* 集成测试：

  * `needMatched=true` 下 TIMEOUT/EVICTION 重入直到成功配对。

  * 达到 `maxTimes` 后进入 `onFailed`，且仅一次最终失败统计。

  * 多 job 并发下无死锁、无许可泄漏、无计数漂移。

* 回归测试：

  * 非配对模式行为不变。

  * 现有背压与 limits 指标不回退。

## 11. 实施步骤（落地顺序）

1. 增加配置项与校验（template-core）。
2. 扩展 `FlowEntry` 重试字段与原子方法。
3. 增加 `MatchRetryCoordinator`（判定/重入/回退）。
4. 实现“重入前二次配对检查”（key 锁内先配对、后重入）。
5. 接入 Caffeine `onEntryRemoved` 重入分支。
6. 增加重试指标与日志。
7. 完成单测与集成测试（仅 Caffeine）。
8. 灰度参数建议与运维观测面板补齐。

## 12. 验收标准

* 功能：强制配对数据在未配对离库时按配置重入，达到上限后退出循环。

* 过滤：未标记可重试的数据不重入，直接走失败出口。

* 稳定性：无无限重试、无许可泄漏、无引用计数泄漏、无吞吐骤降。

* 可观测：可从指标区分“重入成功”“重入耗尽”“最终失败”。

* 回归：非强制配对任务行为与性能基线不变。

