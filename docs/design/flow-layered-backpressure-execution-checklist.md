# Flow 分层背压与受控超时存储实现执行清单

本清单基于 `flow-layered-backpressure.md` 与 `flow-layered-backpressure-implementation-blueprint.md`，用于指导实际编码落地，覆盖从配置扩展到存储实现、接线和测试的所有关键步骤。

**状态说明**：

- 【未开始】：尚未在代码中动手
- 【进行中】：已有部分实现或正在调整
- 【已完成】：代码实现与测试均已就绪

建议按阶段顺序执行，单阶段内可根据团队安排并行实施，并在每个条目旁维护当前状态。

---

## Phase 1：配置与基础模型铺垫

### 1.1 配置扩展（`TemplateConfigProperties.Flow`）【状态：已完成】

**目标**：为软/硬超时与延期机制提供配置基础，并保留与现有字段的兼容关系。

**修改文件**：`TemplateConfigProperties.java`

1. 在 `TemplateConfigProperties.Flow.PerJob` 中新增字段：

   ```java
   private long softTimeoutMill;              // 软超时，毫秒
   private long hardTimeoutMill;              // 硬超时，毫秒
   private long expiryDeferInitialMill = 100; // 首次延期时长
   private long expiryDeferMaxMill = 1000;    // 单次最大延期
   private double expiryDeferBackoffMultiplier = 2.0D; // 退避倍数
   private boolean strictPendingConsumerSlot = true;   // 严格 pending slot 模式
   private int evictionBatchSize = 128;       // 单次协调处理 entry 上限
   private boolean storageCountByEntry = true; // 存储容量按 entry 计数
   ```

2. 新增便捷方法（放在 `PerJob` 内）：

   ```java
   public long getEffectiveSoftTimeoutMill() {
       long v = softTimeoutMill > 0 ? softTimeoutMill : cacheTtlMill;
       return v > 0 ? v : 10_000L;
   }

   public long getEffectiveHardTimeoutMill() {
       long soft = getEffectiveSoftTimeoutMill();
       long v = hardTimeoutMill >= soft ? hardTimeoutMill : soft * 6;
       return v;
   }
   ```

3. 可选：在 `TemplateConfigProperties.Flow.Global` 中预留全局驱逐协调配置字段（仅占位即可，第一版可以不使用）：

   ```java
   private int evictionCoordinatorThreads = 1;
   private long evictionScanIntervalMill = 50L;
   ```

4. 在 `validateConfig()` 中增加校验逻辑：

   - `softTimeoutMill > 0`（或使用 `getEffectiveSoftTimeoutMill()` 结果 > 0）。
   - `hardTimeoutMill >= softTimeoutMill`。
   - `expiryDeferInitialMill > 0`。
   - `expiryDeferMaxMill >= expiryDeferInitialMill`。
   - `evictionBatchSize > 0`。

   保留原有对 `cacheTtlMill` 的校验，但在日志或注释中说明：  
   **`cacheTtlMill` 主要作为软超时默认值使用，新语义由 `softTimeoutMill` / `hardTimeoutMill` 主导。**

---

### 1.2 扩展 `FlowEntry` 运行时元数据【状态：已完成】

**目标**：在 `FlowEntry` 上直接承载受控超时所需的 entry 级元数据，避免额外 envelope 对象。

**修改文件**：`FlowEntry.java`

1. 新增字段（保持 `@Getter` 生效）。超时按 key 在 FlowSlot 上维护，entry 不保存 soft/hard：

   ```java
   private long entryId;
   private long storedAtEpochMs;
   private volatile int runtimeState;
   private volatile int slotVersion;
   ```

2. 新增状态常量：

   ```java
   public static final int STATE_ACTIVE = 0;
   public static final int STATE_SOFT_EXPIRED = 1;
   public static final int STATE_EGRESSING = 2;
   public static final int STATE_REMOVED = 3;
   ```

3. 新增运行时初始化与状态更新方法（推荐包级可见或受限可见）：

   ```java
   void initRuntime(long entryId, long storedAtMs, int slotVersion) {
       this.entryId = entryId;
       this.storedAtEpochMs = storedAtMs;
       this.runtimeState = STATE_ACTIVE;
       this.slotVersion = slotVersion;
   }

   public int getRuntimeState() {
       return runtimeState;
   }

   public void setRuntimeState(int newState) {
       // 要求：只允许单向前进（可根据需要加入断言或日志）
       this.runtimeState = newState;
   }

   public int getSlotVersion() {
       return slotVersion;
   }

   public void setSlotVersion(int slotVersion) {
       this.slotVersion = slotVersion;
   }
   ```

4. 说明约束（Javadoc 或注释）：

   - `entryId` 在首次入库前由存储层赋值，在整个生命周期内不再变化。
   - `runtimeState` 只允许按 `ACTIVE -> SOFT_EXPIRED -> EGRESSING -> REMOVED` 单向前进。
   - `slotVersion` 用于与 slot 版本对齐，防止旧路径重复处理。

---

