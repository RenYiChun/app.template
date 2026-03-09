# Flow 分层背压与受控超时存储设计

## 一、背景与问题

当前 Flow 引擎的背压链路只覆盖了生产前检查与消费侧许可控制，但没有真正覆盖 Caffeine 缓存的超时驱逐路径。

现状见以下实现：

- [FlowLauncher.java](D:/github.com/app.template/template-flow/src/main/java/com/lrenyi/template/flow/internal/FlowLauncher.java)：生产侧在 `awaitBackpressure()` 中检查 `storage.size()`、消费许可和 pending consumer。
- [BackpressureController.java](D:/github.com/app.template/template-flow/src/main/java/com/lrenyi/template/flow/internal/BackpressureController.java)：背压条件成立时阻塞生产者。
- [CaffeineFlowStorage.java](D:/github.com/app.template/template-flow/src/main/java/com/lrenyi/template/flow/storage/CaffeineFlowStorage.java)：缓存配置使用 `maximumSize + expireAfterWrite + Scheduler.systemScheduler()`，并在 `removalListener` 中处理超时和容量驱逐。
- [FlowFinalizer.java](D:/github.com/app.template/template-flow/src/main/java/com/lrenyi/template/flow/internal/FlowFinalizer.java)：数据离库后进入 finalizer/consumer 执行链路。

问题在于：

1. `expireAfterWrite` 到点后由 Caffeine 自己触发驱逐。
2. `maximumSize` 达到上限时由 Caffeine 自己决定淘汰。
3. 驱逐线程不感知 Flow 的下游压力状态。
4. 一旦驱逐发生，数据离开缓存，缓存占用下降，生产侧背压条件随之减弱甚至解除。

因此当前系统实际表现为：

- 下游消费慢时，缓存中的超时数据仍会被异步驱逐。
- 驱逐释放了缓存空间，但并不代表下游真的有能力承接。
- 生产者看到缓存“有空间”，继续生产。
- 背压没有逐层传导到缓存层，只在生产入口和消费许可层生效。

这与期望目标不一致。期望目标是：

- 当下游压力过大时，超时数据先不要离开缓存。
- 让缓存继续占着容量。
- 当缓存最终写满时，生产者立即被缓存容量本身反压。
- 上游数据源因此减速或阻塞，形成真正的“层层背压”。

## 二、设计目标

- 将缓存层纳入背压链路，实现“消费慢 -> 缓存不释放 -> 缓存写满 -> 生产阻塞 -> 上游背压”。
- 将“到期”从“立即驱逐”改为“获得驱逐资格”，是否离库由 Flow 自己决定。
- 去除 Caffeine 在 TTL 与容量上的自动淘汰主导权，改为 Flow 自己控制离库时机。
- 保持现有 `FlowLauncher`、`BackpressureController`、`FlowFinalizer`、`RetryHandler` 主体职责不变，只重构存储与超时驱逐链路。
- 保留 `TIMEOUT`、`EVICTION`、`REPLACE`、`SHUTDOWN` 等现有出口语义，但改变它们的触发时机与责任边界。
- 增加完整的状态机、指标、配置和迁移步骤，确保可分阶段落地。

## 三、非目标

- 本阶段不重写 `FlowJoiner`、`ProgressTracker`、`FlowEgressHandler` 的业务回调模型。
- 本阶段不改变 Queue 存储的核心 drain 模型，Queue 仅做配套适配。
- 本阶段不尝试保留“Caffeine 自动过期 + 自动容量淘汰”的兼容行为。
- 本阶段不处理跨进程/分布式背压，只处理单 JVM 内 Flow 引擎行为。

## 四、核心结论

为了实现分层背压，必须放弃以下两项由 Caffeine 自动控制的机制作为主路径：

- `expireAfterWrite`
- `maximumSize`

原因如下：

1. `expireAfterWrite` 的设计目标是“到点尽快过期”，而不是“到点后先检查下游能否承接”。
2. `maximumSize` 的设计目标是“缓存不能无限增长”，而不是“缓存满时让生产者阻塞等待”。
3. 只要还保留这两项自动行为，缓存空间就可能被异步释放，背压链路就会在缓存层断裂。

因此本设计采用“受控超时、显式容量、业务态驱逐”的新模型：

- TTL 只负责标记“可驱逐”。
- 容量上限由 Flow 显式检查与显式 permit 控制。
- 是否真正离库由 `EvictionCoordinator` 结合下游压力判断。
- 缓存中的超时数据在压力大时可以继续滞留。

## 五、术语定义

- `softTimeout`：软超时，到达后仅表示“具备被驱逐资格”。
- `hardTimeout`：硬超时，到达后必须强制离库，防止永久滞留。
- `expiry-ready`：数据已到软超时，等待驱逐裁决。
- `deferred-expiry`：因下游压力过大，本次驱逐被延期。
- `storage permit`：每条 entry 占用的缓存容量许可。
- `slot`：同一个 `joinKey` 下的一组 `FlowEntry` 容器，沿用现有 `FlowSlot` 语义。
- `egress`：数据离开存储进入终结或失败路径。
- `downstream pressure`：消费并发、pending consumer、finalizer backlog 等下游承压状态。

## 六、现状缺陷拆解

### 6.1 背压判断点与缓存驱逐点分裂

当前生产侧先在 `BackpressureController.awaitSpace()` 中判断：

- 缓存是否已满
- 消费许可是否耗尽
- per-job pending consumer 是否超限
- global pending consumer 是否超限

但是驱逐发生在 Caffeine 的内部调度线程中，不经过这套判定。

结果是：

- 生产侧认为缓存是系统的一个有界中间层。
- 实际上缓存的空间释放不受 Flow 统一控制。

### 6.2 缓存释放与下游承接能力脱钩

当前超时驱逐后，`processEvictedSlot()` 会继续尝试：

- 全量配对
- passive egress
- retry 或 finalizer

但这些动作发生时，缓存空间已经释放。此时即使消费侧已饱和，容量还是先回来了。

### 6.3 `pending slot acquire timeout` 会进一步削弱背压

当前 [FlowFinalizer.java](D:/github.com/app.template/template-flow/src/main/java/com/lrenyi/template/flow/internal/FlowFinalizer.java) 中，若获取 pending consumer slot 超时，会记录日志并“继续提交”。

这意味着：

- pending consumer 限制不是硬约束。
- 在高压时 finalizer 仍可能继续堆积。
- 即使生产侧被配置为依赖 pending consumer 做背压，这条链也会被 finalizer 的兜底投递削弱。

### 6.4 `maximumSize` 不等于可背压容量

当前 `maximumSize` 是缓存库内部的淘汰阈值，不是 Flow 的严格容量边界。它的语义是“超过后淘汰”，不是“超过后阻塞生产者”。

而分层背压需要的容量语义是：

- 未离库的数据必须继续占用容量。
- 只有真正 egress 后才释放容量。
- 容量满后，生产者必须等待，不允许自动让位。

## 七、目标状态下的总体架构

