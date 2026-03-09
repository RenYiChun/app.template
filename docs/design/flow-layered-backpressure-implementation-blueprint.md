# Flow 分层背压实现蓝图

## 一、文档目的

本文不是概念设计，而是 [Flow 分层背压与受控超时存储设计](D:/github.com/app.template/docs/design/flow-layered-backpressure.md) 的实现蓝图。

目标是将最终方案收敛为可直接编码的约束，覆盖：

- 类与职责边界
- 字段定义
- 方法签名建议
- 状态机落点
- 锁顺序
- 关键伪代码
- 指标埋点位置
- 测试矩阵

本文默认采用上一份设计文档中的最终方案，不再讨论替代路线。

## 二、最终实现总览

最终实现由五个核心部分组成：

1. `BoundedTimedFlowStorage<T>`
2. `EvictionCoordinator`
3. `DownstreamPressureEvaluator`
4. `SlotExpiryToken`
5. `FlowEntry` / `FlowSlot` 的运行时字段扩展

总体结构：

```text
FlowLauncher
  -> BackpressureController
  -> BoundedTimedFlowStorage

BoundedTimedFlowStorage
  -> slotByKey
  -> slotById
  -> DelayQueue<SlotExpiryToken>
  -> EvictionCoordinator
  -> MatchedPairProcessor
  -> FlowEgressHandler / FlowFinalizer

EvictionCoordinator
  -> DownstreamPressureEvaluator
  -> BoundedTimedFlowStorage.drainExpiredEntries(...)
```

## 三、类清单与职责

### 3.1 `BoundedTimedFlowStorage<T>`

建议路径：

- `template-flow/src/main/java/com/lrenyi/template/flow/storage/BoundedTimedFlowStorage.java`

职责：

- 作为 Flow 存储层的受控超时主实现
- 管理 `slotByKey` / `slotById`
- 管理 `usedEntryCount`
- 负责写入、配对、摘除、离库提交
- 负责更新 slot 超时元数据
- 负责向 `EvictionCoordinator` 登记 slot 过期检查
- 负责 shutdown / source finished drain

明确不负责：

- 不直接做下游压力判断
- 不在协调线程中执行业务消费逻辑

### 3.2 `EvictionCoordinator`

建议路径：

- `template-flow/src/main/java/com/lrenyi/template/flow/storage/EvictionCoordinator.java`

职责：

- 从 `DelayQueue<SlotExpiryToken>` 中取出到期 token
- 调用 `BoundedTimedFlowStorage.onExpiryToken(...)`
- 控制单线程协调循环生命周期

不负责：

- 不直接操作业务回调
- 不直接释放 permit

### 3.3 `DownstreamPressureEvaluator`

建议路径：

- `template-flow/src/main/java/com/lrenyi/template/flow/internal/DownstreamPressureEvaluator.java`

职责：

- 统一返回 soft timeout 当前是否允许 drain
- 输出原因标签，用于日志和指标

### 3.4 `SlotExpiryToken`

建议路径：

- `template-flow/src/main/java/com/lrenyi/template/flow/storage/SlotExpiryToken.java`

职责：

- 作为 `DelayQueue` 中的唯一调度对象
- 提供 `slotId + version + nextCheckAt`

### 3.5 `FlowEntry`

现有路径：

- [FlowEntry.java](D:/github.com/app.template/template-flow/src/main/java/com/lrenyi/template/flow/context/FlowEntry.java)

职责新增：

- 保存 entry 级运行时元数据

### 3.6 `FlowSlot<T>`

现有路径：

- [FlowSlot.java](D:/github.com/app.template/template-flow/src/main/java/com/lrenyi/template/flow/storage/FlowSlot.java)

职责新增：

- 保存 slot 级调度元数据
- 维护 deque

## 四、字段定义

### 4.1 `FlowEntry` 最终字段

建议新增以下字段：

```java
private long entryId;
private long storedAtEpochMs;
private long softExpireAtEpochMs;
private long hardExpireAtEpochMs;
private volatile int runtimeState;
private volatile int slotVersion;
```

