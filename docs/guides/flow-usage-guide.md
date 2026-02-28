# Flow 流聚合使用指导

## 1. 概述

Flow 是 template-core 中的**流聚合引擎**：多路数据按 `joinKey` 汇聚，支持「配对成功」（双流对齐）与「单条覆盖 / 队列消费」等语义。

- **核心抽象**：`FlowJoiner<T>` 定义如何取 key、使用哪种存储、配对/消费/失败时如何回调；引擎负责拉取或接收数据、背压与任务生命周期。
- **两种模式**：
    - **拉取（Pull）**：数据来自业务自己拉取的流（如 DB 游标、Kafka consumer、分页 API），由引擎驱动 `FlowSource` 拉取并注入。
    - **推送（Push）**：数据由外部调用方持续 `push`（如 HTTP 请求体、消息队列 consumer 主动投递），业务持有 `FlowInlet` 调用
      `push(item)` 与 `markSourceFinished()`。

---

## 2. 核心概念

### FlowJoiner&lt;T&gt;

业务实现接口，定义数据如何聚合与回调。

- **必实现**：`getDataType()`、`joinKey(T)`、`sourceProvider()`（推送模式可用 `FlowSourceAdapters.emptyProvider()`）、
  `onSuccess(T, T, String)`、`onFailed(T, String)`；建议同时实现 `onFailed(T, String, FailureReason)` 以便按原因统计。
- **可选**：`needMatched()` 返回 `true` 表示双流配对；`isMatched(existing, incoming)` 做配对校验；`onConsume(T, String)`
  在单条覆盖或队列模式下被调用。

### 存储类型（FlowStorageType）

- **CAFFEINE**：按 key 的本地缓存，支持配对（双流相遇触发 `onSuccess`）或覆盖（同 key 后到顶替先到，先到者走
  `onFailed(..., REPLACE)`）。
- **QUEUE**：FIFO 队列，无 key 配对，每条数据最终走 `onConsume`。

### FlowJoinerEngine

入口类，持有 `FlowManager`；提供 `run(...)`（拉取）与 `startPush(...)`（推送）。

### FlowManager

单例，通过 `FlowManager.getInstance(JobGlobal)` 获取；负责 Job 注册、Launcher、停止；测试中可用 `FlowManager.reset()` 清理。

### FlowInlet&lt;T&gt;

推送模式入口：`push(item)`、`markSourceFinished()`、`getProgressTracker()`、`getCompletionFuture()`、`stop(boolean)`。

---

## 3. 配置

### JobGlobal（全局）

- `globalSemaphoreMaxLimit`：全局并发上限（默认 8000）。
- `progressDisplaySecond`：进度打印间隔（秒）；设为 `0` 关闭进度打印。

### JobConfig（单任务）

- `jobProducerLimit`：该任务允许的生产者并发数（默认 40）。
- `ttlMill`：缓存条目 TTL（毫秒），配对场景下超时未匹配会走 `onFailed(..., TIMEOUT)`（默认 10000）。
- `maxCacheSize`：缓存/队列容量，满时触发驱逐或背压，可能产生 `EVICTION` / `REJECT`（默认 80000）。
- `cacheEnabled`：是否启用缓存（默认 true）。

---

## 4. 拉取模式（Pull）

适用于数据由业务侧拉取的场景。

### 单流

```java
TemplateConfigProperties.JobGlobal global = new TemplateConfigProperties.JobGlobal();
global.setProgressDisplaySecond(0);
TemplateConfigProperties.JobConfig jobConfig = new TemplateConfigProperties.JobConfig();

FlowManager manager = FlowManager.getInstance(global);
FlowJoinerEngine engine = new FlowJoinerEngine(manager);

FlowJoiner<MyItem> joiner = new MyOverwriteJoiner(); // 实现 getDataType/joinKey/sourceProvider/onSuccess/onFailed 等
FlowSource<MyItem> singleSource = FlowSourceAdapters.fromIterator(myList.iterator(), null);

engine.run("job-1", joiner, singleSource, myList.size(), jobConfig);
ProgressTracker tracker = engine.getProgressTracker("job-1");
tracker.getCompletionFuture().get(30, TimeUnit.SECONDS);
```