新架构将存储层实现调整为“受控定时存储”（例如 `BoundedTimedFlowStorage`）：

1. 存储层保存数据。
2. 超时调度层只负责发现“哪些数据到期了”。
3. 驱逐协调层负责判断“现在能不能让这些数据离库”。
4. 背压层继续根据容量、pending、消费许可做统一阻塞。
5. 离库后才释放容量许可并唤醒生产者。

整体链路如下：

```text
生产者 launch
  -> BackpressureController.awaitSpace()
  -> 获取 storage permit
  -> 数据写入 slot
  -> 注册 soft/hard timeout

到达 soft timeout
  -> EvictionCoordinator 扫描到期项
  -> DownstreamPressureEvaluator 判断下游压力
  -> 若过载：延期，不离库，不释放 permit
  -> 若可承接：执行 timeout egress

到达 hard timeout
  -> 无条件强制离库
  -> 释放 storage permit
  -> signalRelease()
```

## 八、核心设计原则

### 8.1 到期不等于离库

TTL 不再代表“数据立刻离开缓存”，而是代表“数据进入驱逐候选态”。

### 8.2 真实离库之前不得释放容量

任何 entry 在以下动作发生之前，必须继续占用缓存容量：

- 配对成功离库
- timeout 被允许执行离库
- hard timeout 强制离库
- replace/shutdown 等显式移除

### 8.3 驱逐必须服从下游背压

软超时驱逐不是只由“时间到了”决定，而是由以下条件共同决定：

- 时间到了
- 下游当前有能力承接

### 8.4 必须存在硬上限兜底

如果只允许延期、不允许强制离库，那么在极端消费故障下整个缓存会永久卡死。因此必须存在：

- `hardTimeout`
- 或最大延期次数 / 最大保留时长

本文推荐采用 `softTimeout + hardTimeout` 双阈值语义。

## 九、组件设计

### 9.1 `BoundedTimedFlowStorage`

这是 Flow 存储层的目标演进形态。可以通过重构现有存储实现，或直接以新类方式接入并逐步替换旧实现。

职责：

- 管理 `joinKey -> FlowSlot` 映射。
- 显式维护存储占用数量。
- 为 entry 注册软超时和硬超时信息。
- 与 `EvictionCoordinator` 协同处理延期和真正离库。
- 在真正离库后释放全局/每 Job 存储容量并唤醒背压。

不再负责：

- 将 TTL 和容量交给 Caffeine 内部自动驱逐。

### 9.2 `EvictionCoordinator`

新增组件，负责统一处理到期数据。

职责：

- 扫描所有到达 `softTimeout` 的 entry 或 slot。
- 调用 `DownstreamPressureEvaluator` 判断当前是否允许 timeout egress。
- 若允许，则发起 timeout egress。
- 若不允许，则根据延期策略重新调度下一次检查。
- 若达到 `hardTimeout`，无条件强制驱逐。

执行模型：

- 使用单独的 `ScheduledExecutorService` 或 `DelayQueue + dedicated thread`。
- 推荐使用单线程协调器，保证裁决顺序一致，减少并发重复驱逐问题。
- 真正的业务 egress 仍走既有 consumer/finalizer 执行器，不在协调线程中执行业务回调。

### 9.3 `DownstreamPressureEvaluator`

新增组件，负责统一判断“下游是否过载”。

输入来源：

- per-job consumer semaphore
- global consumer semaphore
- per-job pending consumer count
- global pending consumer count
- pending consumer slot semaphore
- finalizer/backlog 指标

输出：

- `ALLOW_EVICT`
- `DEFER_EVICT`
- `FORCE_EVICT`

其中：

- `ALLOW_EVICT`：允许 soft timeout 离库
- `DEFER_EVICT`：本次软超时延期
- `FORCE_EVICT`：用于 hard timeout 或策略强制离库

### 9.4 `DeferredExpiryPolicy`

新增策略对象，负责决定延期时长。

职责：

- 给出首次延期时长
- 给出退避策略
- 控制最大延期时长或最大重试次数

推荐默认策略：

- 初始延期 `100ms`
- 之后指数退避：`200ms -> 400ms -> 800ms -> 1000ms`
- 单次最大延期 `1000ms`
- 总延期时长不得超过 `hardTimeout - softTimeout`

### 9.5 `ExpiryIndex`

新增时间索引结构，用于保存待检查项。

推荐方案一：

- `DelayQueue<ExpiryTask>`

推荐方案二：

- 最小堆 + 调度线程

每个 `ExpiryTask` 包含：

- `jobId`
- `joinKey`
- `slotVersion` 或 `entryId`
- `softExpireAt`
- `hardExpireAt`
- `nextCheckAt`
- `deferCount`

### 9.6 `SlotState` / `EntryState`

需要显式状态机，避免重复驱逐与竞态。

推荐状态：

- `ACTIVE`
- `SOFT_EXPIRED`
- `DEFERRED`
- `EGRESSING`
- `REMOVED`

语义：

- `ACTIVE`：正常等待匹配
- `SOFT_EXPIRED`：达到软超时，等待裁决
- `DEFERRED`：本次因背压延期
- `EGRESSING`：正在执行离库，不允许重复提交
- `REMOVED`：已离开存储

## 十、数据模型设计

### 10.1 `StoredEntryEnvelope`

建议为进入存储的每个 `FlowEntry` 增加包装对象：

```java
final class StoredEntryEnvelope<T> {
    String entryId;
    String jobId;
    String joinKey;
    FlowEntry<T> entry;
    long storedAt;
    long softExpireAt;
    long hardExpireAt;
    long nextCheckAt;
    int deferCount;
    EntryState state;
    long version;
}
```

说明：

- `entryId`：唯一标识，用于避免重复驱逐。
- `version`：用于识别旧的延期任务是否已失效。
- `nextCheckAt`：当前下一次被协调器扫描的时间。

### 10.2 `FlowSlot`

现有 `FlowSlot` 可继续使用，但建议将内部元素从 `FlowEntry<T>` 替换为 `StoredEntryEnvelope<T>`，或新增并行结构进行映射。

必须满足：

- 可按 pairing strategy 取出候选项。
- 可标记其中某些元素已进入 `EGRESSING`。
- 支持多值模式和 `maxPerKey` 约束。

### 10.3 容量计数

容量计数需要明确“按 entry 计数”还是“按 slot 计数”。

当前 `storage.maxCacheSize()` 与 `cache.estimatedSize()` 语义更接近“key 数量”。但分层背压建议改成“entry 数量”。

原因：

- 多值模式下，一个 key 对应多个 entry。
- 真实内存占用和下游负担更接近 entry 数，而不是 key 数。
- 如果仍按 key 数控制容量，多值模式下会失真。

因此建议：

- `per-job.storage` 统一解释为“每 Job 最大 entry 数”
- `global.storage` 统一解释为“全局最大 entry 数”

若必须保留兼容，也应明确：

- 旧语义：key 容量
- 新语义：entry 容量
- 在发布说明中标注破坏性变化