### 1.3 扩展 `FlowSlot<T>` 元数据【状态：已完成】

**目标**：在保持 `Deque<FlowEntry<T>>` 结构的基础上，为 slot 引入最小调度元信息。

**修改文件**：`FlowSlot.java`

1. 新增字段：

   ```java
   private final long slotId;
   private long earliestSoftExpireAt;
   private long earliestHardExpireAt;
   private long nextCheckAt;
   private int version;
   private boolean queuedForExpiry;
   private boolean draining;
   ```

2. 更新构造器签名：

   ```java
   public FlowSlot(long slotId,
                   int maxPerKey,
                   TemplateConfigProperties.Flow.MultiValueOverflowPolicy overflowPolicy) {
       this.slotId = slotId;
       this.deque = new ArrayDeque<>(Math.max(1, maxPerKey));
       this.maxPerKey = Math.max(1, maxPerKey);
       this.overflowPolicy = overflowPolicy != null ? overflowPolicy :
               TemplateConfigProperties.Flow.MultiValueOverflowPolicy.DROP_OLDEST;
   }
   ```

3. 新增必要的访问器方法：

   ```java
   public long getSlotId() { return slotId; }

   public long getEarliestSoftExpireAt() { return earliestSoftExpireAt; }
   public void setEarliestSoftExpireAt(long v) { this.earliestSoftExpireAt = v; }

   public long getEarliestHardExpireAt() { return earliestHardExpireAt; }
   public void setEarliestHardExpireAt(long v) { this.earliestHardExpireAt = v; }

   public long getNextCheckAt() { return nextCheckAt; }
   public void setNextCheckAt(long v) { this.nextCheckAt = v; }

   public int getVersion() { return version; }
   public void setVersion(int version) { this.version = version; }

   public boolean isQueuedForExpiry() { return queuedForExpiry; }
   public void setQueuedForExpiry(boolean queuedForExpiry) { this.queuedForExpiry = queuedForExpiry; }

   public boolean isDraining() { return draining; }
   public void setDraining(boolean draining) { this.draining = draining; }
   ```

4. 为扫描与摘除提供辅助方法：

   ```java
   public Iterable<FlowEntry<T>> entries() {
       return deque;
   }

   public boolean remove(FlowEntry<T> entry) {
       return deque.remove(entry);
   }
   ```

5. 调整 `FlowSlot` 使用处（如 `CaffeineFlowStorage` 之后的 `BoundedTimedFlowStorage`）构造调用，传入 `slotId`。

---

### 1.4 `FlowStorage` 接口增强【状态：已完成】

**目标**：为显式 entry 计数和受控超时能力提供统一视图，兼容现有实现。

**修改文件**：`FlowStorage.java`

1. 在接口中新增默认方法：

   ```java
   default long usedEntries() {
       return size();
   }

   default long entryLimit() {
       return maxCacheSize();
   }

   default boolean supportsDeferredExpiry() {
       return false;
   }
   ```

2. 保持现有方法 `size()` / `maxCacheSize()` 不变，以兼容 `QueueFlowStorage` 等实现。

---

## Phase 2：过期调度与下游压力评估基础类

### 2.1 `SlotExpiryToken`【状态：已完成】

**目标**：为 DelayQueue 提供最小的 slot 级调度对象。

**新增文件**：`template-flow/src/main/java/com/lrenyi/template/flow/storage/SlotExpiryToken.java`

```java
package com.lrenyi.template.flow.storage;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

final class SlotExpiryToken implements Delayed {
    private final long slotId;
    private final long nextCheckAt;
    private final int version;

    SlotExpiryToken(long slotId, long nextCheckAt, int version) {
        this.slotId = slotId;
        this.nextCheckAt = nextCheckAt;
        this.version = version;
    }

    long slotId() {
        return slotId;
    }

    long nextCheckAt() {
        return nextCheckAt;
    }

    int version() {
        return version;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        long diff = nextCheckAt - System.currentTimeMillis();
        return unit.convert(diff, TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed other) {
        if (this == other) {
            return 0;
        }
        long diff = getDelay(TimeUnit.MILLISECONDS) - other.getDelay(TimeUnit.MILLISECONDS);
        return diff < 0 ? -1 : (diff > 0 ? 1 : 0);
    }
}
```

> 约束：不添加任何 `FlowSlot` / `FlowEntry` / 业务数据 / `joinKey` 等引用。

---

### 2.2 `DownstreamPressureEvaluator`【状态：已完成】

**目标**：集中封装“当前是否允许 soft timeout drain”的判定逻辑。

**新增文件**：  
接口：`template-flow/src/main/java/com/lrenyi/template/flow/internal/DownstreamPressureEvaluator.java`  
实现：`DefaultDownstreamPressureEvaluator`（同包）

```java
package com.lrenyi.template.flow.internal;

public interface DownstreamPressureEvaluator {
    ExpiryDecision evaluate(String jobId, long nowEpochMs);

    record ExpiryDecision(
            ExpiryDecisionType type,
            String reason,
            long suggestedDelayMs
    ) {}

    enum ExpiryDecisionType {
        ALLOW_SOFT_DRAIN,
        DEFER,
        FORCE_HARD_DRAIN
    }
}
```

