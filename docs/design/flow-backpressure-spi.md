# Flow 背压 SPI 化重构设计文档

## 一、背景与问题

### 1.1 旧设计的局限

重构前，Flow 框架的背压逻辑集中在 `BackpressureController` 中，存在以下问题：

- **扩展性差**：背压策略硬编码，业务无法替换或新增维度，只能修改框架源码。
- **职责混杂**：资源申请、阻塞决策、指标记录全部耦合在一个类，难以单独测试。
- **释放不可靠**：调用方手动配对 `acquire/release`，遗漏 `finally` 或异常路径容易导致资源泄露，信号量永久减少。
- **存储维度旁路**：全局存储信号量在 `FlowLauncher.submitDepositTask()` 中直接操作，完全绕过背压体系，指标断档。
- **命名混乱**：`pending-consumer`（等待消费）实为"在途消费"语义，名不副实。

### 1.2 设计目标

| 目标 | 说明 |
|------|------|
| SPI 化扩展 | 通过 Java SPI 注册维度实现，框架零侵入可插拔 |
| 职责内聚 | 每个维度独立封装申请、阻塞、释放与指标记录 |
| 配对保障 | `acquire()` 返回 `AutoCloseable` 租约，`close()` 幂等释放，天然支持 `try-with-resources` |
| 泄露检测 | 未关闭的租约通过 JVM `Cleaner` 兜底恢复资源并告警 |
| 存储维度统一 | 全局存储信号量纳入背压体系，存储槽位租约跟随条目生命周期 |
| 可观测性 | 两层指标（维度级 + 管理器级），覆盖申请、阻塞、超时、泄露、活跃租约 |

---

## 二、核心组件设计

### 2.1 组件总览

```
BackpressureManager（每 Job 一个实例）
  │
  ├── 加载 ResourceBackpressureDimension（Java SPI）
  │     同 ID 多实现 → 选 order 最小者
  │
  ├── acquire(dimensionId, stopCheck) → DimensionLease
  │     │ 内部调用 ResourceBackpressureDimension.acquire(ctx)
  │     │ 注册到 activeLeases 注册表
  │     └── DimensionLease.close()
  │           幂等释放 → onBusinessRelease(ctx)
  │           从 activeLeases 移除
  │
  └── 泄露检测
        DefaultDimensionLease 注册 JVM Cleaner
        GC 前未 close → 触发 onLeakDetected + 兜底释放
```

### 2.2 `ResourceBackpressureDimension`（SPI 接口）

```java
// META-INF/services/com.lrenyi.template.flow.backpressure.ResourceBackpressureDimension
public interface ResourceBackpressureDimension {
    String id();     // 维度唯一标识
    int order();     // 同 ID 多实现时，优先执行 order 最小者

    void acquire(DimensionContext ctx)
            throws InterruptedException, TimeoutException;

    void onBusinessRelease(DimensionContext ctx);
}
```

**注册规则**：在 `META-INF/services/com.lrenyi.template.flow.backpressure.ResourceBackpressureDimension` 文件中逐行写入实现类全限定名。`BackpressureManager` 启动时通过 `ServiceLoader` 加载，同维度 ID 存在多个实现时仅执行 `order` 最小的一个。

### 2.3 `DimensionContext`（调用上下文）

`BackpressureManager` 在每次 `acquire()` 时构建，传递给维度实现，维度按需取用，无需关心不相关字段。

| 字段 | 类型 | 用途 |
|------|------|------|
| `jobId` | `String` | Job 标识 |
| `dimensionId` | `String` | 当前路由的维度 ID |
| `stopCheck` | `BooleanSupplier` | 停止信号（返回 true 时维度应退出等待） |
| `meterRegistry` | `MeterRegistry` | 指标注册 |
| `flowConfig` | `Flow` | 全局 Flow 配置（含阻塞模式与超时） |
| `resourceRegistry` | `FlowResourceRegistry` | 全局资源注册表 |
| `inFlightPermitPair` | `PermitPair` | 在途生产许可对 |
| `producerPermitPair` | `PermitPair` | 生产线程许可对 |
| `consumerPermitPair` | `PermitPair` | 消费线程许可对 |
| `pendingConsumerSlotSemaphore` | `Semaphore` | 在途消费槽位信号量 |
| `globalStorageSemaphore` | `Semaphore` | 全局存储信号量 |
| `globalConsumerLimit` | `int` | 全局消费线程上限 |

### 2.4 `DimensionLease`（资源租约）