## 十一、状态机设计

### 11.1 单条 entry 状态机

```text
NEW
  -> STORED(ACTIVE)

ACTIVE
  -> MATCHED_EGRESSING
  -> SOFT_EXPIRED
  -> REPLACED_EGRESSING
  -> SHUTDOWN_EGRESSING

SOFT_EXPIRED
  -> DEFERRED
  -> TIMEOUT_EGRESSING

DEFERRED
  -> SOFT_EXPIRED
  -> HARD_TIMEOUT_EGRESSING

*_EGRESSING
  -> REMOVED
```

约束：

- 从 `EGRESSING` 到 `REMOVED` 是单向不可逆。
- 任意状态进入 `EGRESSING` 必须通过 CAS 或持锁保护，确保只有一个线程成功。

### 11.2 slot 状态补充

若采用 slot 级驱逐，需要 slot 也有状态：

- `ACTIVE`
- `SCANNING`
- `DRAINING`
- `REMOVED`

用于防止：

- 同一 key 被协调线程与配对线程同时处理
- `invalidate(key)` 与 timeout drain 互相打架

## 十二、时序设计

### 12.1 正常入库时序

```text
Producer
  -> BackpressureController.awaitSpace()
  -> acquire per-job/global storage permit
  -> storage.deposit(entry)
  -> register expiry task(soft/hard timeout)
  -> return
```

要点：

- 只有 `deposit` 成功才算真正占用存储。
- `deposit` 失败必须回滚 permit。
- 注册 expiry task 必须与存储写入保持原子可见性。

### 12.2 软超时但下游过载时序

```text
EvictionCoordinator
  -> scan expiry-ready entry
  -> DownstreamPressureEvaluator.evaluate(jobId)
  -> result = DEFER_EVICT
  -> mark entry DEFERRED
  -> compute nextCheckAt
  -> re-enqueue expiry task
  -> entry stays in storage
  -> storage permit unchanged
```

效果：

- 数据继续占着缓存容量。
- 生产者持续感知缓存压力。

### 12.3 软超时且允许离库时序

```text
EvictionCoordinator
  -> evaluate(jobId)
  -> result = ALLOW_EVICT
  -> mark entry/slot EGRESSING
  -> submit timeout egress
  -> on actual removal:
       release global/per-job storage permit
       signalRelease()
```

### 12.4 达到硬超时时序

```text
EvictionCoordinator
  -> now >= hardExpireAt
  -> mark EGRESSING
  -> force timeout egress
  -> release permit after actual removal
```

### 12.5 配对成功时序

```text
Deposit / Retry Path
  -> find partner
  -> mark two entries EGRESSING
  -> remove from slot
  -> cancel expiry tasks logically(by version/state)
  -> processMatchedPair()
  -> release 2 storage permits
  -> signalRelease()
```

### 12.6 shutdown 时序

```text
FlowManager.stop / registry shutdown
  -> storage.shutdown()
  -> mark all entries EGRESSING
  -> perform shutdown egress
  -> release all storage permits
  -> clear expiry index
```

## 十三、下游压力判断规则

### 13.1 判定输入

`DownstreamPressureEvaluator` 需要以下数据：

- `jobConsumerAvailablePermits`
- `globalConsumerAvailablePermits`
- `perJobPendingConsumerCount`
- `perJobPendingConsumerLimit`
- `globalPendingConsumerCount`
- `globalPendingConsumerLimit`
- `pendingConsumerSlotAvailablePermits`
- `consumerExecutorQueueDepth` 或等效 backlog
- `finalizerSubmissionFailure/timeout` 统计

### 13.2 默认判定规则

默认建议如下：

- 满足任一条件则 `DEFER_EVICT`

条件：

1. `jobConsumerAvailablePermits <= 0`
2. `globalConsumerAvailablePermits <= 0`，且全局限制已启用
3. `perJobPendingConsumerCount >= perJobPendingConsumerLimit`
4. `globalPendingConsumerCount >= globalPendingConsumerLimit`，且全局限制已启用
5. `pendingConsumerSlotAvailablePermits <= 0`
6. consumer/finalizer backlog 超过阈值

否则：

- `ALLOW_EVICT`

特殊规则：

- `now >= hardExpireAt` 时直接 `FORCE_EVICT`

### 13.3 关于 finalizer 的建议

当前 finalizer 在 pending slot acquire 超时后仍可能继续提交。这会导致 `pendingConsumer` 限制被软化。

建议调整为：

- 默认严格模式：获取 pending slot 超时则不继续提交，转为延期或错误出口。
- 兼容模式：保留“提交 anyway”，但 `DownstreamPressureEvaluator` 应将最近一段时间的 acquire timeout 视为高压信号。

推荐最终目标是严格模式。

## 十四、容量与许可模型

### 14.1 容量获取时机

生产侧流程调整为：

1. `awaitBackpressure()`
2. 获取 per-job/global storage permit
3. 执行 `deposit`
4. 若失败则回滚 permit

### 14.2 容量释放时机

容量释放只能发生在“真实离库”之后。

真实离库包括：

- 配对成功
- timeout egress 成功进入终结链路
- replace 被替换
- overflow 丢弃
- shutdown 清空
- source finished drain

不得在以下时机释放：

- 仅仅达到 soft timeout
- 仅仅进入延期态
- 协调器刚决定允许驱逐但实际还没从 slot 移除

### 14.3 `BackpressureController` 的新判断

建议新增统一存储状态接口：

```java
interface FlowStoragePressureView {
    long usedEntries();
    long entryLimit();
    boolean isFull();
}
```

`BackpressureController` 不再依赖具体缓存实现的 `estimatedSize()`（或其他近似容量接口），而是依赖显式计数器。

### 14.4 `signalRelease()` 触发点

只有在以下时机调用：

- entry 真正从存储移除
- 或一批 entry 在 slot drain 后完成移除

不能在“允许驱逐但未移除”时提前调用。

## 十五、与现有类的改造方案

### 15.1 存储实现（`BoundedTimedFlowStorage`）

目标：

- 将旧版依赖底层缓存库 `expireAfterWrite` / `maximumSize` / 调度线程的自动驱逐逻辑全部下线
- 统一由 Flow 自己控制 soft/hard timeout 与容量释放时机

保留或重用的部分：

- `FlowSlot`
- key stripe lock
- `PairingStrategy`
- `MatchedPairProcessor`
- 既有 `handleEgress` 统一出口模型

新职责（由 `BoundedTimedFlowStorage` 承担）：

- 显式维护 entry 数量
- 注册和更新 slot 级 expiry token
- 与 `EvictionCoordinator` 配合处理 soft/hard timeout

### 15.2 `BackpressureController`

改造点：

- `cacheFull` 改为依据 `usedEntries >= entryLimit`
- 增加一个下游压力评估便捷方法，供 `EvictionCoordinator` 复用
- 保留现有等待、超时、指标记录逻辑

建议补充接口：