默认实现示例（简化版，实际落地时可注入更多信号）：

```java
package com.lrenyi.template.flow.internal;

import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

public final class DefaultDownstreamPressureEvaluator implements DownstreamPressureEvaluator {
    private final IntSupplier jobConsumerAvailablePermits;
    private final IntSupplier globalConsumerAvailablePermits;
    private final LongSupplier perJobPendingCount;
    private final int perJobPendingLimit;
    private final LongSupplier globalPendingCount;
    private final int globalPendingLimit;
    private final long deferInitialMs;
    private final long deferMaxMs;

    public DefaultDownstreamPressureEvaluator(
            IntSupplier jobConsumerAvailablePermits,
            IntSupplier globalConsumerAvailablePermits,
            LongSupplier perJobPendingCount,
            int perJobPendingLimit,
            LongSupplier globalPendingCount,
            int globalPendingLimit,
            long deferInitialMs,
            long deferMaxMs
    ) {
        this.jobConsumerAvailablePermits = jobConsumerAvailablePermits;
        this.globalConsumerAvailablePermits = globalConsumerAvailablePermits;
        this.perJobPendingCount = perJobPendingCount;
        this.perJobPendingLimit = perJobPendingLimit;
        this.globalPendingCount = globalPendingCount;
        this.globalPendingLimit = globalPendingLimit;
        this.deferInitialMs = deferInitialMs;
        this.deferMaxMs = deferMaxMs;
    }

    @Override
    public ExpiryDecision evaluate(String jobId, long nowEpochMs) {
        // 简化规则：任一条件成立则 DEFER
        if (jobConsumerAvailablePermits != null && jobConsumerAvailablePermits.getAsInt() <= 0) {
            return new ExpiryDecision(ExpiryDecisionType.DEFER, "consumer_permits_exhausted", deferInitialMs);
        }
        if (globalConsumerAvailablePermits != null && globalConsumerAvailablePermits.getAsInt() <= 0) {
            return new ExpiryDecision(ExpiryDecisionType.DEFER, "global_consumer_permits_exhausted", deferInitialMs);
        }
        if (perJobPendingLimit > 0 && perJobPendingCount != null
                && perJobPendingCount.getAsLong() >= perJobPendingLimit) {
            return new ExpiryDecision(ExpiryDecisionType.DEFER, "pending_consumer_overflow", deferInitialMs);
        }
        if (globalPendingLimit > 0 && globalPendingCount != null
                && globalPendingCount.getAsLong() >= globalPendingLimit) {
            return new ExpiryDecision(ExpiryDecisionType.DEFER, "global_pending_consumer_overflow", deferInitialMs);
        }
        return new ExpiryDecision(ExpiryDecisionType.ALLOW_SOFT_DRAIN, "ok", 0L);
    }
}
```

> 说明：硬超时 `FORCE_HARD_DRAIN` 通常在存储层根据 `hardExpireAt` 判断，`evaluate()` 可不负责该分支。

---

### 2.3 `EvictionCoordinator`【状态：已完成】

**目标**：单线程消费 `DelayQueue<SlotExpiryToken>`，将 token 转交给存储层处理。

**新增文件**：`template-flow/src/main/java/com/lrenyi/template/flow/storage/EvictionCoordinator.java`

```java
package com.lrenyi.template.flow.storage;

import java.util.Objects;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class EvictionCoordinator implements AutoCloseable {
    private final DelayQueue<SlotExpiryToken> expiryQueue;
    private final BoundedTimedFlowStorage<?> storage;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private Thread worker;

    public EvictionCoordinator(DelayQueue<SlotExpiryToken> expiryQueue,
                               BoundedTimedFlowStorage<?> storage,
                               String threadName) {
        this.expiryQueue = Objects.requireNonNull(expiryQueue, "expiryQueue");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.worker = new Thread(this::runLoop, threadName);
        this.worker.setDaemon(true);
    }

    public void start() {
        worker.start();
    }

    private void runLoop() {
        try {
            while (!closed.get()) {
                SlotExpiryToken token = expiryQueue.take();
                handleToken(token);
            }
        } catch (InterruptedException e) {
            if (!closed.get()) {
                log.warn("EvictionCoordinator interrupted unexpectedly", e);
            }
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            log.error("EvictionCoordinator loop failed", t);
        } finally {
            log.info("EvictionCoordinator stopped");
        }
    }

    private void handleToken(SlotExpiryToken token) {
        try {
            storage.onExpiryToken(token);
        } catch (Throwable t) {
            log.error("Failed to handle SlotExpiryToken for slotId={}", token.slotId(), t);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (worker != null) {
            worker.interrupt();
        }
    }
}
```

> 说明：第一版每个 `BoundedTimedFlowStorage` 拥有一个独立协调线程，后续可根据 `Global` 配置集中化。

---

## Phase 3：实现 `BoundedTimedFlowStorage<T>`