- 若总量未知，`total` 可传 `-1`。
- `run()` 在提交完子流后即返回，实际消费在异步执行；需「全部处理完」时务必用 `getCompletionFuture().get(timeout, unit)`
  等待，必要时再短轮询业务回调计数（与集成测试中的做法一致）。

### 多流

由 `FlowJoiner.sourceProvider()` 返回多个子流，并传入自建的 `ProgressTracker` 与 `setTotalExpected`：

```java
DefaultProgressTracker tracker = new DefaultProgressTracker("job-2", manager);
tracker.setTotalExpected("job-2", totalItemCount);
joiner.setSourceProvider(FlowSourceAdapters.fromFlowSources(List.of(sourceA, sourceB)));
engine.run("job-2", joiner, tracker, jobConfig);
tracker.getCompletionFuture().get(30, TimeUnit.SECONDS);
```

---

## 5. 推送模式（Push）

适用于数据由调用方持续推送的场景。

```java
FlowInlet<MyItem> inlet = engine.startPush("job-3", joiner, jobConfig);
for (MyItem item : items) {
    inlet.push(item);
}
inlet.markSourceFinished();
inlet.getCompletionFuture().get(30, TimeUnit.SECONDS);
```

- 未调用 `markSourceFinished()` 就调用 `inlet.stop(true)` 时，`getCompletionFuture()` 可能不会完成；若需提前结束，可
  `stop(true)` 后按业务决定是否短时等待或忽略 Future。

---

## 6. 进度与完成

- **ProgressTracker**：`getSnapshot()` 得到 `FlowProgressSnapshot`；`getCompletionFuture()` 在「source 已结束且缓存排空、活跃消费归零」时完成。
- **FlowProgressSnapshot** 常用字段与方法：
    - `terminated`：已物理终结的条数。
    - `activeEgress` / `passiveEgress`：主动出口（业务达成）与被动出口（超时/驱逐/替换等）。
    - `passiveEgressByReason`：按 `FailureReason` 统计的被动出口数。
    - `getCompletionRate()`：完成率（如 terminated/totalExpected）。
    - `getSuccessRate()`：成功率（activeEgress / (activeEgress + passiveEgress)）。
    - `getStuckCount()`：已出缓存但尚未终结的数量，可反映回调或线程池积压。

可用于监控或测试断言（如完成率、成功率、按原因统计的损耗）。

---

## 7. 失败原因与 onFailed

枚举 `FailureReason`：TIMEOUT、EVICTION、REPLACE、MISMATCH、REJECT、SHUTDOWN、UNKNOWN。

| 原因       | 含义                                                |
|----------|---------------------------------------------------|
| TIMEOUT  | 配对场景下在缓存中等待超时（TTL 到期）                             |
| EVICTION | 容量满导致条目被驱逐                                        |
| REPLACE  | 非配对模式下同 key 新条顶替旧条，旧条走失败出口                        |
| MISMATCH | `isMatched(existing, incoming)` 返回 false，两条均走失败出口 |
| REJECT   | 背压/过载拒绝准入                                         |
| SHUTDOWN | 任务/系统关闭时存储内残留未处理数据                                |
| UNKNOWN  | 未分类                                               |

实现 `onFailed(T item, String jobId, FailureReason reason)` 可做统计或日志；
`FlowProgressSnapshot.getPassiveEgressByReason(reason.name())` 与 `FlowMetrics.getMetrics()` 中的 `failureReasons` 一致。

---

## 8. 监控与健康检查

### 指标

- `FlowManager.getMetrics()` 或 `FlowMetrics.getMetrics()` 返回结构包含：
    - `counters`：各类计数（如 job_started、job_completed）。
    - `latencies`：按操作名的延迟统计（count/min/max/avg/median/p95/p99）。
    - `resources`：资源使用（如 active_launchers、semaphore_available）。
    - `errors`：按 errorType:jobId 的错误计数。
    - `failureReasons`：按 `FailureReason` 的被动出口计数。