```java
public interface DimensionLease extends AutoCloseable {
    String dimensionId();   // 维度 ID
    String getLeaseId();    // 全局唯一 ID（用于 activeLeases 跟踪）

    @Override
    void close();           // 幂等释放，多次调用安全

    static DimensionLease noop(String dimensionId) { ... }
}
```

**幂等保障**：`DefaultDimensionLease` 内部以 `AtomicBoolean released` 保证首次 `close()` 执行释放，后续调用仅计入 `idempotent_hit` 指标，不重复释放资源。

**泄露检测**：每个 `DefaultDimensionLease` 实例注册 JVM `Cleaner`。若租约在未调用 `close()` 的情况下被 GC，`LeakGuard` 会：
1. 调用 `onLeakDetected` 上报 `leak_detected` 指标并记录 `WARN` 日志；
2. 兜底执行 `dimension.onBusinessRelease(ctx)` 回收资源，防止信号量永久占用。

**`NoopDimensionLease`**：当维度未注册或无需占位时返回，`close()` 为空操作，不注册 Cleaner，不计入 `activeLeases`。

---

## 三、五个默认维度

框架内置五个维度，均通过 SPI 注册，可被业务实现覆盖（提供同 ID、更小 order 的实现）。

### 3.1 维度总览

| 维度 ID | 类 | order | 资源 | acquire 时机 | release 时机 |
|---------|----|-------|------|--------------|--------------|
| `in-flight-production` | `InFlightProductionDimension` | 100 | `PermitPair`（全局 + 每 Job） | `FlowLauncher.launch()` | deposit 任务 `finally` |
| `in-flight-consumer` | `InFlightConsumerDimension` | 100 | `pendingConsumerSlotSemaphore` | 数据出库提交消费前 | 消费任务 `finally` |
| `storage` | `StorageDimension` | 100 | `globalStorageSemaphore` | deposit 任务，semaphore 占位 | 条目离库时（`entry.closeStorageLease()`） |
| `producer-concurrency` | `ProducerConcurrencyDimension` | 100 | `PermitPair`（全局 + 每 Job） | 生产者线程进入时 | 生产者线程退出时 |
| `consumer-concurrency` | `ConsumerConcurrencyDimension` | 100 | `PermitPair`（全局 + 每 Job） | 消费者线程进入时 | 消费者线程退出时 |

### 3.2 `in-flight-production`（在途生产）

**语义**：限制"已 acquire、未完成 deposit"的并发生产条数，防止生产端无限提前，造成存储积压。

**acquire 行为**：
- `BLOCK_FOREVER` 模式：循环 `tryAcquire(200ms)`，每轮检查 `stopCheck`，Job 停止时退出。
- 超时模式：在配置的 `producerBackpressureTimeoutMill` 内循环，超时抛 `TimeoutException`。
- `PermitPair.tryAcquireBoth(1)` 同时占用全局 + 每 Job 两个 Semaphore，确保两级限制同时生效。

**release 行为**：`PermitPair.release(1)` 同时释放两个 Semaphore。

**调用链**：
```
FlowLauncher.launch()
  └── backpressureManager.acquire("in-flight-production", () -> stopped) → lease
        │
  FlowLauncher.submitDepositTask() → finally
        └── lease.close()  →  PermitPair.release(1)
```

### 3.3 `in-flight-consumer`（在途消费）

**语义**：限制"已离库、消费逻辑尚未终结"的并发条数，防止消费线程池雪崩或 OOM。

**acquire 行为**：`pendingConsumerSlotSemaphore.tryAcquire(30s)`，超时抛 `TimeoutException`。

**调用链**：
```
FlowFinalizer.submitDataToConsumer()
  └── backpressureManager.acquire("in-flight-consumer", null) → lease
        │
  消费任务 Runnable → finally
        └── lease.close()  →  slot.release(1)

MatchedPairProcessor.processMatchedPair()
  ├── backpressureManager.acquire("in-flight-consumer", null) → lease1（partner）
  ├── backpressureManager.acquire("in-flight-consumer", null) → lease2（entry）
  └── 消费任务 Runnable → finally
        ├── lease1.close()
        └── lease2.close()
```

### 3.4 `storage`（存储槽位）

**语义**：限制全局跨 Job 的存储条数，防止无界存储导致内存溢出。

**关键差异**：存储槽位的生命周期**跨越 deposit 任务**，条目在 storage 中存活期间一直持有租约，离库时才释放。因此租约以 `FlowEntry.storageLease` 形式随条目流转（见第四节）。