> 本阶段是实现的核心部分，建议严格按 blueprint 中的方法划分与锁顺序规则实现。

### 3.1 类骨架与字段【状态：已完成】

**新增文件**：`template-flow/src/main/java/com/lrenyi/template/flow/storage/BoundedTimedFlowStorage.java`

1. 类声明：

   ```java
   @Slf4j
   public final class BoundedTimedFlowStorage<T> extends AbstractEgressFlowStorage<T> implements FlowStorage<T> {
   }
   ```

2. 字段：

   ```java
   private static final int STRIPE_COUNT = 256;
   private static final Lock[] KEY_STRIPES = new Lock[STRIPE_COUNT];
   static {
       for (int i = 0; i < STRIPE_COUNT; i++) {
           KEY_STRIPES[i] = new ReentrantLock();
       }
   }

   private final ConcurrentMap<String, FlowSlot<T>> slotByKey = new ConcurrentHashMap<>();
   private final ConcurrentMap<Long, FlowSlot<T>> slotById = new ConcurrentHashMap<>();
   private final DelayQueue<SlotExpiryToken> expiryQueue = new DelayQueue<>();

   private final LongAdder usedEntryCount = new LongAdder();
   private final AtomicLong expiryReadyEntryCount = new AtomicLong(0L);
   private final LongAdder forcedExpiryCount = new LongAdder();

   private final AtomicLong slotIdGenerator = new AtomicLong(1L);
   private final AtomicLong entryIdGenerator = new AtomicLong(1L);

   private final EvictionCoordinator evictionCoordinator;
   private final DownstreamPressureEvaluator downstreamPressureEvaluator;
   private final TemplateConfigProperties.Flow.PerJob perJob;
   private final int maxPerKey;
   private final String jobId;
   private final Clock clock;
   ```

3. 构造函数（示例）：

   ```java
   public BoundedTimedFlowStorage(TemplateConfigProperties.Flow flowConfig,
                                  FlowJoiner<T> joiner,
                                  ProgressTracker progressTracker,
                                  FlowFinalizer<T> finalizer,
                                  FlowEgressHandler<T> egressHandler,
                                  FlowResourceRegistry resourceRegistry,
                                  MeterRegistry meterRegistry,
                                  String jobId) {
       super(joiner, finalizer, progressTracker, meterRegistry, egressHandler);
       this.perJob = flowConfig.getLimits().getPerJob();
       this.maxPerKey = perJob.getEffectiveMultiValueMaxPerKey();
       this.jobId = jobId;
       this.clock = Clock.systemUTC();

       // 下游压力评估器：后续可注入更多信号
       this.downstreamPressureEvaluator = new DefaultDownstreamPressureEvaluator(
               () -> /* job consumer permits */,    // 需在接线阶段补充
               () -> /* global consumer permits */, // 同上
               () -> /* per-job pending count */,
               perJob.getEffectivePendingConsumer(),
               () -> /* global pending count */,
               flowConfig.getLimits().getGlobal().getPendingConsumer(),
               perJob.getExpiryDeferInitialMill(),
               perJob.getExpiryDeferMaxMill()
       );

       this.evictionCoordinator = new EvictionCoordinator(
               expiryQueue,
               this,
               "app-template-flow-eviction-" + jobId
       );
       this.evictionCoordinator.start();

       Gauge.builder(FlowMetricNames.LIMITS_STORAGE_USED, this, s -> s.usedEntries())
            .tag(FlowMetricNames.TAG_JOB_ID, jobId)
            .tag(FlowMetricNames.TAG_STORAGE_TYPE, "bounded")
            .description("每 Job 存储当前 entry 数")
            .register(meterRegistry);
       Gauge.builder(FlowMetricNames.LIMITS_STORAGE_LIMIT, this::entryLimit)
            .tag(FlowMetricNames.TAG_JOB_ID, jobId)
            .tag(FlowMetricNames.TAG_STORAGE_TYPE, "bounded")
            .description("每 Job 存储 entry 上限")
            .register(meterRegistry);
   }
   ```

> 说明：`DefaultDownstreamPressureEvaluator` 中的信号先用占位 lambda，接线阶段再补齐。

---

### 3.2 接口方法实现【状态：已完成】