```java
BackpressureSnapshot snapshot();
boolean isDownstreamOverloaded();
```

其中 `BackpressureSnapshot` 包含：

- storageUsed/storageLimit
- consumer permits
- pending counts
- overload reason set

### 15.3 `FlowLauncher`

改造点：

- 明确 storage permit 的获取与回滚边界
- 不再假设“缓存到期会自动释放空间”
- 当 `awaitBackpressure()` 因容量满阻塞时，意味着缓存中可能包含大量延期超时数据，这是预期行为

### 15.4 `FlowFinalizer`

改造点：

- 明确 pending slot acquire 失败后的策略
- 推荐增加严格模式配置
- 对 acquire timeout、提交失败、排队过深输出指标，供 `DownstreamPressureEvaluator` 使用

### 15.5 `FlowResourceRegistry`

改造点：

- 新增 `evictionCoordinatorExecutor` 或 `ScheduledExecutorService`
- 注册相关 gauge/counter
- 在 shutdown 期间按顺序关闭协调器与消费者执行器

### 15.6 `FlowStorage`

建议扩展接口：

```java
default long usedEntries() { return size(); }
default long entryLimit() { return maxCacheSize(); }
default boolean supportsDeferredExpiry() { return false; }
```

若做更彻底设计，可增加：

```java
FlowStoragePressureView pressureView();
```

## 十六、配置设计

### 16.1 新增配置项

建议在 `TemplateConfigProperties.Flow.PerJob` 下新增：

| 配置路径 | 默认值 | 含义 |
|----------|--------|------|
| `flow.limits.per-job.soft-timeout-mill` | 与 `cache-ttl-mill` 相同 | 达到后进入可驱逐态 |
| `flow.limits.per-job.hard-timeout-mill` | `soft-timeout-mill * 6` | 达到后必须强制离库 |
| `flow.limits.per-job.expiry-defer-initial-mill` | 100 | 首次延期时长 |
| `flow.limits.per-job.expiry-defer-max-mill` | 1000 | 单次最大延期时长 |
| `flow.limits.per-job.expiry-defer-backoff-multiplier` | 2.0 | 延期退避倍数 |
| `flow.limits.per-job.strict-pending-consumer-slot` | true | pending slot 获取失败时是否禁止继续提交 |
| `flow.limits.per-job.eviction-batch-size` | 128 | 单次协调扫描处理的最多 entry 数 |
| `flow.limits.per-job.storage-count-by-entry` | true | 容量是否按 entry 计数 |

说明：

- `cache-ttl-mill` 可以保留一版兼容，但语义应逐步迁移为 `soft-timeout-mill`。
- 若同时配置 `cache-ttl-mill` 与 `soft-timeout-mill`，以 `soft-timeout-mill` 优先。

### 16.2 全局配置

可选新增：

| 配置路径 | 默认值 | 含义 |
|----------|--------|------|
| `flow.limits.global.eviction-coordinator-threads` | 1 | 驱逐协调线程数 |
| `flow.limits.global.eviction-scan-interval-mill` | 50 | 当使用轮询而非 DelayQueue 时的扫描间隔 |

推荐默认仍使用单线程协调器。

### 16.3 配置校验

启动时必须校验：

- `soft-timeout-mill > 0`
- `hard-timeout-mill >= soft-timeout-mill`
- `expiry-defer-initial-mill > 0`
- `expiry-defer-max-mill >= expiry-defer-initial-mill`
- `eviction-batch-size > 0`

## 十七、指标设计

### 17.1 新增指标

建议新增以下指标：

| 指标名 | 标签 | 类型 | 含义 |
|--------|------|------|------|
| `app.template.flow.storage.expiry.ready` | `jobId` | Gauge | 已达到软超时、等待裁决的 entry 数 |
| `app.template.flow.storage.expiry.deferred` | `jobId` | Gauge | 当前处于延期态的 entry 数 |
| `app.template.flow.storage.expiry.defer.total` | `jobId` | Counter | 延期累计次数 |
| `app.template.flow.storage.expiry.force.total` | `jobId` | Counter | 硬超时强制离库累计数 |
| `app.template.flow.storage.expiry.allow.total` | `jobId` | Counter | 软超时被允许离库累计数 |
| `app.template.flow.storage.expiry.delay.duration` | `jobId` | Timer/Distribution | 从 soft timeout 到真正离库的延迟分布 |
| `app.template.flow.storage.entries.used` | `jobId,storageType` | Gauge | 当前 entry 占用数 |
| `app.template.flow.storage.entries.limit` | `jobId,storageType` | Gauge | 当前 entry 上限 |
| `app.template.flow.eviction.coordinator.queue.size` | — | Gauge | 过期协调队列长度 |
| `app.template.flow.eviction.coordinator.run.duration` | — | Timer | 协调线程每轮处理耗时 |
| `app.template.flow.downstream.overloaded.total` | `jobId,reason` | Counter | 因各类过载原因导致延期的累计次数 |

### 17.2 过载原因标签

`reason` 建议枚举为：

- `consumer_permits_exhausted`
- `global_consumer_permits_exhausted`
- `pending_consumer_overflow`
- `global_pending_consumer_overflow`
- `pending_slot_exhausted`
- `consumer_backlog_overflow`
- `finalizer_timeout_recently`

### 17.3 现有指标解释调整

现有 `limits.storage.used/limit` 若仍保留原名，需要更新文档说明其统计对象是：

- 旧版：cache key 数
- 新版：entry 数

若不想改变旧指标语义，则新增 `entries.used/limit` 更稳妥。

## 十八、并发控制与一致性

### 18.1 一致性要求

必须保证：

- 同一个 entry 只能离库一次
- 同一个 entry 的 permit 只能释放一次
- 同一个 expiry task 即使重复触发，也不能重复处理

### 18.2 推荐手段

- key 级 stripe lock 继续保留
- entry 状态使用 CAS 或持锁更新
- expiry task 附带 `entryId + version`
- 执行前先校验：
  - entry 是否仍在 slot 中
  - version 是否匹配
  - state 是否允许从当前态进入 `EGRESSING`

### 18.3 关于 `DelayQueue` 的旧任务问题

延期后旧任务无法从队列中高效删除时，可以接受“逻辑失效、物理保留”策略：

- 每次延期时将 `version + 1`
- 新任务使用新 version 入队
- 旧任务出队后发现 version 不匹配，直接丢弃

### 18.4 生产与驱逐竞争

场景：

- 某个 key 的 slot 正在进行配对
- 同时协调器尝试 timeout drain

处理方式：

- 两边必须都走同一个 stripe lock
- 进入锁后再次检查 slot/entry 状态
- 优先以“已成功配对/已移除”的最新状态为准

## 十九、故障与边界场景

### 19.1 消费端永久卡死

表现：

- soft timeout 数据不断延期
- 缓存最终写满
- 生产全面背压

这是预期的“保护性停顿”，但不能无限持续。需依赖 `hardTimeout` 兜底逐步释放。

### 19.2 某 Job 持续过载影响全局

