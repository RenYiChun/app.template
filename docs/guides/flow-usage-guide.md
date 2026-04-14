# Flow 使用指导

本文档解释当前仓库里已经存在、且对外可用的 Flow 能力。只写真实公开 API，不写源码中不存在的抽象。

## 1. Flow 是什么

Flow 是 `template-flow` 模块里的流聚合引擎，用来处理“多路数据进入系统后，按业务 key 聚合并消费”的问题。

它的核心不是 CRUD，也不是消息中间件替代品，而是：

- 把多条输入流统一纳入一个 Job
- 对输入过程做背压控制
- 在存储层中完成等待、覆盖、配对或排队
- 在消费回调中交给业务处理
- 提供完成判定、指标和健康检查

## 2. 公开 API 里最重要的对象

### `FlowJoiner<T>`

`template-flow/src/main/java/com/lrenyi/template/flow/api/FlowJoiner.java`

业务实现接口。你至少需要定义：

- `getDataType()`
- `sourceProvider()`
- `joinKey(T item)`
- `onPairConsumed(...)`
- `onSingleConsumed(...)`

可选项：

- `getStorageType()`：默认 `LOCAL_BOUNDED`
- `needMatched()`：返回 `true` 时按配对语义运行
- `isMatched(existing, incoming)`：配对场景下做业务校验
- `isRetryable(item, jobId)`：是否允许重试

当前公开接口里没有 `PairingStrategy`、`PairingContext`、`getPairingStrategy()` 这些扩展点，文档和接入代码不要按这些名字编写。

### `FlowJoinerEngine`

`template-flow/src/main/java/com/lrenyi/template/flow/engine/FlowJoinerEngine.java`

Flow Job 的启动入口，主要有两类方法：

- `run(...)`：拉取模式
- `startPush(...)`：推送模式

### `FlowManager`

`template-flow/src/main/java/com/lrenyi/template/flow/manager/FlowManager.java`

负责：

- 创建和登记 Job
- 获取 `ProgressTracker`
- 停止 Job
- 提供健康状态

### `FlowInlet<T>`

`template-flow/src/main/java/com/lrenyi/template/flow/api/FlowInlet.java`

推送模式下的入口，只提供这些公开能力：

- `push(item)`
- `markSourceFinished()`
- `getProgressTracker()`
- `isCompleted()`
- `stop(force)`

当前没有 `getCompletionFuture()`。

### `ProgressTracker`

`template-flow/src/main/java/com/lrenyi/template/flow/api/ProgressTracker.java`

用于观测任务进度和完成态：

- `getSnapshot()`
- `isCompleted(boolean showStatus)`
- `isCompletionConditionMet()`
- `markSourceFinished(...)`
- `setTotalExpected(...)`

## 3. 两种运行模式

### 拉取模式

适用于数据已经抽象成 `FlowSource<T>` 或 `FlowSourceProvider<T>` 的场景。

典型来源：

- Kafka consumer
- NATS subscription
- 分页 API
- 内存列表、游标、迭代器

### 推送模式

适用于数据由业务方自己消费出来，再手工交给 Flow 的场景。

典型来源：

- HTTP 请求
- MQ consumer 业务代码
- 定时任务

## 4. 两种存储类型

### `FlowStorageType.LOCAL_BOUNDED`

- 默认值
- 按 `joinKey` 存储
- 可用于覆盖消费或配对消费

常见行为：

- 同 key 后来的数据覆盖先来的数据，旧数据以 `REPLACE` 出场
- 配对成功时触发 `onPairConsumed(...)`
- TTL 到期或驱逐时触发 `onSingleConsumed(..., reason)`

### `FlowStorageType.QUEUE`

- FIFO 队列
- 不按 key 配对
- 数据按队列语义逐条消费

## 5. 最小 Joiner 写法

```java
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowSourceAdapters;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.model.FlowStorageType;

record DemoItem(String id, String value) {}

class DemoJoiner implements FlowJoiner<DemoItem> {
    @Override
    public FlowStorageType getStorageType() {
        return FlowStorageType.LOCAL_BOUNDED;
    }

    @Override
    public Class<DemoItem> getDataType() {
        return DemoItem.class;
    }

    @Override
    public FlowSourceProvider<DemoItem> sourceProvider() {
        return FlowSourceAdapters.emptyProvider();
    }

    @Override
    public String joinKey(DemoItem item) {
        return item.id();
    }

    @Override
    public void onPairConsumed(DemoItem existing, DemoItem incoming, String jobId) {
        // 配对成功时的业务处理
    }

    @Override
    public void onSingleConsumed(DemoItem item, String jobId, EgressReason reason) {
        // 单条出场时的业务处理
    }
}
```