1. `doDeposit(FlowEntry<T> entry)`：

   - 计算当前时间：`long now = clock.millis();`
   - `initializeEntryRuntime(entry, now);`
   - 计算 `key = joiner().joinKey(entry.getData());`
   - 取对应的 stripe lock：

     ```java
     Lock stripe = KEY_STRIPES[(key.hashCode() & 0x7FFFFFFF) % STRIPE_COUNT];
     List<Runnable> afterUnlock = new ArrayList<>(2);
     stripe.lock();
     try {
         FlowSlot<T> slot = slotByKey.computeIfAbsent(key, k -> {
             long slotId = slotIdGenerator.getAndIncrement();
             FlowSlot<T> created = new FlowSlot<>(slotId, maxPerKey, perJob.getMultiValueOverflowPolicy());
             slotById.put(slotId, created);
             return created;
         });

         boolean deposited = joiner().needMatched()
                 ? handleMatchingModeLocked(key, slot, entry, afterUnlock)
                 : handleOverwriteModeLocked(key, slot, entry, afterUnlock);

         if (!deposited) {
             return false;
         }

         usedEntryCount.increment();
         updateSlotExpiryMetadata(slot);
         enqueueSlotExpiryIfNeeded(slot);
         return true;
     } finally {
         stripe.unlock();
     }
     // 锁外执行 afterUnlock
     runAll(afterUnlock);
     ```

   - `handleMatchingModeLocked` / `handleOverwriteModeLocked` 可复用 `CaffeineFlowStorage` 中现有逻辑，但改为操作 `slotByKey/slotById` 而非 `cache`。

2. `size()` / `maxCacheSize()` / `usedEntries()` / `entryLimit()`：

   ```java
   @Override
   public long size() {
       return usedEntryCount.sum();
   }

   @Override
   public long maxCacheSize() {
       return perJob.getKeyedCache().getStorageCapacity();
   }

   @Override
   public long usedEntries() {
       return usedEntryCount.sum();
   }

   @Override
   public long entryLimit() {
       return perJob.getKeyedCache().getStorageCapacity();
   }
   ```

3. `supportsDeferredExpiry()`：

   ```java
   @Override
   public boolean supportsDeferredExpiry() {
       return true;
   }
   ```

4. `drainRemainingToFinalizer()`：

   - 遍历 `slotByKey`，在对应 stripe lock 内：
     - 将 slot 内所有 entry `remove` 出队。
     - 将这些 entry 提交给 `handleEgress(key, entry, EgressReason.SINGLE_CONSUMED, false)`。
     - 同时 `resourceRegistry().releaseGlobalStorage(1)`、`usedEntryCount--`。
   - 返回排空条数。

5. `shutdown()`：

   - 标记关闭：
     - `evictionCoordinator.close();`
   - 对所有 slot 做 `SHUTDOWN` drain：
     - 与 `drainRemainingToFinalizer` 类似，只是 reason 使用 `EgressReason.SHUTDOWN`，`skipRetry=true`。
   - 清空 `slotByKey/slotById/expiryQueue`。

---

### 3.3 过期处理方法【状态：已完成】

1. `onExpiryToken(SlotExpiryToken token)`：

   ```java
   public void onExpiryToken(SlotExpiryToken token) {
       long now = clock.millis();
       drainExpiredEntries(token.slotId(), token.version(), now);
   }
   ```

2. `drainExpiredEntries(long slotId, int expectedVersion, long nowEpochMs)`：

   - 按设计文档中的伪代码实现，关键步骤包括：
     - 通过 `slotById.get(slotId)` 获取 slot；为空则 return 0。
     - 通过 `slot.currentKey()`（可在 slot 中附加 key 字段，或在 `slotByKey` 反查）获取 key。
     - 使用同一 stripe lock：
       - 校验 `slotById.get(slotId) == slot`。
       - 校验 `slot.getVersion() == expectedVersion`。
       - 校验 `!slot.isDraining()`。
       - 收集 `hardExpired` 列表（按 key）：
         - 若 `now >= slot.getEarliestHardExpireAt()`，将 slot 内所有 entry 加入列表。
       - 若 `hardExpired` 非空：
         - `slot.setDraining(true);`
         - `removeEntriesForEgress(key, slot, hardExpired);`
         - 将 batch 记录为 `EgressReason.TIMEOUT`, `skipRetry=true`。
       - 否则：
         - 收集 `softExpired` 列表（按 key）：若 `now >= slot.getEarliestSoftExpireAt()`，将 slot 内所有 entry 标记为 `STATE_SOFT_EXPIRED` 并加入列表，`expiryReadyEntryCount` 按条数增加。
         - 若 `softExpired` 非空：
           - 调用 `downstreamPressureEvaluator.evaluate(jobId, now)`：
             - `ALLOW_SOFT_DRAIN` → `slot.setDraining(true)` + `removeEntriesForEgress(..., skipRetry=false)`。
             - `DEFER` → `requeueSlotExpiry(slot, computeNextCheckAt(slot, decision, now));`
         - 若 soft/hard 都为空：
           - `requeueBasedOnEarliestFutureExpiry(slot);`
       - `cleanupEmptySlot(key, slot);`
     - 锁外：对每个 `ExpiredBatch` 调用 `submitExpiredEntries(...)`。