**acquire 行为**：`globalStorageSemaphore.acquire(1)`（阻塞式，无超时）。

**release 行为**：`resourceRegistry.releaseGlobalStorage(1)`。

**调用链**：
```
FlowLauncher.submitDepositTask()
  ├── backpressureManager.acquire("storage", () -> stopped) → storageLease
  ├── ctx.setStorageLease(storageLease)
  ├── getStorage().deposit(ctx)
  │     ├── 入槽成功 → storageLease 随 ctx 在 slot 中存活
  │     └── 未入槽（已配对/队满）→ ctx.closeStorageLease()（幂等）
  └── 异常 → ctx.closeStorageLease()

条目离库（任意路径）
  └── entry.closeStorageLease()  →  globalStorageSemaphore.release(1)
```

### 3.5 `producer-concurrency`（生产线程并发）

**语义**：限制全局及每 Job 的同时生产线程数，防止生产线程打满线程池。

**acquire/release**：使用 `producerPermitPair.tryAcquireBoth(1)` / `release(1)`。

### 3.6 `consumer-concurrency`（消费线程并发）

**语义**：限制全局及每 Job 的同时消费线程数，配合消费线程池容量保障稳定性。

**acquire/release**：使用 `consumerPermitPair.tryAcquireBoth(1)` / `release(1)`。

---

## 四、存储租约：FlowEntry.storageLease

### 4.1 设计动机

`storage` 维度的资源持有时间不同于其他维度：

| 维度 | lease 从 acquire 到 release 的生命周期 |
|------|----------------------------------------|
| `in-flight-production` | `launch()` → deposit 任务 `finally`（毫秒级） |
| `in-flight-consumer` | 出库提交消费 → 消费任务 `finally` |
| `storage` | deposit 成功 → 条目从存储离库（可能秒级甚至分钟级） |

将存储租约绑定在 `FlowEntry` 上，是让租约跟随条目生命周期的最自然方式。

### 4.2 FlowEntry 新增字段

```java
public class FlowEntry<T> implements AutoCloseable {
    // ... 原有字段

    /** 存储槽位租约：entry 进入 storage 时持有，离库时通过 closeStorageLease() 释放。 */
    private volatile DimensionLease storageLease;

    public void setStorageLease(DimensionLease lease) { ... }

    /** 幂等释放，storageLease 为 null 或已关闭均为空操作。 */
    public void closeStorageLease() {
        DimensionLease lease = storageLease;
        if (lease != null) { lease.close(); }
    }
}
```

### 4.3 存储租约释放点全景

`DimensionLease.close()` 的幂等性确保多路径交汇时不会双重释放。

| 触发位置 | 场景 | 释放哪个 entry |
|----------|------|----------------|
| `FlowLauncher.submitDepositTask()` | deposit 返回 false（含 `getParentReference` 内部已释放的情况） | 当前 incoming |
| `FlowLauncher.submitDepositTask()` catch | deposit 抛异常 | 当前 incoming |
| `MatchedPairProcessor.processMatchedPair()` | 配对成功，partner 离槽 | partner |
| `BoundedTimedFlowStorage.getParentReference()` | deposit 期间找到 partner，incoming 不入槽 | incoming |
| `BoundedTimedFlowStorage.processEvictedSlot()` 配对路径 | TTL 驱逐时 x 与 matched 配对，x 离槽 | x（matched 由 processMatchedPair 释放） |
| `BoundedTimedFlowStorage.processEvictedSlot()` 未匹配路径 | TTL 驱逐时无配对，条目直接离库 | unmatched entry |
| `BoundedTimedFlowStorage.drainExpiredEntries()` | 非匹配模式 TTL 驱逐 | 各 expired entry |
| `BoundedTimedFlowStorage.drainSlotForCompletion()` | completion drain（生产完成后主动消费） | 各 entry |
| `BoundedTimedFlowStorage.handleMatchingMode()` | CLEARED_AFTER_PAIR_SUCCESS（非多配对模式清槽） | 被清除的 entry |
| `BoundedTimedFlowStorage.handleMatchingMode()` | overflow（slot 满溢出） | overflow entry |
| `BoundedTimedFlowStorage.handleOverwriteModeLocked()` | 覆盖模式替换旧值 | 被替换的 data |
| `BoundedTimedFlowStorage.preRetry()` | 重入时找到配对，entry 为重入条目（storageLease 已关闭） | entry（幂等，空操作） |
| `BoundedTimedFlowStorage.shutdown()` | Job 关闭清空 storage | 各 remaining entry |
| `QueueFlowStorage.drainLoop()` | 队列 drain 循环 | 各出队 entry |
| `QueueFlowStorage.shutdown()` | 队列关闭清空 | 各 remaining entry |