状态常量建议放在 `FlowEntry` 中或单独常量类中：

```java
public static final int STATE_ACTIVE = 0;
public static final int STATE_SOFT_EXPIRED = 1;
public static final int STATE_EGRESSING = 2;
public static final int STATE_REMOVED = 3;
```

字段约束：

- `entryId` 在 entry 首次入库前赋值
- `slotVersion` 仅用于防御旧路径误处理，不作为主调度版本
- `runtimeState` 只允许单向前进，不允许回退

### 4.2 `FlowSlot<T>` 最终字段

建议新增字段：

```java
private final long slotId;
private long earliestSoftExpireAt;
private long earliestHardExpireAt;
private long nextCheckAt;
private int version;
private boolean queuedForExpiry;
private boolean draining;
private final ArrayDeque<FlowEntry<T>> entries;
```

字段约束：

- `slotId` 创建后不可变
- `version` 仅在以下场景递增：
  - slot 首次登记 token
  - 延期重新登记
  - slot 内超时边界发生变化且需重排 token
- `queuedForExpiry` 表示“当前存在逻辑有效 token”
- `draining` 表示 slot 正在进行 timeout/shutdown/source-finished drain

### 4.3 `BoundedTimedFlowStorage<T>` 字段

建议字段：

```java
private final ConcurrentMap<String, FlowSlot<T>> slotByKey;
private final ConcurrentMap<Long, FlowSlot<T>> slotById;
private final DelayQueue<SlotExpiryToken> expiryQueue;
private final LongAdder usedEntryCount;
private final LongAdder expiryReadyEntryCount;
private final LongAdder forcedExpiryCount;
private final AtomicLong slotIdGenerator;
private final AtomicLong entryIdGenerator;
private final EvictionCoordinator evictionCoordinator;
private final DownstreamPressureEvaluator downstreamPressureEvaluator;
```

说明：

- `LongAdder` 用于高频计数
- `slotById` 仅为 token 回查 slot 服务
- `slotIdGenerator` 和 `entryIdGenerator` 在单 JVM 内唯一即可

### 4.4 `SlotExpiryToken` 字段

```java
private final long slotId;
private final long nextCheckAt;
private final int version;
```

禁止增加：

- `FlowSlot` 引用
- `FlowEntry` 引用
- 业务数据引用
- `joinKey` 原始字符串

## 五、核心方法签名

### 5.1 `BoundedTimedFlowStorage<T>`

建议最少包含以下方法：

```java
public final class BoundedTimedFlowStorage<T> extends AbstractEgressFlowStorage<T> implements FlowStorage<T> {

    @Override
    public boolean doDeposit(FlowEntry<T> entry);

    @Override
    public long size();

    public long usedEntries();

    public long entryLimit();

    public void onExpiryToken(SlotExpiryToken token);

    public int drainExpiredEntries(long slotId, int expectedVersion, long nowEpochMs);

    @Override
    public int drainRemainingToFinalizer();

    @Override
    public void shutdown();
}
```

建议拆出的私有方法：

```java
private boolean handleMatchingMode(String key, FlowEntry<T> entry);
private boolean handleOverwriteMode(String key, FlowEntry<T> entry);
private FlowSlot<T> createSlot();
private void initializeEntryRuntime(FlowEntry<T> entry, long now);
private void updateSlotExpiryMetadata(FlowSlot<T> slot);
private void enqueueSlotExpiryIfNeeded(FlowSlot<T> slot);
private void requeueSlotExpiry(FlowSlot<T> slot, long nextCheckAt);
private List<FlowEntry<T>> collectHardExpired(FlowSlot<T> slot, long now);
private List<FlowEntry<T>> collectSoftExpired(FlowSlot<T> slot, long now);
private void removeEntriesForEgress(String key, FlowSlot<T> slot, List<FlowEntry<T>> expired);
private void submitExpiredEntries(String key, List<FlowEntry<T>> expired, EgressReason reason, boolean skipRetry);
private void releaseStorageAfterRemoval(int n);
private void cleanupEmptySlot(String key, FlowSlot<T> slot);
```