如果全局存储上限启用，一个 Job 的延期数据会长期占用全局 storage permit。

这是期望行为的一部分，因为它真实反映系统整体承压。但可选增加：

- per-job 最大延期 entry 比例
- 单 Job 强制清退阈值

本设计第一阶段不引入。

### 19.3 source 已结束但缓存仍有延期超时数据

处理建议：

- 一旦 `sourceFinished == true` 且引擎进入完成排空阶段，可绕过 soft timeout 的延期逻辑，直接将剩余数据送入 finalizer 或 passive egress。
- 若仍希望严格服从下游背压，则至少不应无限延期，应切换为完成态 drain 专用策略。

推荐：

- `drainRemainingToFinalizer()` 在 source finished 场景优先级高于普通 soft timeout 延期。

### 19.4 shutdown

shutdown 必须优先于延期策略：

- 一旦系统关闭，全部 entry 立即转为 `SHUTDOWN_EGRESSING`
- 不再等待下游恢复

### 19.5 replace/overflow

replace/overflow 不是时间驱动，而是写入路径上的显式行为，不受延期策略控制。

处理原则：

- replace/overflow 导致的被淘汰数据直接离库
- 新写入数据继续正常注册 soft/hard timeout

## 二十、实现阶段规划

### Phase 1：设计落地与接口铺垫

目标：

- 不改变默认行为，先引入接口和指标骨架

工作：

- 新增 `DownstreamPressureEvaluator`
- 为 `BackpressureController` 增加 snapshot 能力
- 在 `FlowFinalizer` 中补指标
- 在配置类中增加新字段但暂不启用

### Phase 2：受控超时替换自动过期

目标：

- 去掉 `expireAfterWrite`
- 引入 `EvictionCoordinator`
- soft timeout 改为协调器驱逐

工作：

- 存储中增加 expiry task 注册
- timeout 由协调器统一裁决
- 保持容量仍按现有近似语义，先不解决 `maximumSize`

说明：

- 这一阶段只能部分实现目标，因为 `maximumSize` 仍会在容量层面自动淘汰。

### Phase 3：显式容量替换自动淘汰

目标：

- 去掉 `maximumSize`
- 容量完全由 Flow 控制

工作：

- 引入显式 `entryCount`
- `BackpressureController` 以显式计数判断缓存满
- 只有真实离库才释放容量

说明：

- 到这一阶段，分层背压链条才完整成立。

### Phase 4：严格 pending consumer 模式

目标：

- 让 finalizer/pending consumer 成为硬边界

工作：

- `strict-pending-consumer-slot=true` 默认开启
- acquire timeout 不再 submit anyway
- 调整相关测试和文档

### Phase 5：文档、指标、兼容项收口

目标：

- 清理旧配置
- 统一 metrics 和 guide
- 确认发布策略

## 二十一、测试设计

### 21.1 单元测试

必须覆盖：

- soft timeout 到期后在下游过载时延期
- 延期后 entry 仍占用 storage permit
- 延期期间生产者因 storage full 被阻塞
- hard timeout 到达后强制离库
- 同一 entry 不会重复驱逐
- 配对成功后旧 expiry task 出队时被正确忽略

### 21.2 集成测试

必须覆盖：

- 下游消费慢，缓存堆满，生产阻塞
- 下游恢复，延期项逐步离库，生产恢复
- 多 Job 并发时，per-job 与 global pending/storage 一起生效
- source finished 后剩余数据按完成态策略排空
- shutdown 时所有延期项都能安全释放资源

### 21.3 压测与 soak test

必须验证：

- 长时间高压下 permit 不泄漏
- `entries.used` 与实际存储内容一致
- `expiry.defer.total` 和 `delay.duration` 分布合理
- 不出现协调线程 CPU 空转

## 二十二、兼容性与发布策略

### 22.1 兼容性影响

本设计可能带来以下行为变化：

1. timeout 后数据不再立即离库，可能在缓存中停留更久。
2. 存储容量的语义可能从“key 数”变为“entry 数”。
3. finalizer 在高压时可能更早拒绝或延期，不再“超限继续提交”。
4. 某些依赖“超时自动释放缓存空间”的业务会观察到更早、更持续的背压。

### 22.2 发布建议

建议分两版发布：

- V1：新增配置，默认兼容旧行为
- V2：默认开启受控超时与严格容量语义

若团队接受破坏性升级，也可以直接按新语义切换，但必须在 release note 中明确说明。

## 二十三、推荐默认参数

对大多数场景，建议默认值：

- `soft-timeout-mill = 10000`
- `hard-timeout-mill = 60000`
- `expiry-defer-initial-mill = 100`
- `expiry-defer-max-mill = 1000`
- `expiry-defer-backoff-multiplier = 2.0`
- `strict-pending-consumer-slot = true`
- `eviction-batch-size = 128`
- `storage-count-by-entry = true`

推荐关系：

- `hard-timeout-mill >= soft-timeout-mill * 3`
- `pending-consumer >= consumer-concurrency`
- `storage >= pending-consumer`

## 二十四、性能影响与低对象数实现建议

### 24.1 性能影响判断

这套方案会引入额外性能成本，但要区分“对象包裹本身的成本”和“受控超时机制的成本”。

对象层层包裹本身的代价主要是：

- 多一层对象分配
- 多一次指针跳转
- 增加 GC 扫描对象数量

这部分成本通常是线性的、可预期的，不太会成为第一瓶颈。

真正更容易成为瓶颈的是：

- 过期索引的入队、出队与延期重排
- stripe lock 或 CAS 状态切换带来的竞争
- 多值模式下 slot 内候选扫描
- 旧过期任务失效判断
- 过多高频指标上报

因此，性能优化的重点不应只盯着“是否多包一层对象”，而应优先控制：

- 对象总数
- 延期任务总数
- 锁竞争次数
- 每次扫描处理的候选数量

### 24.2 性能风险排序

按影响从高到低，通常更值得优先优化的点是：

1. 过期任务数量膨胀
2. 延期导致的重复调度
3. 高频锁竞争
4. 多值 slot 扫描复杂度
5. 对象包裹额外层级

换句话说：

- “每个 entry 多一个 envelope”有成本
- “每个 entry 多一个 envelope + 多一个 expiry task + 多次延期重入队”才是真正危险的组合

### 24.3 推荐的低对象数方案

如果实现时优先考虑性能，建议不要把 `FlowEntry`、`StoredEntryEnvelope`、`ExpiryTask` 都做成重量级对象链，而采用“主对象轻扩展 + 轻量 token 索引”的方式。

推荐结构：

```text
FlowEntry<T>
  + entryMeta(内嵌轻量字段或 sidecar)

FlowSlot<T>
  -> 直接持有 FlowEntry<T>

ExpiryToken
  -> 只持有 entryId / key / version / nextCheckAt
```

也就是：

- 业务数据仍放在 `FlowEntry`
- slot 里仍直接存 `FlowEntry`
- 过期队列里不再复制整条 entry，只放轻量 token

