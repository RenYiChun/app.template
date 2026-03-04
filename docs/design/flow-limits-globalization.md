# Flow 配置优化与全局化设计（最终版）

## 一、设计目标

- 采用方案 C：全局上限与按 Job 限制并存，行为等价于 `effectiveLimit = min(perJob, globalRemaining)`。
- 配置清晰：五个 OOM 控制维度命名直观、含义明确。
- 统一限流语义、获取顺序、回滚策略，避免并发场景下死锁与许可泄漏。
- 不做向后兼容：直接替换旧字段，不保留旧配置与旧代码路径。

---

## 二、约束语义契约与实现准则

### 2.1 约束语义契约

- `limits.global.*`：`<=0` 表示禁用该维度全局限制，仅使用 per-job 限制。
- `limits.per-job.*`：不允许 `<=0`，启动时 `validateConfig()` 校验失败并抛出异常。
- `limits.global.consumer-concurrency`：`>0` 启用全局限制，`<=0` 仅使用 per-job 限制。
- `limits.per-job.cache-ttl-mill`、`limits.per-job.queue-poll-interval-mill`：时间配置，必须 `>0`。
- `limits.per-job.pending-consumer`：`>0` 显式值，`0` 使用 global.consumer-concurrency。若采用“默认同 consumer-concurrency”，定义为启动时一次性取值，不做运行期动态联动。

### 2.2 双层限流获取与释放顺序

- 获取顺序：先 global，再 per-job。
- 释放顺序：先 per-job，再 global（反向释放）。
- 适用维度：`producer-threads`、`in-flight-production`、`storage`、`pending-consumer`。

### 2.3 失败回滚原子化

- 先拿到 global，再拿 per-job 失败时，必须立即归还 global。
- 抽象 `PermitPair`（`com.lrenyi.template.flow.resource` 包下）：
  - `tryAcquireBoth(globalSemaphore, perJobSemaphore)`：先 acquire global，再 acquire per-job；任一步失败则立即 release 已占用的，返回 false。
  - `release()`：按先 per-job 再 global 顺序释放。
  - 支持 `tryAcquireBoth(timeout)` 用于可中断场景。
- 避免 `global 已占用 + per-job 失败` 导致许可泄漏。

### 2.4 effectiveLimit 实现准则

- `effectiveLimit = min(...)` 是行为等价描述，不要求显式计算 `min`。
- 实现采用统一串行 acquire（global -> per-job）即可达成等价效果。
- 各模块统一语义，避免实现分叉。

### 2.5 防止 Job 饥饿（已实现）

- 全局信号量下，大 Job 可能长期占用资源。
- **`limits.global.fair-scheduling`**：`true` 时信号量 FIFO 公平调度（默认），`false` 时更高吞吐。
- **`limits.{dimension}.acquire-wait-duration`**：各维度许可获取等待耗时指标（按 `jobId`、`dimension` 标签）。

### 2.6 pending-consumer.global 统计

- 不采用“每次遍历所有 Job 求和”。
- 事件驱动累计：`FlowResourceRegistry.globalPendingConsumerAdder`（`LongAdder`）。
- **+N**：`submitConsumerToGlobal(orchestrator, permits, task)` 调用时，数据已离库、即将进入消费管道，`adder.add(permits)`。
- **-N**：`strategy.release()` 执行时（consumer 任务完成），`adder.add(-permits)`。
- 全局 pending 以累计值为准，减少遍历开销与竞态误差。

### 2.7 storage-global 预占与回滚

- **预占时机**：`BackpressureController.awaitSpace()` 通过后、调用 `storage.deposit()` 之前。
- **流程**：`globalStorageSemaphore.acquire(1)` -> `storage.deposit(ctx)` -> 若 `deposit` 返回 `false` 或抛异常，则 `globalStorageSemaphore.release(1)`。
- **接口变更**：`FlowStorage.deposit(FlowEntry)` 改为返回 `boolean`，`true` 表示入库成功，`false` 表示拒绝（如 Queue 满）。默认实现 `return doDeposit(entry)`，失败时仍执行 `entry.release()`。
- **释放时机**：数据离开存储时释放全局额度。释放点：
  - Caffeine `onEntryRemoved`：驱逐/过期，`release(1)`；
  - Caffeine `processMatchedPair`：配对成功两条离库，`release(2)`；
  - Queue `drainLoop`：`poll` 成功，`release(1)`。