### 5.2 `EvictionCoordinator`

```java
public final class EvictionCoordinator implements AutoCloseable {

    public void start();

    @Override
    public void close();
}
```

建议内部方法：

```java
private void runLoop();
private void handleToken(SlotExpiryToken token);
```

### 5.3 `DownstreamPressureEvaluator`

```java
public interface DownstreamPressureEvaluator {
    ExpiryDecision evaluate(String jobId, long nowEpochMs);
}
```

返回对象：

```java
public record ExpiryDecision(
        ExpiryDecisionType type,
        String reason,
        long suggestedDelayMs
) {}
```

其中：

- `type`：`ALLOW_SOFT_DRAIN` / `DEFER` / `FORCE_HARD_DRAIN`

## 六、锁顺序与并发约束

### 6.1 唯一锁顺序

所有涉及 slot 内容变更的路径，统一遵循：

1. 先通过 key 或 slotId 找到 slot
2. 计算 stripe
3. 获取该 stripe lock
4. 在锁内再次确认 slot 仍有效
5. 修改 slot / entry / 计数

禁止：

- 先拿 stripe A 再拿 stripe B
- 同时持有多个 stripe lock
- 在持锁状态下做阻塞式消费者提交等待

### 6.2 锁内允许做的事

允许：

- 更新 deque
- 更新 slot 元字段
- 更新 entry 状态
- 更新 `usedEntryCount` 等本地计数
- 决定哪些 entry 被摘除

不允许：

- 长时间执行业务回调
- 调用可能阻塞很久的 `submitConsumer` 等待结果
- 复杂日志字符串拼接

### 6.3 锁外执行

以下动作应在锁外：

- `matchedPairProcessor.processMatchedPair`
- `handleEgress`
- `finalizer.submitDataToConsumer`
- 大部分 metrics timer record

但锁内必须先完成：

- 将 entry 从 slot 中摘掉
- 将状态设为 `EGRESSING`
- 扣减 `usedEntryCount`

## 七、状态迁移规则

### 7.1 Entry 状态迁移

只允许：

```text
ACTIVE -> SOFT_EXPIRED -> EGRESSING -> REMOVED
ACTIVE -> EGRESSING -> REMOVED
```

说明：

- `SOFT_EXPIRED` 只表示“已达到软超时”
- 延期不改变 entry 为新状态
- 真正从 slot 摘除前必须先转为 `EGRESSING`
- 离库提交完成后统一标记 `REMOVED`

### 7.2 Slot 状态规则

不单独做枚举状态机，只用两个布尔：

- `queuedForExpiry`
- `draining`

约束：

- `draining=true` 时，不允许协调器重复发起超时摘除
- `queuedForExpiry=true` 时，不允许重复登记同版本 token

## 八、关键算法伪代码

### 8.1 写入

```java
boolean doDeposit(FlowEntry<T> entry) {
    long now = clock.millis();
    initializeEntryRuntime(entry, now);
    String key = joiner().joinKey(entry.getData());
    Lock stripe = stripeFor(key);
    stripe.lock();
    List<Runnable> afterUnlock = new ArrayList<>(2);
    try {
        FlowSlot<T> slot = slotByKey.computeIfAbsent(key, k -> {
            FlowSlot<T> created = createSlot();
            slotById.put(created.getSlotId(), created);
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
    runAll(afterUnlock);
}
```

说明：

- 真正代码里 `afterUnlock` 的执行需要放到锁外
- `computeIfAbsent` 若担心在锁外先创建再加锁冲突，可改为显式双检

### 8.2 协调器主循环

```java
while (!closed) {
    SlotExpiryToken token = expiryQueue.take();
    storage.onExpiryToken(token);
}
```

### 8.3 处理 token

```java
void onExpiryToken(SlotExpiryToken token) {
    long now = clock.millis();
    drainExpiredEntries(token.slotId(), token.version(), now);
}
```