3. 私有辅助方法（按 blueprint 建议拆分）：

   - `initializeEntryRuntime(FlowEntry<T> entry, long now)`：设置 entryId 与 storedAtMs（超时按 key 在 slot 上维护）。
   - `updateSlotExpiryMetadata(FlowSlot<T> slot)`：由 slot 的 `earliestStoredAtEpochMs` + 配置的 soft/hard 计算 `earliestSoftExpireAt/earliestHardExpireAt`。
   - `enqueueSlotExpiryIfNeeded(FlowSlot<T> slot)`：根据 `queuedForExpiry` 与 `earliestSoftExpireAt` 等字段入队。
   - `requeueSlotExpiry(FlowSlot<T> slot, long nextCheckAt)`。
   - `collectHardExpired/collectSoftExpired`。
   - `removeEntriesForEgress(String key, FlowSlot<T> slot, List<FlowEntry<T>> expired)`：
     - 设置 `runtimeState` 为 `STATE_EGRESSING`。
     - 从 slot 移除 entry。
     - 更新 `usedEntryCount`、`expiryReadyEntryCount` 等。
     - 更新 `updateSlotExpiryMetadata(slot)`，必要时重新登记 token。
   - `cleanupEmptySlot(key, slot)`：清理 `slotByKey/slotById`。

---

## Phase 4：接线与命名收口

### 4.1 `FlowStorageType` 与 `FlowJoiner` 默认存储类型【状态：已完成】

**目标**：从“Caffeine 专有”命名收口到通用本地受控存储类型。

**修改文件**：`FlowStorageType.java`、`FlowJoiner.java`

1. 将 `FlowStorageType` 中 `CAFFEINE` 改名为更通用值（例如 `LOCAL_BOUNDED`），并更新 Javadoc：

   ```java
   public enum FlowStorageType {
       /**
        * 本地受控超时存储（BoundedTimedFlowStorage）
        */
       LOCAL_BOUNDED,

       QUEUE;

       public static FlowStorageType from(String value) {
           for (FlowStorageType type : values()) {
               if (type.name().equalsIgnoreCase(value)) {
                   return type;
               }
           }
           return LOCAL_BOUNDED;
       }
   }
   ```

2. 更新 `FlowJoiner.getStorageType()` 默认返回：

   ```java
   default FlowStorageType getStorageType() {
       return FlowStorageType.LOCAL_BOUNDED;
   }
   ```

---

### 4.2 新增 `BoundedTimedFlowStorageFactory`【状态：已完成】

**新增文件**：`template-flow/src/main/java/com/lrenyi/template/flow/storage/BoundedTimedFlowStorageFactory.java`

```java
package com.lrenyi.template.flow.storage;

import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.internal.FlowEgressHandler;
import com.lrenyi.template.flow.internal.FlowFinalizer;
import com.lrenyi.template.flow.model.FlowStorageType;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import io.micrometer.core.instrument.MeterRegistry;

public class BoundedTimedFlowStorageFactory implements FlowStorageFactory {

    @Override
    public FlowStorageType getSupportedType() {
        return FlowStorageType.LOCAL_BOUNDED;
    }

    @Override
    public boolean supports(FlowStorageType type) {
        return type == FlowStorageType.LOCAL_BOUNDED;
    }

    @Override
    public <T> FlowStorage<T> createStorage(String jobId,
            FlowJoiner<T> joiner,
            TemplateConfigProperties.Flow config,
            FlowFinalizer<T> finalizer,
            ProgressTracker progressTracker,
            FlowEgressHandler<T> egressHandler,
            FlowResourceRegistry resourceRegistry,
            MeterRegistry meterRegistry) {
        return new BoundedTimedFlowStorage<>(config,
                                             joiner,
                                             progressTracker,
                                             finalizer,
                                             egressHandler,
                                             resourceRegistry,
                                             meterRegistry,
                                             jobId);
    }

    @Override
    public int getPriority() {
        return 5;
    }
}
```

在 `META-INF/services/com.lrenyi.template.flow.storage.FlowStorageFactory` 中注册该工厂，并移除旧的 Caffeine 工厂注册。

---

### 4.3 移除 Caffeine 专用实现【状态：已完成】

**目标**：不再依赖 Caffeine 的 TTL/maximumSize 自动驱逐路径。

**操作**：

1. 删除 `CaffeineFlowStorage.java` 及其对应的 `FlowStorageFactory` 实现（若存在）。
2. 删除 SPI 注册中指向 Caffeine 的条目。
3. 检查引用，替换注释中的“基于 Caffeine 实现”为“本地受控超时存储”等通用描述。

---

### 4.4 BackpressureController 兼容新存储视图【状态：已完成】

**修改文件**：`BackpressureController.java`

1. 在 `awaitSpace` 中，将 `cacheFull` 判断从：

   ```java
   boolean cacheFull = flowStorage.size() >= flowStorage.maxCacheSize();
   ```

   更新为（示例实现）：

   ```java
   boolean cacheFull;
   if (flowStorage.supportsDeferredExpiry()) {
       cacheFull = flowStorage.usedEntries() >= flowStorage.entryLimit();
   } else {
       cacheFull = flowStorage.size() >= flowStorage.maxCacheSize();
   }
   ```

2. 可选：增加 `BackpressureSnapshot` 与 `isDownstreamOverloaded()` 方法，供 `DefaultDownstreamPressureEvaluator` 使用。

---

### 4.5 FlowLauncherFactory & FlowCacheManager【状态：已完成】