- Storage 实现类需持有 `FlowResourceRegistry` 引用，在离库时调用 `releaseGlobalStorage(n)`。`FlowStorageFactory.createStorage()` 需增加 `FlowResourceRegistry` 参数，由 `FlowCacheManager` 传入。

### 2.8 TTL 语义拆分（已实现）

- 已拆分为 `cache-ttl-mill`（Caffeine 过期）与 `queue-poll-interval-mill`（Queue 轮询间隔）。

---

## 三、五个 OOM 控制维度与命名

| 维度 | 当前字段 | 新配置路径 | 含义 |
|------|----------|------------|------|
| 1. 生产并发线程数（per-job） | `producer.parallelism` | `limits.per-job.producer-threads` | 每 Job 同时执行的 deposit 任务数 |
| 1. 生产并发线程数（global） | 无 | `limits.global.producer-threads` | 全主机生产线程上限（`<=0` 不启用） |
| 2. 生产在途数据量（per-job） | `producer.maxInFlightThreshold` | `limits.per-job.in-flight-production` | 每 Job 已 launch 未 deposit 完成的数据条数 |
| 2. 生产在途数据量（global） | 无 | `limits.global.in-flight-production` | 全主机生产在途数据量上限（`<=0` 不启用） |
| 3. 消费并发数（per-job） | 无 | `limits.per-job.consumer-concurrency` | 每 Job 同时消费的数据条数 |
| 3. 消费并发数（global） | `consumer.concurrencyLimit` | `limits.global.consumer-concurrency` | 全主机同时消费的数据条数（`<=0` 不启用） |
| 4. 等待消费许可数据量（per-job） | 背压复用 `concurrencyLimit` | `limits.per-job.pending-consumer` | 每 Job 已离库未终结条数上限，触发背压 |
| 4. 等待消费许可数据量（global） | 无 | `limits.global.pending-consumer` | 全主机已离库未终结条数上限（`<=0` 不启用） |
| 5. 存储容量（per-job） | `producer.maxCacheSize` | `limits.per-job.storage` | 每 Job 缓存/队列容量 |
| 5. 存储容量（global） | 无 | `limits.global.storage` | 全主机存储容量上限（`<=0` 不启用） |
| 缓存 TTL | `consumer.ttlMill` | `limits.per-job.cache-ttl-mill` | 每 Job Caffeine 缓存过期时间（毫秒） |
| Queue 轮询间隔 | 无 | `limits.per-job.queue-poll-interval-mill` | 每 Job Queue  drain 轮询间隔（毫秒） |

---

## 四、配置结构（全局与按 Job 分离）

```yaml
app:
  template:
    flow:
      limits:
        global:
          fair-scheduling: true
          producer-threads: 0
          in-flight-production: 0
          storage: 0
          consumer-concurrency: 1000
          pending-consumer: 0
        per-job:
          producer-threads: 40
          in-flight-production: 4000
          storage: 40000
          consumer-concurrency: 1000
          pending-consumer: 1000
          cache-ttl-mill: 10000
          queue-poll-interval-mill: 10000
```

### 4.1 结构说明

- `limits.global`：全主机级限制，**始终来自 `FlowManager.getGlobalConfig()`**（即 `FlowManager.getInstance(flowConfig)` 传入的 config）。所有 Job 共享；`<=0` 表示禁用该维度。
- `limits.per-job`：每 Job 独立限制，**来自各 Job 的 `registration.getFlow()`**（即 `createLauncher(jobId, joiner, tracker, flowConfig)` 传入的 flowConfig）。未显式传入时，使用与 global 相同的 config。
- 本方案不保留旧字段，不提供兼容映射。

### 4.2 启动期配置校验

- `global.consumer-concurrency > 0` 或 `per-job.consumer-concurrency > 0`（至少一个启用消费并发限制）
- `per-job.producer-threads > 0`
- `per-job.in-flight-production > 0`
- `per-job.storage > 0`
- `per-job.consumer-concurrency > 0`
- `per-job.pending-consumer > 0` 或 `per-job.consumer-concurrency > 0`（当 pending-consumer 为 0 时）
- `per-job.cache-ttl-mill > 0`
- `per-job.queue-poll-interval-mill > 0`