### 健康

- `FlowManager.checkHealth()` 返回 `HealthStatus`：HEALTHY / DEGRADED / UNHEALTHY。
- `FlowManager.getHealthStatus()` 返回详情（含 `overallStatus`、`indicators`）。
- 多指示器时取最差状态；可与 Spring Boot Actuator 集成（暴露自定义 HealthIndicator 或读取上述接口）。

---

## 9. 最小示例

### 单流拉取（最简 Joiner + 列表）

```java
// 1. 配置与引擎
TemplateConfigProperties.JobGlobal global = new TemplateConfigProperties.JobGlobal();
global.setProgressDisplaySecond(0);
TemplateConfigProperties.JobConfig jobConfig = new TemplateConfigProperties.JobConfig();

FlowManager manager = FlowManager.getInstance(global);
FlowJoinerEngine engine = new FlowJoinerEngine(manager);

// 2. Joiner：Caffeine + 单条消费（覆盖语义），推送模式可不用 source，此处用 emptyProvider）
FlowJoiner<MyItem> joiner = new FlowJoiner<MyItem>() {
    @Override
    public FlowStorageType getStorageType() { return FlowStorageType.CAFFEINE; }
    @Override
    public Class<MyItem> getDataType() { return MyItem.class; }
    @Override
    public FlowSourceProvider<MyItem> sourceProvider() { return FlowSourceAdapters.emptyProvider(); }
    @Override
    public String joinKey(MyItem item) { return item.getId(); }
    @Override
    public void onSuccess(MyItem existing, MyItem incoming, String jobId) {}
    @Override
    public void onConsume(MyItem item, String jobId) { /* 处理单条 */ }
    @Override
    public void onFailed(MyItem item, String jobId) {}
};

// 3. 单流拉取
FlowSource<MyItem> source = FlowSourceAdapters.fromIterator(list.iterator(), null);
engine.run("job-single", joiner, source, list.size(), jobConfig);
engine.getProgressTracker("job-single").getCompletionFuture().get(30, TimeUnit.SECONDS);
```

### 推送

```java
FlowInlet<MyItem> inlet = engine.startPush("job-push", joiner, jobConfig);
for (MyItem item : list) inlet.push(item);
inlet.markSourceFinished();
inlet.getCompletionFuture().get(30, TimeUnit.SECONDS);
```

完整可运行示例见 [FlowJoinerEngineIntegrationTest](template-core/src/test/java/com/lrenyi/template/core/flow/it/FlowJoinerEngineIntegrationTest.java)。

---

## 10. 测试与重置

- 单测/集成测中，每个用例后建议调用：`FlowManager.reset()`、`FlowResourceRegistry.reset()`；如需清空健康指示器可调用
  `FlowHealth.clearIndicators()`，避免跨用例残留。
- 关闭进度打印：`JobGlobal.progressDisplaySecond = 0`。

---

## 11. 参考与延伸

- **主要包与类**：
    - `com.lrenyi.template.core.flow`：FlowJoiner、FlowJoinerEngine、FlowInlet、ProgressTracker、FailureReason
    - `com.lrenyi.template.core.flow.config`：FlowStorageType
    - `com.lrenyi.template.core.flow.context`：FlowProgressSnapshot
    - `com.lrenyi.template.core.flow.manager`：FlowManager
    - `com.lrenyi.template.core.flow.source`：FlowSource、FlowSourceProvider、FlowSourceAdapters
    - `com.lrenyi.template.core.flow.health`：FlowHealth、HealthStatus
    - `com.lrenyi.template.core.flow.metrics`：FlowMetrics
- **集成测试**：`template-core/src/test/java/com/lrenyi/template/core/flow/it/FlowJoinerEngineIntegrationTest.java`
  覆盖拉取/推送、存储类型、失败原因、进度与指标、生命周期等场景。
- **测试用 Joiner 与数据模型**：`PairItem`、`PairingJoiner`、`OverwriteJoiner`、`QueueJoiner`、`MismatchPairingJoiner` 位于
  `template-core/src/test/.../flow/`，可作实现参考。