## 6. 拉取模式用法

### 单流

```java
import java.util.List;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowSource;
import com.lrenyi.template.flow.api.FlowSourceAdapters;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.engine.FlowJoinerEngine;
import com.lrenyi.template.flow.manager.FlowManager;

TemplateConfigProperties.Flow flowConfig = new TemplateConfigProperties.Flow();
FlowManager manager = FlowManager.getInstance(flowConfig);
FlowJoinerEngine engine = new FlowJoinerEngine(manager);

List<DemoItem> list = List.of(
        new DemoItem("k1", "v1"),
        new DemoItem("k2", "v2")
);

FlowSource<DemoItem> source = FlowSourceAdapters.fromIterator(list.iterator(), null);
engine.run("pull-single", new DemoJoiner(), source, list.size(), flowConfig);

ProgressTracker tracker = engine.getProgressTracker("pull-single");
while (!tracker.isCompleted(true)) {
    Thread.sleep(50);
}
System.out.println(tracker.getSnapshot());
```

### 多流

```java
import java.util.List;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowSource;
import com.lrenyi.template.flow.api.FlowSourceAdapters;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.internal.DefaultProgressTracker;
import com.lrenyi.template.flow.engine.FlowJoinerEngine;
import com.lrenyi.template.flow.manager.FlowManager;

TemplateConfigProperties.Flow flowConfig = new TemplateConfigProperties.Flow();
FlowManager manager = FlowManager.getInstance(flowConfig);
FlowJoinerEngine engine = new FlowJoinerEngine(manager);

FlowSource<DemoItem> sourceA = FlowSourceAdapters.fromIterator(List.of(
        new DemoItem("k1", "A-1"),
        new DemoItem("k2", "A-2")
).iterator(), null);
FlowSource<DemoItem> sourceB = FlowSourceAdapters.fromIterator(List.of(
        new DemoItem("k1", "B-1"),
        new DemoItem("k2", "B-2")
).iterator(), null);

DemoJoiner joiner = new DemoJoiner() {
    @Override
    public boolean needMatched() {
        return true;
    }
};

DefaultProgressTracker tracker = new DefaultProgressTracker("pull-multi", manager);
tracker.setTotalExpected("pull-multi", 4);

joiner = new DemoJoiner() {
    @Override
    public FlowSourceProvider<DemoItem> sourceProvider() {
        return FlowSourceAdapters.fromFlowSources(List.of(sourceA, sourceB));
    }

    @Override
    public boolean needMatched() {
        return true;
    }
};

engine.run("pull-multi", joiner, tracker, flowConfig);
while (!tracker.isCompleted(true)) {
    Thread.sleep(50);
}
```

## 7. 推送模式用法

```java
import java.util.List;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowInlet;
import com.lrenyi.template.flow.engine.FlowJoinerEngine;
import com.lrenyi.template.flow.manager.FlowManager;

TemplateConfigProperties.Flow flowConfig = new TemplateConfigProperties.Flow();
FlowManager manager = FlowManager.getInstance(flowConfig);
FlowJoinerEngine engine = new FlowJoinerEngine(manager);

FlowInlet<DemoItem> inlet = engine.startPush("push-demo", new DemoJoiner(), flowConfig);
for (DemoItem item : List.of(new DemoItem("k1", "v1"), new DemoItem("k2", "v2"))) {
    inlet.push(item);
}
inlet.markSourceFinished();

while (!inlet.isCompleted()) {
    Thread.sleep(50);
}
System.out.println(inlet.getProgressTracker().getSnapshot());
```

## 8. 进度与完成态怎么理解

`FlowProgressSnapshot` 位于 `template-flow/src/main/java/com/lrenyi/template/flow/context/FlowProgressSnapshot.java`。

最常看的字段：