---

## 五、BackpressureManager 详解

### 5.1 生命周期

- **创建**：`FlowLauncherFactory` 在 Job 启动时构建，传入 `DimensionContext`（含 Job 所有资源句柄）和 `MeterRegistry`。
- **销毁**：随 Job 关闭，`activeLeases` 中若存有未关闭租约，JVM Cleaner 在 GC 时兜底释放。

### 5.2 SPI 加载与路由

```
ServiceLoader.load(ResourceBackpressureDimension.class)
  → 按 id 分组
  → 同 id 取 order 最小者
  → 存入 dimensionMap（不可变 Map）
```

`acquire(dimensionId, stopCheck)` 的路由逻辑：
1. `dimensionMap.get(dimensionId)` 若为 null → 返回 `NoopDimensionLease`，不计入 activeLeases。
2. 构建 `DimensionContext`（深拷贝 baseCtx 字段，注入本次的 `dimensionId` 和 `stopCheck`）。
3. 调用 `dim.acquire(ctx)`；失败时 `acquireFailed++` 并重抛异常，不注册 lease。
4. 成功时生成 `leaseId = jobId:dimensionId:seq`，构造 `DefaultDimensionLease`，注册到 `activeLeases`，`acquireSuccess++`，返回 lease。

### 5.3 activeLeases 注册表

```java
ConcurrentHashMap<String, DimensionLease> activeLeases
AtomicInteger activeLeasesGauge  // 供 Gauge 指标读取
```

- `acquire` 成功：`activeLeases.put(leaseId, lease)`，`activeLeasesGauge++`。
- `close()`（非幂等）：`activeLeases.remove(leaseId)`，`activeLeasesGauge--`。
- `close()`（幂等重复调用）：仅增 `idempotentHit`，不操作 Map。
- 泄露检测：`onLeakDetected` 从 Map 移除，`activeLeasesGauge--`，增 `leakDetected`。

---

## 六、指标体系

### 6.1 维度级指标（每维度独立上报）

所有维度级指标均按 **global** 与 **per-job** 两个维度划分：

| 指标名 | 类型 | 标签 | 含义 |
|--------|------|------|------|
| `backpressure.dimension.acquire.attempts.global` | Counter | `jobId`, `dimensionId` | acquire 调用次数（global 层） |
| `backpressure.dimension.acquire.attempts.per_job` | Counter | `jobId`, `dimensionId` | acquire 调用次数（per-job 层） |
| `backpressure.dimension.acquire.blocked.global` | Counter | `jobId`, `dimensionId` | 因 global 信号量阻塞次数 |
| `backpressure.dimension.acquire.blocked.per_job` | Counter | `jobId`, `dimensionId` | 因 per-job 信号量阻塞次数 |
| `backpressure.dimension.acquire.timeout.global` | Counter | `jobId`, `dimensionId` | acquire 超时次数（global 层） |
| `backpressure.dimension.acquire.timeout.per_job` | Counter | `jobId`, `dimensionId` | acquire 超时次数（per-job 层） |
| `backpressure.dimension.acquire.duration.global` | Timer | `jobId`, `dimensionId` | acquire 耗时分布（global 层） |
| `backpressure.dimension.acquire.duration.per_job` | Timer | `jobId`, `dimensionId` | acquire 耗时分布（per-job 层） |
| `backpressure.dimension.release.count.global` | Counter | `jobId`, `dimensionId` | release 次数（global 层） |
| `backpressure.dimension.release.count.per_job` | Counter | `jobId`, `dimensionId` | release 次数（per-job 层） |

**注册条件**：仅当该维度的 `flow.limits.global.*` 配置 > 0 时注册上述指标；global 未启用时该维度不产生任何背压指标。

### 6.2 管理器级指标（每 Job 一组）