这样可以减少一层长期存活对象。

### 24.4 方案 A：在 `FlowEntry` 上直接扩展元数据

这是最省对象数的方案。

做法：

- 直接在 `FlowEntry` 上增加少量字段：
  - `entryId`
  - `joinKey`
  - `storedAt`
  - `softExpireAt`
  - `hardExpireAt`
  - `nextCheckAt`
  - `deferCount`
  - `state`
  - `version`

优点：

- 不需要新增 `StoredEntryEnvelope`
- slot 继续直接存 `FlowEntry`
- 少一次对象分配和一次间接访问

缺点：

- `FlowEntry` 从通用上下文对象变成带较强存储语义的对象
- 会侵入现有模型
- 未来如果 Queue/Caffeine 想有不同元数据结构，扩展性稍弱

适用场景：

- 你确认 FlowEntry 主要就是给 Flow 存储用
- 你优先要性能和对象数控制

### 24.5 方案 B：使用 sidecar 元数据表

如果不想污染 `FlowEntry`，但又不想每条 entry 多包一层对象，可以使用 sidecar 元数据表。

结构如下：

```text
FlowSlot -> FlowEntry
EntryMetaTable(entryId -> EntryMeta)
ExpiryQueue -> ExpiryToken(entryId, version, nextCheckAt)
```

其中 `EntryMeta` 只保存元数据：

- `joinKey`
- `softExpireAt`
- `hardExpireAt`
- `nextCheckAt`
- `deferCount`
- `state`
- `version`

优点：

- `FlowEntry` 保持干净
- `ExpiryToken` 很轻
- 元数据结构可按存储实现独立演进

缺点：

- 访问 entry 状态时需要多查一张表
- entry 与 meta 的生命周期必须严格同步

适用场景：

- 你要兼顾模型边界和对象数
- 可以接受一次 map 查询开销

### 24.6 不推荐方案：重量级 envelope 长期挂在 slot 中

以下结构不推荐作为默认实现：

```text
FlowSlot -> StoredEntryEnvelope -> FlowEntry -> data
```

如果再叠加：

- `ExpiryTask -> StoredEntryEnvelope`

那么每条 entry 至少会关联：

- 1 个 FlowEntry
- 1 个 envelope
- 1 个 expiry task

在高并发高积压场景下，对象数量和 GC 压力会明显放大。

这种方案不是不能用，但更适合：

- 第一版快速实现
- 先验证正确性
- 后续再做对象收缩优化

不适合作为长期高吞吐版本的最终结构。

### 24.7 `ExpiryToken` 的轻量化建议

过期队列里的对象必须尽量轻。

推荐字段：

- `long nextCheckAt`
- `long entryId`
- `int version`
- `int shardId` 或 `int keyHash`

尽量不要在 token 里放：

- `FlowEntry` 引用
- 业务 data 引用
- 大字符串 key

原因：

- 队列可能非常大
- token 存活时间较长
- 带业务对象引用会延长对象存活链，放大 GC root 可达范围

### 24.8 `joinKey` 的存储优化

如果 `joinKey` 很长，且在多个结构中重复保存，内存放大会很明显。

建议：

- slot 主表保存完整 `joinKey`
- `ExpiryToken` 不保存完整 key，只保存 `keyHash` 或 `slotId`
- 真正处理时再通过 `slotId -> slot` 或 `entryId -> meta -> joinKey` 获取

### 24.9 状态字段压缩建议

为降低每条 entry 元数据体积，状态和计数应尽量压缩：

- `state` 用 `byte` 或 `int` 枚举值，不用重量级对象
- `deferCount` 用 `short` 或 `int`
- `version` 用 `int`
- 时间字段统一用 `long epochMillis`

不要为状态机引入额外状态对象。

### 24.10 减少延期任务数量的建议

对象数暴涨经常不是因为 entry 多，而是因为“每次延期都产生一个新任务”。

优化建议：

1. 使用 version 失效旧任务，而不是尝试持有并删除旧任务对象
2. 每个 entry 在任一时刻只允许存在一个逻辑有效的过期 token
3. 延期退避不要太细，避免高频重排
4. 达到 `hardTimeout` 前，延期步长应逐渐变大

推荐默认退避：

- 100ms
- 200ms
- 400ms
- 800ms
- 1000ms 之后固定

这样能明显减少重调度次数。

### 24.11 slot 级 token 与 entry 级 token 的取舍

如果一个 key 下可能有很多 entry，可以考虑只给 slot 注册 token，而不是每个 entry 一个 token。

两种方案：

- entry 级 token
  - 更精确
  - 对象更多
  - 适合单值模式或低多值场景

- slot 级 token
  - 对象更少
  - 扫描时要遍历 slot 内元素找出过期项
  - 适合多值模式或高 key 聚合场景

推荐：

- 单值模式优先 entry 级 token
- 多值模式优先 slot 级 token

如果想统一实现，可默认采用 slot 级 token，这样更稳妥地控制对象总数。

### 24.12 锁竞争优化建议

对象包裹不是最大风险，锁竞争通常更贵。

建议：

- 继续复用现有 stripe lock，不引入全局大锁
- 协调器扫描时按 key/slot 分片处理
- 单次批量处理的 entry 数受 `eviction-batch-size` 限制
- 能在锁外做的判断放到锁外，锁内只做最终状态确认和移除

### 24.13 GC 视角下的建议

从 GC 角度看，更重要的是：

- 长寿命对象尽量少
- 队列中的对象尽量轻
- 不要让 token 直接引用大对象图

因此推荐优先级是：

1. token 轻量化
2. 减少 envelope 长期存在
3. 避免重复保存 `joinKey` 和 data 引用

### 24.14 推荐最终落地方案

如果要在“正确性、可维护性、性能”之间取一个比较稳的平衡，推荐这样落地：

- `FlowSlot` 继续直接持有 `FlowEntry`
- 在 `FlowEntry` 上增加少量受控元数据字段，或使用 `EntryMetaTable`
- `ExpiryQueue` 只存轻量 `ExpiryToken`
- 多值模式下优先使用 slot 级 token
- 旧任务通过 `version` 逻辑失效，不做昂贵删除

不推荐一上来就采用“entry + envelope + task”三层长期对象链。

### 24.15 实现优先级建议

如果你后续要做实现，建议按这个顺序优化：

1. 先实现正确性，允许临时 envelope 存在
2. 压测确认瓶颈位置
3. 若对象数和 GC 成本明显，再收敛为：
   - `FlowEntry + EntryMeta`
   - `ExpiryToken`
   - slot 级索引

也就是说，性能优化应该建立在真实测量之上，而不是先验地把复杂度都堆进第一版实现。

## 二十五、最终推荐实现方案

本节不是过渡方案，而是本文推荐直接落地的最终形态。目标优先级如下：

1. 正确实现分层背压
2. 将对象数量控制在可长期高压运行的水平
3. 将并发控制收敛到最少的同步点
4. 让多值模式和单值模式共用同一套主干实现