- `productionAcquired`：已进入系统的数据数
- `productionReleased`：已成功入存储的数据数
- `activeConsumers`：当前仍在消费中的数量
- `inStorage`：当前仍在存储中等待的数据数
- `terminated`：已经彻底离场的数据数

最常看的方法：

- `getCompletionRate()`
- `getInProductionCount()`
- `getPendingConsumerCount()`
- `getTps()`

当前公开 API 的完成判定方式是：

- 拉取模式：`tracker.isCompleted(true)`
- 推送模式：`inlet.isCompleted()`

不是 `getCompletionFuture()`。

### Pipeline（`FlowPipeline`）与分阶段 `storage-capacity`

多阶段管道（`FlowPipeline.builder(...)`）中，**默认**各阶段共用运行时传入的 `TemplateConfigProperties.Flow` 里的 `limits.per-job.storage-capacity`。

若某一阶段需要**单独**的存储条数上限，可在构建阶段指定（均为可选，与 YAML 基底合并为**独立快照**，不会改写你传入的基底配置对象）：

- `NextStageSpec.builder(...).storageCapacity(int).build()`：为本段覆盖值（须 `> 0`）。
- `NextMapSpec.builder(...).storageCapacity(int).build()`：为本段覆盖值。
- `aggregate(batchSize, timeout, unit, Integer storageCapacity)`：最后一参非 null 时覆盖该 aggregate 段。
- `sink(sinkClass, onSink, Integer storageCapacity)`：最后一参非 null 时覆盖 Sink 段。

未指定覆盖时，行为与仅配置 YAML/基底 `flow` 时一致。

## 9. `EgressReason` 怎么用

`EgressReason` 定义位于 `template-flow/src/main/java/com/lrenyi/template/flow/model/EgressReason.java`。

常见值：

- `PAIR_MATCHED`
- `SINGLE_CONSUMED`
- `TIMEOUT`
- `EVICTION`
- `REPLACE`
- `MISMATCH`
- `REJECT`
- `BACKPRESSURE_TIMEOUT`
- `SHUTDOWN`
- `CLEARED_AFTER_PAIR_SUCCESS`

业务上常见的处理方式是：

- `PAIR_MATCHED`：统计配对成功
- `SINGLE_CONSUMED`：统计单条正常消费
- `TIMEOUT` / `EVICTION` / `REPLACE`：记录损耗或报警
- `SHUTDOWN`：记录任务被中断时的残留数据

## 10. 健康检查与指标

### 健康

- `FlowManager.checkHealth()`：返回 `HealthStatus`
- `FlowManager.getHealthStatus()`：返回健康详情

引入 Actuator 后，Flow 会桥接到健康检查。

### 指标

Flow 指标名定义在 `template-flow/src/main/java/com/lrenyi/template/flow/metrics/FlowMetricNames.java`。

当前公开文档里应以这些能力为准：

- 生产累计：`app.template.flow.production_acquired`
- 终结累计：`app.template.flow.terminated`
- 错误计数：`app.template.flow.errors`
- 阶段耗时：`app.template.flow.deposit.duration`
- 阶段耗时：`app.template.flow.match.duration`
- 阶段耗时：`app.template.flow.finalize.duration`

资源 Gauge 由 `template-flow/src/main/java/com/lrenyi/template/flow/metrics/FlowResourceMetrics.java` 注册。

## 11. 第一次接入时最容易踩的坑

### 用了旧配置键

当前真实键是：

- `consumer-threads`
- `storage-capacity`
- `in-flight-consumer`
- `keyed-cache.cache-ttl-mill`

不是：

- `consumer-concurrency`
- `storage`
- `pending-consumer`

### 推送模式忘记调用 `markSourceFinished()`

不声明输入结束，Job 往往不会进入完成态。

### 文档照抄了不存在的 API

当前没有：

- `FlowInlet.getCompletionFuture()`
- `ProgressTracker.getCompletionFuture()`
- `FlowManager.getMetrics()`
- `FlowMetrics.getMetrics()`
- `FlowJoiner.getPairingStrategy()`

## 12. 参考

- [Flow 快速开始](../getting-started/quick-start.md)
- [Flow 配置参考](../getting-started/config-reference.md)
- [Flow 同 Key 多 Value 使用指南](flow-multi-value-guide.md)
- [Flow 完成态收敛设计（归档）](../design/archive/flow-completion-isCompleted.md)