| 指标名 | 类型 | 标签 | 含义 |
|--------|------|------|------|
| `backpressure.manager.acquire.success.global` | Counter | `jobId` | acquire 成功次数（global 层） |
| `backpressure.manager.acquire.success.per_job` | Counter | `jobId` | acquire 成功次数（per-job 层） |
| `backpressure.manager.acquire.failed.global` | Counter | `jobId` | acquire 失败次数（超时于 global） |
| `backpressure.manager.acquire.failed.per_job` | Counter | `jobId` | acquire 失败次数（超时于 per-job） |
| `backpressure.manager.acquire.failed.other` | Counter | `jobId` | acquire 失败次数（中断、异常等无来源） |
| `backpressure.manager.lease.active.global` | Gauge | `jobId` | 当前活跃租约数（持有 global 许可） |
| `backpressure.manager.lease.active.per_job` | Gauge | `jobId` | 当前活跃租约数（持有 per-job 许可） |
| `backpressure.manager.release.idempotent_hit.global` | Counter | `jobId` | 幂等 close 次数（global 层） |
| `backpressure.manager.release.idempotent_hit.per_job` | Counter | `jobId` | 幂等 close 次数（per-job 层） |
| `backpressure.manager.release.leak_detected.global` | Counter | `jobId` | 泄露检测告警次数（global 层） |
| `backpressure.manager.release.leak_detected.per_job` | Counter | `jobId` | 泄露检测告警次数（per-job 层） |

**注册条件**：仅当至少有一个维度的 global 配置 > 0 时注册；所有维度均未启用 global 时不注册 Manager 级指标。

### 6.3 监控建议

- **背压强度**：`(acquire.blocked.global + acquire.blocked.per_job) / acquire.attempts`，超过 10% 时建议检查资源上限配置。
- **阻塞来源区分**：`blocked.global` 高表示全局配额紧张；`blocked.per_job` 高表示单 Job 配额紧张。
- **租约泄露**：`lease.active.global` / `lease.active.per_job` 长时间不归零或 `leak_detected.* > 0` 时，需排查是否有 `finally` 缺失。
- **幂等命中**：`idempotent_hit` 较高属正常现象（`getParentReference` 场景），无需告警。
- **性能瓶颈**：`acquire.duration.per_job` 或 `acquire.duration.global` 的 p99 超过业务 SLA 时，考虑放宽资源上限或调整阻塞模式。

---

## 七、SPI 扩展指南

### 7.1 实现自定义维度

```java
// 1. 实现接口
public class MyRateLimitDimension implements ResourceBackpressureDimension {

    public static final String ID = "my-rate-limit";

    @Override
    public String id() { return ID; }

    @Override
    public int order() { return 50; }   // 小于 100，覆盖同 ID 内置实现

    @Override
    public void acquire(DimensionContext ctx) throws InterruptedException, TimeoutException {
        // 自定义限流逻辑，从 ctx 取所需资源句柄
    }

    @Override
    public void onBusinessRelease(DimensionContext ctx) {
        // 释放自定义资源
    }
}

// 2. 注册到 SPI
// 文件：META-INF/services/com.lrenyi.template.flow.backpressure.ResourceBackpressureDimension
// 内容：com.example.MyRateLimitDimension
```

### 7.2 覆盖内置维度

若需替换 `storage` 维度的 acquire 逻辑（例如改为有超时的阻塞）：

```java
public class TimedStorageDimension implements ResourceBackpressureDimension {
    @Override
    public String id() { return "storage"; }   // 与内置维度相同的 ID

    @Override
    public int order() { return 10; }           // 小于内置的 100，优先执行

    @Override
    public void acquire(DimensionContext ctx) throws InterruptedException, TimeoutException {
        Semaphore sem = ctx.getGlobalStorageSemaphore();
        if (sem == null) return;
        if (!sem.tryAcquire(1, 5, TimeUnit.SECONDS)) {
            throw new TimeoutException("storage acquire timeout");
        }
    }

    @Override
    public void onBusinessRelease(DimensionContext ctx) {
        if (ctx.getResourceRegistry() != null) {
            ctx.getResourceRegistry().releaseGlobalStorage(1);
        }
    }
}
```

---

## 八、配置参考

背压维度依赖的资源上限均位于现有配置节点，维度本身**不引入新的配置字段**。

### 8.1 全局限制（`flow.limits.global`）

| 配置键 | 对应维度 | 说明 |
|--------|----------|------|
| `in-flight-production` | `in-flight-production` | 全局最大在途生产条数（0=不限） |
| `in-flight-consumer` | `in-flight-consumer` | 全局最大在途消费条数（0=不限） |
| `storage-capacity` | `storage` | 全局最大存储条数（0=不限） |
| `producer-threads` | `producer-concurrency` | 全局最大生产线程数（0=不限） |
| `consumer-threads` | `consumer-concurrency` | 全局最大消费线程数（0=不限） |

### 8.2 每 Job 限制（`flow.limits.per-job`）