### 8.4 slot 级过期处理

```java
int drainExpiredEntries(long slotId, int expectedVersion, long now) {
    FlowSlot<T> slot = slotById.get(slotId);
    if (slot == null) {
        return 0;
    }

    String key = slot.currentKey();
    Lock stripe = stripeFor(key);
    List<ExpiredBatch<T>> batches = new ArrayList<>(2);
    stripe.lock();
    try {
        if (slotById.get(slotId) != slot) {
            return 0;
        }
        if (slot.getVersion() != expectedVersion) {
            return 0;
        }
        if (slot.isDraining()) {
            return 0;
        }

        List<FlowEntry<T>> hardExpired = collectHardExpired(slot, now);
        if (!hardExpired.isEmpty()) {
            slot.setDraining(true);
            removeEntriesForEgress(key, slot, hardExpired);
            batches.add(new ExpiredBatch<>(hardExpired, EgressReason.TIMEOUT, true));
        } else {
            List<FlowEntry<T>> softExpired = collectSoftExpired(slot, now);
            if (!softExpired.isEmpty()) {
                ExpiryDecision decision = downstreamPressureEvaluator.evaluate(jobId(), now);
                if (decision.type() == ALLOW_SOFT_DRAIN) {
                    slot.setDraining(true);
                    removeEntriesForEgress(key, slot, softExpired);
                    batches.add(new ExpiredBatch<>(softExpired, EgressReason.TIMEOUT, false));
                } else {
                    requeueSlotExpiry(slot, computeNextCheckAt(slot, decision, now));
                }
            } else {
                requeueBasedOnEarliestFutureExpiry(slot);
            }
        }

        cleanupEmptySlot(key, slot);
    } finally {
        stripe.unlock();
    }

    for (ExpiredBatch<T> batch : batches) {
        submitExpiredEntries(key, batch.entries(), batch.reason(), batch.skipRetry());
    }
    return batches.stream().mapToInt(b -> b.entries().size()).sum();
}
```

### 8.5 软超时收集

```java
List<FlowEntry<T>> collectSoftExpired(FlowSlot<T> slot, long now) {
    List<FlowEntry<T>> expired = new ArrayList<>();
    for (FlowEntry<T> entry : slot.entries()) {
        if (entry.getRuntimeState() == STATE_ACTIVE && entry.getSoftExpireAtEpochMs() <= now) {
            entry.setRuntimeState(STATE_SOFT_EXPIRED);
            expiryReadyEntryCount.increment();
            expired.add(entry);
        }
    }
    return expired;
}
```

### 8.6 摘除并提交离库

```java
void removeEntriesForEgress(String key, FlowSlot<T> slot, List<FlowEntry<T>> expired) {
    for (FlowEntry<T> entry : expired) {
        if (entry.getRuntimeState() != STATE_EGRESSING) {
            entry.setRuntimeState(STATE_EGRESSING);
        }
        slot.remove(entry);
        usedEntryCount.decrement();
        expiryReadyEntryCount.decrementIfPositive();
    }
    updateSlotExpiryMetadata(slot);
    if (!slot.isEmpty()) {
        enqueueSlotExpiryIfNeeded(slot);
    }
}
```

注：

- `decrementIfPositive()` 不是现成 API，实际实现时需用 `LongAdder` 配合局部计数或改用 `AtomicLong`
- 如果需要严格可逆计数，`expiryReadyEntryCount` 更适合 `AtomicLong`

## 九、最终推荐的计数器选型

建议：

- `usedEntryCount`：`LongAdder`
- `forcedExpiryCount`：`LongAdder`
- `expiryReadyEntryCount`：`AtomicLong`

原因：

- `usedEntryCount` 高频增减，适合 `LongAdder`
- `expiryReadyEntryCount` 需要精确增减，且容易在同一 entry 上重复触发，适合 `AtomicLong`

## 十、与现有类的具体改造点

### 10.1 引入 `BoundedTimedFlowStorage`

最终目标：