**修改文件**：`FlowLauncherFactory.java`、`FlowCacheManager.java`

1. `FlowLauncherFactory` 中创建 `FlowStorage` 的逻辑保持不变（仍经由 `FlowCacheManager` 与 `FlowStorageFactoryLoader`），只需要确保：
   - `FlowJoiner.getStorageType()` 默认值已指向 `LOCAL_BOUNDED`。
   - 新的 `BoundedTimedFlowStorageFactory` 能被 SPI 成功加载。

2. `FlowMetricNames.TAG_STORAGE_TYPE` 的取值：
   - 对 `BoundedTimedFlowStorage`，使用 `"bounded"`。
   - 对 Queue 存储，保持 `"queue"`。

---

### 4.6 FlowFinalizer 严格 pending 模式【状态：已完成】

**修改文件**：`FlowFinalizer.java`

1. 引入 `TemplateConfigProperties.Flow.PerJob.strictPendingConsumerSlot` 配置（可从 `FlowLauncher` 或 `FlowResourceContext` 中间接获取）。

2. 在 `submitDataToConsumer` 中：

   - 当 `slotSemaphore != null` 且 `tryAcquire` 超时：

     - 若 `strictPendingConsumerSlot == true`：
       - 记录一次 acquire timeout 指标。
       - 不再“submitting anyway”，而是 **直接返回**，不提交消费任务。
     - 若 `strictPendingConsumerSlot == false`：
       - 保留现有“提交 anyway”行为，同时记录兼容指标。

3. 新增相关指标（在 `FlowMetricNames` 中定义）：

   - pending slot acquire timeout total。
   - finalizer 提交失败计数等。

---

## Phase 5：指标、日志与测试

### 5.1 指标完整性【状态：已完成】

**修改文件**：`FlowMetricNames.java`、`BoundedTimedFlowStorage.java`、`FlowFinalizer.java` 等。

1. 在 `FlowMetricNames` 中新增与受控超时相关的指标常量（参考设计文档 17.1）：

   - `app.template.flow.storage.expiry.ready`
   - `app.template.flow.storage.expiry.deferred`
   - `app.template.flow.storage.expiry.defer.total`
   - `app.template.flow.storage.expiry.force.total`
   - `app.template.flow.storage.expiry.allow.total`
   - `app.template.flow.storage.expiry.delay.duration`

2. 在 `BoundedTimedFlowStorage` 中适当位置埋点：

   - `collectSoftExpired`：`expiry.ready` / `expiry.ready.total`。
   - `requeueSlotExpiry`：`expiry.deferred` / `downstream.overloaded.total{reason=...}`。
   - 硬超时强制离库路径：`expiry.force.total`。
   - 真正离库后：`entries.used`、`expiry.delay.duration`。

3. 更新 `LIMITS_STORAGE_USED/LIMITS_STORAGE_LIMIT` 的语义说明为“entry 数”，文档中明确与旧版 key 数语义的差异。

---

### 5.2 日志策略【状态：已完成】

**关键点**：

- INFO/WARN：
  - hard timeout 发生。
  - 某 slot 延期次数持续增长（可选实现为 threshold 检测）。
  - pending slot acquire timeout（无论 strict 与否）。
  - eviction coordinator 异常退出。
- DEBUG：
  - 普通 token 出队。
  - 延期重排。
  - slot 元数据更新。

确保高压场景下不会因 DEBUG 日志放大导致 I/O 压力。

---

### 5.3 测试矩阵落地【状态：已完成】

---

## Phase 6：附加抽象与实现形态优化（可选增强）

> 本阶段内容对应设计文档中的“实现形态建议”，不影响核心功能闭环，但有助于后续扩展与维护。

### 6.1 `DeferredExpiryPolicy` 策略对象【状态：已完成】

**目标**：将延期退避逻辑从存储实现中抽离，形成可配置/可替换的策略。

**要点**：

- 定义接口 `DeferredExpiryPolicy`，提供：
  - 计算首次延期时长；
  - 在给定 `deferCount` 下计算下一次延期时长；
  - 限制总延期时长不超过 `hardTimeout - softTimeout`。
- 默认实现使用文档推荐的指数退避：`100ms -> 200ms -> 400ms -> 800ms -> 1000ms（封顶）`。
- 在 `BoundedTimedFlowStorage` 的 `requeueSlotExpiry` / `computeNextCheckAt` 中使用该策略，而不是硬编码逻辑。

### 6.2 `ExpiryIndex` 抽象【状态：已完成】

**目标**：为过期调度索引（DelayQueue/最小堆等）形成统一抽象，便于后续切换实现。

**要点**：

- 定义接口 `ExpiryIndex`，封装：
  - 注册新的 `SlotExpiryToken`；
  - 取消/逻辑失效旧任务（可仅依赖 version 语义）；
  - 获取下一待处理 token。
- 首版可简单包装 `DelayQueue<SlotExpiryToken>`，保留与当前实现几乎相同的行为。

### 6.3 `FlowStoragePressureView` 视图接口【状态：已完成】