| 配置键 | 对应维度 | 说明 |
|--------|----------|------|
| `in-flight-production` | `in-flight-production` | 每 Job 最大在途生产条数 |
| `in-flight-consumer` | `in-flight-consumer` | 每 Job 最大在途消费条数 |
| `storage-capacity` | `storage`（per-job 由 BoundedTimedFlowStorage 内部控制） | 每 Job 存储容量 |
| `producer-threads` | `producer-concurrency` | 每 Job 最大生产线程数 |
| `consumer-threads` | `consumer-concurrency` | 每 Job 最大消费线程数 |

### 8.3 生产背压阻塞模式（`flow.producer-backpressure-blocking-mode`）

| 值 | 行为 |
|----|------|
| `BLOCK_FOREVER`（默认） | 永久阻塞，每 200ms 检查 `stopCheck`，Job 停止时退出 |
| `BLOCK_WITH_TIMEOUT` | 阻塞最多 `producer-backpressure-timeout-mill` 毫秒，超时抛 `TimeoutException` |

---

## 九、关键设计决策记录

### 9.1 同 ID 多实现取最小 order，而非全部执行

维度是独立的资源申请语义，多个实现代表的是"替换"而非"叠加"。若全部执行则语义模糊（同一信号量被 acquire 多次），且会导致 release 次数不匹配。选最小 order 符合"优先级覆盖"的直觉。

### 9.2 存储租约附着在 FlowEntry 而非从 deposit 任务传递

存储槽位的生命周期远长于 deposit 任务，传递 lease 引用必须穿越存储内部的多个锁区和异步路径，风险极高。将 lease 作为 entry 的字段，每个离库路径只需 `entry.closeStorageLease()` 一行，与现有"entry 即生命周期单元"的设计高度吻合。

### 9.3 `DimensionLease.close()` 幂等设计解决双释放问题

`getParentReference()` 配对成功时，会在存储内部释放 incoming entry 的存储租约（`incoming.closeStorageLease()`），随后 `FlowLauncher.submitDepositTask()` 的 `!deposited` 分支也会调用 `ctx.closeStorageLease()`。幂等机制确保第二次调用是空操作，从根本上消除了旧代码中因两处 `releaseGlobalStorage(1)` 导致的许可证超发问题。

### 9.4 Cleaner 兜底不替代 finally，仅作告警与应急

JVM `Cleaner` 不保证执行时机，GC 延迟可能导致兜底释放远晚于预期。正确的使用姿势始终是 `try-with-resources` 或显式 `finally { lease.close(); }`，Cleaner 仅在编程错误（忘记关闭）时提供最后一道防线和可观测信号。

---

## 十、文件清单

```
template-flow/src/main/java/com/lrenyi/template/flow/
├── backpressure/
│   ├── ResourceBackpressureDimension.java     # SPI 接口
│   ├── BackpressureManager.java               # 中枢协调器（每 Job 一实例）
│   ├── DimensionContext.java                  # 调用上下文（Builder 模式）
│   ├── DimensionLease.java                    # 资源租约接口
│   ├── DefaultDimensionLease.java             # 幂等 + Cleaner 泄露检测实现
│   ├── NoopDimensionLease.java                # 空操作租约
│   ├── BackpressureMetricNames.java           # 指标名称与标签常量
│   └── dimension/
│       ├── InFlightProductionDimension.java   # 在途生产维度
│       ├── InFlightConsumerDimension.java     # 在途消费维度（原 pending-consumer）
│       ├── StorageDimension.java              # 存储槽位维度
│       ├── ProducerConcurrencyDimension.java  # 生产线程并发维度
│       └── ConsumerConcurrencyDimension.java  # 消费线程并发维度
├── context/
│   └── FlowEntry.java                         # +storageLease 字段与 closeStorageLease()
├── internal/
│   ├── FlowLauncher.java                      # acquire storage 改为 backpressureManager
│   ├── FlowFinalizer.java                     # acquire in-flight-consumer 改为 backpressureManager
│   └── MatchedPairProcessor.java              # partner.closeStorageLease() 替换 releaseGlobalStorage
├── storage/
│   ├── BoundedTimedFlowStorage.java           # 所有 releaseGlobalStorage → entry.closeStorageLease()
│   └── QueueFlowStorage.java                  # 同上
└── resources/
    └── META-INF/services/
        └── com.lrenyi.template.flow.backpressure.ResourceBackpressureDimension
            # 内置 5 个维度实现全限定名
```