### 25.1 最终方案总览

最终推荐采用以下结构：

```text
FlowEntry<T>
  + EntryRuntimeMeta(直接挂载在 FlowEntry 内)

SlotTable
  key -> FlowSlot<T>

FlowSlot<T>
  -> Deque<FlowEntry<T>>
  -> SlotRuntimeMeta

ExpiryQueue
  -> SlotExpiryToken
```

也就是：

- 不引入长期存在的 `StoredEntryEnvelope`
- 不为每条 entry 单独维护长期 `ExpiryTask`
- 以 `FlowEntry` 作为唯一长期 entry 对象
- 以 `slot` 作为过期调度与驱逐裁决的基本单位

这是在正确性、对象数、锁竞争和实现复杂度之间最优的平衡点。

### 25.2 为什么选 slot 级方案而不是 entry 级方案

最终推荐 `slot` 级，而不是 `entry` 级，原因如下：

1. 现有 Flow 的核心操作本来就是围绕 `joinKey -> FlowSlot` 展开。
2. 配对、重试、overflow、replace 都天然发生在 slot 上。
3. 多值模式下，一个 key 对应多个 entry，若做 entry 级 token，会导致 token 数量膨胀。
4. timeout 驱逐在业务上也更适合按 slot 视角处理，因为同 key 下通常需要整体查看候选关系。

因此最终设计中：

- 容量按 entry 计数
- 过期调度按 slot 计时
- 真正离库时按 entry 释放 permit

### 25.3 `FlowEntry` 的最终字段设计

推荐直接扩展 [FlowEntry.java](D:/github.com/app.template/template-flow/src/main/java/com/lrenyi/template/flow/context/FlowEntry.java)，加入运行时元数据字段。

建议新增字段：

```java
private long entryId;
private long storedAtEpochMs;
private long softExpireAtEpochMs;
private long hardExpireAtEpochMs;
private volatile int runtimeState;
private volatile int slotVersion;
```

字段语义：

- `entryId`：全局唯一或单 JVM 唯一递增 ID
- `storedAtEpochMs`：入库时间
- `softExpireAtEpochMs`：软超时时间
- `hardExpireAtEpochMs`：硬超时时间
- `runtimeState`：entry 当前状态，使用 `int` 常量表示
- `slotVersion`：entry 最近一次被 slot 重排或重新登记时的逻辑版本

不建议增加的字段：

- 不要在 `FlowEntry` 上挂额外对象引用作为 runtime meta
- 不要挂 token 引用
- 不要挂复杂状态对象

### 25.4 `runtimeState` 的最终取值

使用 `int` 常量，不使用 Java enum 实例参与高频路径。

建议取值：

```text
0 ACTIVE
1 SOFT_EXPIRED
2 EGRESSING
3 REMOVED
```

说明：

- `DEFERRED` 不单独作为 entry 状态保存，延期是 slot 调度层的行为，不是 entry 的长期业务态
- `REMOVED` 表示已从 slot 中移除并进入终结路径

### 25.5 `FlowSlot` 的最终结构

推荐保留 [FlowSlot.java](D:/github.com/app.template/template-flow/src/main/java/com/lrenyi/template/flow/storage/FlowSlot.java) 作为核心容器，但将其强化为“数据 + 调度元信息”的最小聚合单元。

建议新增 slot 元字段：

```java
private long slotId;
private long earliestSoftExpireAt;
private long earliestHardExpireAt;
private long nextCheckAt;
private int version;
private boolean queuedForExpiry;
private boolean draining;
```

字段语义：

- `slotId`：slot 唯一 ID
- `earliestSoftExpireAt`：slot 内最早软超时 entry 的时间
- `earliestHardExpireAt`：slot 内最早硬超时 entry 的时间
- `nextCheckAt`：当前 slot 下一次应被协调器检查的时间
- `version`：slot 调度版本号
- `queuedForExpiry`：当前是否已有逻辑有效 token 在队列中
- `draining`：是否正在被 timeout/shutdown drain

容器建议：

- 单值模式：内部可退化为单元素结构
- 多值模式：使用 `ArrayDeque<FlowEntry<T>>`

不推荐：

- `LinkedList`
- 为每个元素再套一层节点对象

### 25.6 `SlotExpiryToken` 的最终结构

最终过期队列只存 slot 级 token。

```java
final class SlotExpiryToken implements Delayed {
    long slotId;
    long nextCheckAt;
    int version;
}
```

说明：

- token 中不保存 `FlowEntry`
- token 中不保存业务 data
- token 中不保存完整 `joinKey`

需要通过以下映射找回 slot：

- `slotId -> slot`

推荐在存储中增加：

```java
Long2ObjectMap<FlowSlot<T>> slotById
```

如果不想引入第三方 primitive map，也至少保持：

- `Map<Long, FlowSlot<T>>`

### 25.7 主索引表的最终形态

最终推荐双索引：

1. `Map<String, FlowSlot<T>> slotByKey`
2. `Map<Long, FlowSlot<T>> slotById`

原因：

- 正常配对、写入、删除按 key 查找
- 过期协调器按 token 中的 slotId 查找

这样可以避免在 token 中重复保存大字符串 key。

### 25.8 计数模型的最终形态

最终统一按 entry 计数。

显式计数器：

- `usedEntryCount`
- `expiryReadyEntryCount`
- `egressingEntryCount`

其中：

- `usedEntryCount`：当前仍在存储中的 entry 数
- `expiryReadyEntryCount`：已达到 soft timeout 且仍滞留在 slot 中的 entry 数
- `egressingEntryCount`：已从 slot 逻辑摘除、正在走离库路径的 entry 数

背压判断只看：

- `usedEntryCount >= storageLimit`

不再依赖 key 数量。

### 25.9 容量与过期的最终职责边界

最终职责边界如下：

- `BackpressureController`
  - 负责生产侧阻塞判断
  - 负责等待与唤醒

- `BoundedTimedFlowStorage`
  - 负责 slot 存储、entry 计数、写入、配对、离库
  - 负责维护 slot 的最早超时时间

- `EvictionCoordinator`
  - 只负责扫描 slot token
  - 只负责“是否发起 drain 检查”
  - 不直接执行业务消费逻辑

- `DownstreamPressureEvaluator`
  - 只负责返回是否允许 soft timeout drain

### 25.10 最终超时处理模型

推荐按 slot 处理：

1. token 到期
2. 协调器拿到 slot
3. 加 stripe lock
4. 检查 slot 是否仍有效、version 是否匹配
5. 扫描 slot 内所有 entry
6. 分成三组：
   - 已到 hard timeout
   - 已到 soft timeout
   - 尚未到 soft timeout
7. 若存在 hard timeout：
   - 优先强制 drain 这些 entry
8. 否则若 soft timeout entry 存在：
   - 调用 `DownstreamPressureEvaluator`
   - 若允许，则 drain soft timeout entry
   - 若不允许，则更新 `nextCheckAt` 后重新入队
9. 尚未超时的 entry 留在 slot 中