**目标**：集中暴露存储压力视图，减少 `BackpressureController` 对具体存储实现的依赖。

**要点**：

- 在 `FlowStorage` 之上定义小接口：

  ```java
  interface FlowStoragePressureView {
      long usedEntries();
      long entryLimit();
      boolean isFull();
  }
  ```

- 为 `BoundedTimedFlowStorage` 提供一个简单实现；Queue 存储可按需实现或使用默认视图。
- 在 `BackpressureController` 中优先依赖该视图，而非直接访问 `FlowStorage`。

### 6.4 `BackpressureSnapshot` 完整快照【状态：已完成】

**目标**：为 `DownstreamPressureEvaluator` 提供统一的下游压力快照对象，减少多处采样与重复逻辑。

**要点**：

- 定义 `BackpressureSnapshot`，包含：
  - `storageUsed/storageLimit`；
  - `jobConsumerAvailablePermits/globalConsumerAvailablePermits`；
  - `perJobPendingCount/globalPendingCount`；
  - `pendingConsumerSlotAvailablePermits`（如有）。
- 在 `BackpressureController` 中提供：
  - `BackpressureSnapshot snapshot()`；
  - `boolean isDownstreamOverloaded()`。
- `DefaultDownstreamPressureEvaluator` 改为依赖 `BackpressureSnapshot`，简化内部 if/else。

### 6.5 EvictionCoordinator 线程资源集中化【状态：已完成】

**目标**：在高 Job 数量场景下，减少“每 Job 一个协调线程”的资源占用。

**要点**：

- 在 `FlowResourceRegistry` 中新增 `ScheduledExecutorService evictionCoordinatorExecutor` 或通用 executor。
- 将 `EvictionCoordinator` 改为复用该 executor 提交任务，而不是每个 storage 自建线程：
  - 例如：用一个线程轮询多个 `DelayQueue`，或用任务提交的方式拉平。
- 保持与当前实现的锁顺序和错误处理语义一致。


**单元测试**（`template-flow/src/test/java`）：

1. `FlowEntry`：
   - 校验 `initRuntime` 正确赋值。
   - 校验 `runtimeState` 可以按设计状态迁移。

2. `FlowSlot`：
   - 按 key：`earliestStoredAtEpochMs` 在创建时设置，`earliestSoftExpireAt/earliestHardExpireAt` 由 slot 级时间 + 配置计算正确。
   - `remove` 与 `entries()` 遍历行为正确。

3. `BoundedTimedFlowStorage`：
   - soft timeout 到期 + 下游过载时，entry 不被摘除、`usedEntryCount` 保持不变。
   - hard timeout 到达时，一定摘除 entry，并计入 `forcedExpiryCount`。
   - 空 slot 正确从 `slotByKey/slotById` 删除。

4. `DownstreamPressureEvaluator`：
   - 各种输入组合下正确返回 `ALLOW_SOFT_DRAIN` / `DEFER`。

**并发测试**：

1. 生产写入与协调器 drain 同时命中同一 key：
   - 验证不会重复离库或重复释放 storage permit。

2. 配对成功路径与 timeout token 同时命中同一 slot：
   - 验证最终只有一方成功将 entry 标记为 `EGRESSING` 并触发 egress。

3. shutdown 与 soft timeout drain 并发：
   - 验证 shutdown 优先级更高，所有 entry 最终以 `SHUTDOWN` 或完成态离开。

**集成测试**：

1. 下游消费慢场景：
   - 模拟消费端长期卡慢，观察：
     - soft timeout entry 不立即离库。
     - usedEntries 接近 entryLimit 时，生产被 `awaitBackpressure` 阻塞。

2. 下游恢复：
   - 恢复消费速度，观察延期 entry 逐步离库，`usedEntries` 下降，生产重新流畅。

3. 多 Job 并发：
   - 配置 per-job 与 global storage/pending 限制，观察指标与行为。

4. source finished：
   - 模拟 source 完成，调用 `drainRemainingToFinalizer`，验证剩余 entry 以完成态离库。

**Queue 存储回归**：

1. 确认 `QueueFlowStorage` 行为未被 `FlowStorage` 接口扩展破坏：
   - `supportsDeferredExpiry()` 仍为 `false`。
   - `size()/maxCacheSize()` 语义保持不变。
   - Queue 相关测试全部通过。

---

## 总结

本执行清单将高层设计拆解为可直接编码的步骤，覆盖：

- 配置扩展与模型字段落地；
- slot 级 token 与下游压力评估基础类；
- `BoundedTimedFlowStorage` 的实现与接线；
- Backpressure/Finalizer 行为调整与指标更新；
- 单元、并发与集成测试矩阵。

实际实现时，建议严格遵守：

- 唯一锁顺序：统一使用 key stripe lock；
- 到期不等于离库：soft timeout 仅标记可驱逐；
- 真正离库前不释放 storage permit；
- 所有离库路径只释放一次 permit 与引用计数。

完成本清单后，Flow 引擎即具备文档中描述的“分层背压 + 受控超时存储”能力。