- 以 `BoundedTimedFlowStorage` 作为受控超时存储的默认实现
- 旧版基于 TTL/maximumSize 自动驱逐的缓存实现不再作为主路径使用

### 10.2 `FlowStorageFactory`

需要支持创建：

- `BoundedTimedFlowStorage`

### 10.3 `FlowResourceRegistry`

需要新增：

- `ScheduledExecutorService evictionCoordinatorExecutor`

或：

- 由 `EvictionCoordinator` 内部持有 dedicated thread

推荐 dedicated single thread，更容易隔离调度责任。

### 10.4 `BackpressureController`

建议新增方法：

```java
public BackpressureSnapshot snapshot();
public boolean isDownstreamOverloaded();
```

快照字段：

- `storageUsed`
- `storageLimit`
- `jobConsumerAvailablePermits`
- `globalConsumerAvailablePermits`
- `perJobPendingCount`
- `globalPendingCount`

### 10.5 `FlowFinalizer`

建议新增指标：

- pending slot acquire timeout 累计数
- submit consumer failure 累计数
- 当前待消费 backlog gauge

## 十一、指标埋点位置

### 11.1 写入成功

位置：

- `doDeposit` 成功后

埋点：

- `entries.used`
- `slot.count`

### 11.2 soft timeout 命中

位置：

- `collectSoftExpired`

埋点：

- `storage.expiry.ready`
- `storage.expiry.ready.total`

### 11.3 延期

位置：

- `requeueSlotExpiry`

埋点：

- `storage.expiry.deferred`
- `downstream.overloaded.total{reason=...}`

### 11.4 hard timeout 强制离库

位置：

- `collectHardExpired` + 提交离库

埋点：

- `storage.expiry.force.total`

### 11.5 真正离库

位置：

- 摘除 entry 并释放 permit 后

埋点：

- `entries.used`
- `expiry.delay.duration`

## 十二、日志建议

### 12.1 必须记录的 INFO/WARN

- hard timeout 发生
- 某 slot 延期次数持续增长
- pending slot acquire timeout
- eviction coordinator 异常退出

### 12.2 只放 DEBUG 的内容

- 普通 token 出队
- 普通延期重排
- 普通 slot metadata 更新

防止高压场景日志放大。

## 十三、测试矩阵

### 13.1 单元测试

- `FlowEntry` 状态迁移只能单向
- `FlowSlot` 更新最早超时时间正确
- token version 失效逻辑正确
- soft timeout 下游过载时不会摘除 entry
- hard timeout 一定摘除
- 空 slot 会从双索引表删除

### 13.2 并发测试

- 生产写入与协调器 drain 同时命中同一 key
- 配对成功与 timeout token 同时命中同一 slot
- shutdown 与 soft timeout drain 并发

验证：

- 不重复离库
- 不重复释放 permit
- `usedEntryCount` 最终一致

### 13.3 集成测试

- 下游卡慢导致缓存逐步堆满，生产阻塞
- 下游恢复后生产恢复
- 多值模式下 slot 级 token 正常工作
- source finished 后 drainRemainingToFinalizer 正常排空

### 13.4 压测

- 10 万级 entry 下对象数量稳定
- GC 不因 token/包装对象膨胀失控
- 单线程协调器不会成为明显瓶颈

## 十四、编码顺序建议

按实现依赖顺序，建议这样落代码：

1. 扩展 `FlowEntry`
2. 扩展 `FlowSlot`
3. 新增 `SlotExpiryToken`
4. 新增 `DownstreamPressureEvaluator`
5. 新增 `EvictionCoordinator`
6. 实现 `BoundedTimedFlowStorage`
7. 修改工厂接线
8. 修改指标与配置
9. 补齐测试

## 十五、文档索引

- [Flow 分层背压与受控超时存储设计](D:/github.com/app.template/docs/design/flow-layered-backpressure.md)
- [Flow 配置优化与全局化设计](D:/github.com/app.template/docs/design/flow-limits-globalization.md)
- [Flow 消费出口统一设计](D:/github.com/app.template/docs/design/flow-egress-unification.md)