**校验失败行为**：任一条件不满足时，`TemplateConfigProperties.validateConfig()` 抛出 `IllegalArgumentException`，携带明确错误信息（如 `flow.limits.per-job.producer-threads 必须 > 0，当前值: 0`），应用启动中止。

---

## 五、实现要点

### 5.1 配置类变更（TemplateConfigProperties.java）

- 新增 `Flow.Limits`，包含 `Flow.Limits.Global` 与 `Flow.Limits.PerJob`。
- `Flow` 仅持有 `limits`。
- 删除旧字段：
  - `Producer.parallelism / maxInFlightThreshold / maxCacheSize`
  - `Consumer.concurrencyLimit`

### 5.2 全局信号量与计数器（FlowResourceRegistry.java）

- 新增 `globalInFlightSemaphore`（`global.inFlightProduction > 0` 时创建）。
- 新增 `globalStorageSemaphore`（`global.storage > 0` 时创建）；提供 `releaseGlobalStorage(int n)` 供 Storage 离库时调用。`FlowStorageFactory.createStorage()` 需接收 `FlowResourceRegistry`，以便 Storage 在离库时调用。
- 新增 `globalProducerThreadsSemaphore`（`global.producerThreads > 0` 时创建，Phase 4）。
- 新增 `globalPendingConsumerAdder`（`LongAdder`），在 `submitConsumerToGlobal` 入口 `add(permits)`，在 `strategy.release()` 中 `add(-permits)`。

### 5.3 许可获取逻辑

- **FlowLauncher.launch()**：in-flight 使用 `PermitPair`，先 global 再 per-job；失败立即回滚。
- **FlowLauncherFactory**：`createProducerExecutor` 在启用 `threads-global` 时，传入 `PermitPair` 或自定义 `PermitStrategy`，包装“先 global 再 per-job”；`DefaultFlowExecutorProvider` 需支持 `createProducerExecutor(Semaphore global, Semaphore perJob)` 重载。
- **FlowStorage.deposit()**：接口改为 `boolean deposit(FlowEntry)`；容量读取 `limits.per-job.storage`；启用 `global.storage` 时，调用方在 `deposit` 前预占、`deposit` 返回 `false` 或抛异常时回滚。
- **BackpressureController.awaitSpace()**：
  - per-job：`jobPending >= limits.per-job.pending-consumer`
  - global：`globalPendingConsumerAdder.sum() >= limits.global.pending-consumer`（启用时）
  - 任一满足即触发背压。

### 5.4 文档更新范围

- `docs/guides/flow-usage-guide.md`：配置章节与代码示例统一到 `TemplateConfigProperties.Flow`。
- `docs/getting-started/config-reference.md`：新增 Flow 配置参考章节。
- `docs/guides/metrics-guide.md`：补充 limits 维度指标与 OOM 对应关系。

### 5.5 语义关系：pending-consumer 与 consumer-concurrency

- **consumer-concurrency**：限制同时持有消费许可的数量（正在执行 `onSuccess`/`onConsume` 回调）。
- **pending-consumer**：限制“已离库未终结”的数量，即已提交 `submitConsumerToGlobal` 但尚未调用 `onGlobalTerminated` 的数据（含等待 acquire 的 + 正在消费的）。
- 两者独立：`pending` 不含 `activeConsumers`（`getPendingConsumerCount = productionReleased - inStorage - activeConsumers - terminated`）。
- 配置建议：`pending-consumer >= consumer-concurrency`，避免背压过早触发；典型取相同值或略大。

---

## 六、监控指标设计

命名规范：`app.template.flow.limits.{dimension}.{used|limit|count}`。