### 25.11 为什么不推荐“每条 entry 一个 token”

最终不选 entry 级 token 的主要原因是：

- token 数量与 entry 数线性一致
- 多值模式下对象量膨胀明显
- 同一个 slot 多条 entry 过期时会重复命中同一把 stripe lock
- 同一 key 的超时裁决逻辑会被拆碎

slot 级 token 的缺点是需要扫描 slot 内元素，但这通常比维护大量 entry 级 token 更划算。

### 25.12 最终延期策略

最终推荐：

- slot 级延期
- 指数退避
- 基于 slot 中最早 soft timeout entry 的下一次检查时间重排

建议公式：

```text
deferMillis = min(initial * 2^deferCount, deferMax)
nextCheckAt = now + deferMillis
```

slot 内存在 `hardTimeout` 更早到来的 entry 时：

```text
nextCheckAt = min(now + deferMillis, earliestHardExpireAt)
```

这样既不会错过硬超时，也避免了高频重排。

### 25.13 最终并发模型

最终只保留三类核心同步机制：

1. `slotByKey` / `slotById` 的并发 map
2. key stripe lock
3. DelayQueue 的线程安全出入队

原则：

- 所有 slot 内容变更必须在对应 stripe lock 下完成
- 过期协调器和生产/配对路径共享同一把 stripe lock
- 不引入额外全局大锁

### 25.14 最终内存模型

推荐对象构成：

- 每条业务数据：1 个 `FlowEntry`
- 每个活跃 key：1 个 `FlowSlot`
- 每个活跃且已登记过期检查的 slot：最多 1 个逻辑有效 `SlotExpiryToken`

这意味着对象数量上界近似为：

```text
O(entryCount + activeKeyCount)
```

而不是：

```text
O(entryCount + activeKeyCount + entryCount token + entryCount envelope)
```

这就是最终方案选择 slot 级 token 的根本原因。

### 25.15 最终实现下的多值模式行为

多值模式下：

- slot 内维护 `ArrayDeque<FlowEntry<T>>`
- 配对时按现有策略从 deque 中取候选
- soft timeout 检查时遍历 deque，找出已到 soft/hard timeout 的 entry
- drain 只移除已到期那部分 entry，不必整 slot 清空

也就是说：

- 调度粒度是 slot
- 实际离库粒度仍是 entry

这样兼顾了对象数和超时精度。

### 25.16 最终实现下的单值模式行为

单值模式下：

- slot 内最多 1 个 entry
- slot 级 token 几乎等价于 entry 级 token
- 不需要为单值模式做额外分支架构

因此 slot 级方案能天然统一单值/多值两种模式。

### 25.17 最终类职责草案

建议最终新增/改造以下类：

- `BoundedTimedFlowStorage<T>`
  - 取代旧版基于 TTL/size 自动驱逐的存储主逻辑

- `EvictionCoordinator`
  - 消费 `DelayQueue<SlotExpiryToken>`

- `DownstreamPressureEvaluator`
  - 基于 `BackpressureController` 和 `FlowResourceContext` 生成裁决

- `ExpiryDecisionType` / `ExpiryDecision`
  - 复用 5.3 中定义的 `ALLOW_SOFT_DRAIN` / `DEFER` / `FORCE_HARD_DRAIN`

### 25.18 最终实现下的关键算法

#### 写入

1. `awaitBackpressure`
2. 获取 storage permit
3. 根据 key 找 slot
4. 在 stripe lock 下写 entry
5. 更新 slot 最早超时字段
6. 若 slot 尚未排入 expiry queue，入队一个 token

#### 配对成功

1. 在 stripe lock 下找到 partner
2. 将两条 entry 从 slot 中移除
3. 更新 `usedEntryCount`
4. 若 slot 为空，从 `slotByKey/slotById` 中删除
5. 释放 permit
6. 提交 matched egress

#### 软超时

1. token 出队
2. 找到 slot
3. stripe lock
4. 检查 version
5. 扫描 slot 中到 soft timeout 的 entry
6. 若允许，摘除这些 entry 并提交 timeout egress
7. 若不允许，更新 slot 的 `nextCheckAt/version`，重新入队

#### 硬超时

1. token 出队
2. 找到 slot
3. stripe lock
4. 扫描 slot 中到 hard timeout 的 entry
5. 直接摘除
6. 提交 forced timeout egress

### 25.19 最终推荐实现的理由总结

最终推荐方案之所以是最优，不是因为它最简单，而是因为它同时满足：

- 正确：缓存真正参与背压
- 稳定：不会被底层缓存实现的自动驱逐绕开
- 省对象：没有 envelope 链和 entry 级 token 海量对象
- 统一：单值与多值共用一套架构
- 易控：核心并发点只有 stripe lock 和 DelayQueue

这套方案应作为后续实现的唯一目标方案，不建议再引入“长期 envelope + entry 级 token”的并行路线。

## 二十六、实施建议总结

本设计的关键不是“换一个缓存库”，而是“收回驱逐权”。

必须落实以下四点，分层背压才成立：

1. soft timeout 只标记可驱逐，不自动离库。
2. 是否离库必须经过下游压力判断。
3. 延期时数据必须继续占着缓存容量。
4. 只有真实离库后才释放 permit 并唤醒生产者。

如果只做其中一部分，例如：

- 只加 `EvictionCoordinator` 但仍保留 `maximumSize`
- 或只去掉 `expireAfterWrite` 但 finalizer 仍然超限继续提交

那都只能得到“部分改良”，得不到真正的层层背压。

## 二十七、涉及代码范围

本设计预计影响以下核心文件：

- [FlowLauncher.java](D:/github.com/app.template/template-flow/src/main/java/com/lrenyi/template/flow/internal/FlowLauncher.java)
- [BackpressureController.java](D:/github.com/app.template/template-flow/src/main/java/com/lrenyi/template/flow/internal/BackpressureController.java)
- [FlowFinalizer.java](D:/github.com/app.template/template-flow/src/main/java/com/lrenyi/template/flow/internal/FlowFinalizer.java)
- [FlowStorage.java](D:/github.com/app.template/template-flow/src/main/java/com/lrenyi/template/flow/storage/FlowStorage.java)
- [BoundedTimedFlowStorage.java](D:/github.com/app.template/template-flow/src/main/java/com/lrenyi/template/flow/storage/BoundedTimedFlowStorage.java)
- [FlowResourceRegistry.java](D:/github.com/app.template/template-flow/src/main/java/com/lrenyi/template/flow/resource/FlowResourceRegistry.java)
- [TemplateConfigProperties.java](D:/github.com/app.template/template-core/src/main/java/com/lrenyi/template/core/TemplateConfigProperties.java)

## 二十八、相关文档

- [Flow 使用指南](../guides/flow-usage-guide.md)
- [Flow 配置优化与全局化设计](flow-limits-globalization.md)
- [Flow 消费出口统一设计](flow-egress-unification.md)
- [Flow 完成态收敛优化](flow-completion-isCompleted.md)