| 维度 | 指标名 | 标签 | 类型 | 含义 |
|------|--------|------|------|------|
| 生产并发线程数 | `limits.producer-threads.used` | `jobId` | Gauge | 每 Job 已占用生产线程数 |
|  | `limits.producer-threads.limit` | `jobId` | Gauge | 每 Job 生产线程上限 |
|  | `limits.producer-threads.global.used` | — | Gauge | 全主机已占用生产线程数 |
|  | `limits.producer-threads.global.limit` | — | Gauge | 全主机生产线程上限 |
| 在途数据量 | `limits.in-flight.used` | `jobId` | Gauge | 每 Job 在途数据条数 |
|  | `limits.in-flight.limit` | `jobId` | Gauge | 每 Job 在途数据上限 |
|  | `limits.in-flight.global.used` | — | Gauge | 全主机在途数据条数 |
|  | `limits.in-flight.global.limit` | — | Gauge | 全主机在途数据上限 |
| 消费并发数 | `limits.consumer-concurrency.used` | `jobId` | Gauge | 每 Job 已占用消费许可数 |
|  | `limits.consumer-concurrency.limit` | `jobId` | Gauge | 每 Job 消费许可上限 |
|  | `limits.consumer-concurrency.global.used` | — | Gauge | 全主机已占用消费许可数 |
|  | `limits.consumer-concurrency.global.limit` | — | Gauge | 全主机消费许可上限 |
| 等待消费许可 | `limits.pending-consumer.count` | `jobId` | Gauge | 每 Job 已离库未终结条数 |
|  | `limits.pending-consumer.limit` | `jobId` | Gauge | 每 Job 背压阈值 |
|  | `limits.pending-consumer.global.count` | — | Gauge | 全主机已离库未终结条数 |
|  | `limits.pending-consumer.global.limit` | — | Gauge | 全主机背压阈值 |
| 存储容量 | `limits.storage.used` | `jobId,storageType` | Gauge | 每 Job 缓存当前条数 |
|  | `limits.storage.limit` | `jobId,storageType` | Gauge | 每 Job 缓存容量上限 |
|  | `limits.storage.global.used` | — | Gauge | 全主机缓存总条数 |
|  | `limits.storage.global.limit` | — | Gauge | 全主机缓存容量上限 |

已实现 `limits.acquire-wait-duration`（按 `jobId`、`dimension` 标签：producer-threads / in-flight / storage / consumer-concurrency）。

---

## 七、实施顺序

1. Phase 1：配置重命名与配置类重构（直接替换旧字段）。
2. Phase 2：实现 `in-flight-global`（最高优先级）。
3. Phase 3：实现 `storage-global`。
4. Phase 4：实现 `threads-global`。
5. Phase 5：指标对齐与文档收口。

---

## 八、阶段验收门禁

每个 Phase 必须同时满足：

- 功能正确：达到阈值时阻塞/背压行为正确。
- 一致性稳定：无许可泄漏、无计数漂移、无死锁。
- 观测完整：`used/limit/count` 指标齐全，告警可覆盖饱和、积压、饥饿。

**验证方式**：

- 单元测试：`PermitPair` 的 acquire/release 对称性、失败回滚无泄漏。
- 集成测试：多 Job 并发下各维度限流生效、背压触发正确。
- 压力测试：长时间运行后 `used` 与 `limit` 关系合理，无持续增长（泄漏检测）。

---

## 九、配置对照表（最终）

| 配置路径 | 默认值 | 含义 |
|----------|--------|------|
| `flow.limits.global.fair-scheduling` | true | 全局信号量公平调度（FIFO），`false` 更高吞吐 |
| `flow.limits.global.producer-threads` | 0 | 全主机生产线程上限，`<=0` 不启用 |
| `flow.limits.global.in-flight-production` | 0 | 全主机生产在途数据量上限，`<=0` 不启用 |
| `flow.limits.global.storage` | 0 | 全主机存储容量上限，`<=0` 不启用 |
| `flow.limits.global.consumer-concurrency` | 1000 | 全主机消费并发数，`<=0` 不启用 |
| `flow.limits.global.pending-consumer` | 0 | 全主机已离库未终结条数上限，`<=0` 不启用 |
| `flow.limits.per-job.producer-threads` | 40 | 每 Job 生产线程数（必须 `>0`） |
| `flow.limits.per-job.in-flight-production` | 4000 | 每 Job 生产在途数据量（必须 `>0`） |
| `flow.limits.per-job.storage` | 40000 | 每 Job 缓存容量（必须 `>0`） |
| `flow.limits.per-job.consumer-concurrency` | 1000 | 每 Job 消费并发数（必须 `>0`） |
| `flow.limits.per-job.pending-consumer` | 0 | 每 Job 背压阈值，`>0` 显式值，`0` 使用 per-job.consumer-concurrency |
| `flow.limits.per-job.cache-ttl-mill` | 10000 | 每 Job Caffeine 缓存过期时间（毫秒，必须 `>0`） |
| `flow.limits.per-job.queue-poll-interval-mill` | 10000 | 每 Job Queue 轮询间隔（毫秒，必须 `>0`） |
