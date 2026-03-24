# Flow Pipeline 使用指南

## 概述

`FlowPipeline` 是 `template-flow` 引擎的高级抽象，旨在支持复杂的、多阶段的数据处理工作流。它允许开发者通过声明式 API 将多个 `FlowJoiner` 串联、并联（扇出）以及实施聚合（攒批）操作。

## 核心概念

### 1. 阶段 (Stage)
管道由一系列阶段组成。每个阶段通常包含一个 `FlowJoiner`（业务逻辑）和一个列表转换器；声明式 API 使用 **`NextStageSpec`** 封装下游类型、Joiner、`Function<T, List<R>>` 及可选的配对产出，以便后续扩展字段而不改 `nextStage` 方法签名。

### 2. 扇出 (Fork)
支持将上游阶段产出的数据广播到多个并行的子管道中。实现上，fork 节点会对 **每个分支子管道的首段 Inlet** 各 `push` 一次；若构建时 fork 之后仍存在主链下游，还会向 **主链** 再 `push` 一次（当前 `fork(...)` 在子链构建完成后即 `build()`，通常 **无** 主链延续）。`markSourceFinished` 同样按分支（及可选主链）广播。

### 3. 聚合 (Aggregate)
支持按数量或时间窗口将流式数据聚合为 `List<T>`，并传递给后续阶段。框架内 `AggregationJoiner` 为每条入站数据分配 **唯一 `joinKey`**，避免在存储层与固定 key 冲突导致无法逐条进入攒批缓冲。

**内嵌攒批（推荐在「单段产出 + 攒批」紧耦合时）**：攒批参数使用 **`EmbeddedBatchSpec`**（`batchSize` / `timeout` / `unit`）。
- **`nextMap(NextMapSpec, EmbeddedBatchSpec)`**：攒批挂在 **本段线性映射** 出口；
- **`nextStage(NextStageSpec, EmbeddedBatchSpec)`**：攒批挂在 **本段常规 Joiner** 出口（与 `nextMap` 内嵌攒批同一机制，均不单独增加 `aggregate` 段 Launcher）。

语义与「`nextMap` + `aggregate`」或「`nextStage` + `aggregate`」等价，但少一段异步边界。仍保留 **`aggregate(...)`** 用于仅需攒批、或需显式独立阶段的场景。

### 4. 线性映射 (Map)

纯映射（类型变换或过滤）可使用 **`nextMap`**，内部使用 **`MapOperatorJoiner`**（每条 **`joinKey`** 唯一），无需手写「假 Joiner + 常数 key」：

```java
import java.util.concurrent.TimeUnit;

import com.lrenyi.template.flow.api.NextMapSpec;

@SuppressWarnings("unchecked")
FlowPipeline<Integer> pipeline = (FlowPipeline<Integer>) FlowPipeline.builder("map-job", String.class, flowManager)
    .nextMap(NextMapSpec.of(String.class, Integer.class, Integer::parseInt, 100, TimeUnit.MILLISECONDS))
    .sink(Integer.class, (n, jobId) -> save(n));
```

（`NextMapSpec` 描述本段驻留类型、下游类型、映射与消费节拍；`QUEUE` 上为出队轮询间隔，`LOCAL_BOUNDED` 上为驱逐协调扫描间隔。后续若增加可选参数，可只扩展 `NextMapSpec`，而无需改动 `nextMap` 方法签名。）

`cacheProducer` 返回 **`null`** 时该条被过滤（不下发）。

内嵌攒批示例（下游为 `List<Integer>` 批次）：

```java
import com.lrenyi.template.flow.api.EmbeddedBatchSpec;

.nextMap(NextMapSpec.of(Integer.class, Integer.class, i -> i, 100, TimeUnit.MILLISECONDS),
        EmbeddedBatchSpec.of(10, 5, TimeUnit.SECONDS))
.sink((List<Integer> batch, String jobId) -> { /* ... */ });
```

常规阶段同样支持（在 **`nextStage` 上**增加攒批参数，而非写在 `NextStageSpec` 里）：

```java
.nextStage(NextStageSpec.of(String.class, myJoiner, s -> List.of(transform(s))),
        EmbeddedBatchSpec.of(10, 5, TimeUnit.SECONDS))
```

（签名：`nextStage(NextStageSpec<T,R> spec, EmbeddedBatchSpec batchSpec)`，返回 `Builder<List<R>>`。）

### 5. 配对后的下游产出（PipelineStageOutput / Builder）

默认兼容行为：若 Joiner **未**实现 `PipelineStageOutput` 且 **未**在 Builder 中传入 `pairOutput`，则在 `onPairConsumed` 之后仍对 `existing`、`incoming` **各**走一遍 `transformer`（旧语义）。

更推荐的方式：

- 让业务 `FlowJoiner` 实现 `com.lrenyi.template.flow.pipeline.PipelineStageOutput`，在 `outputsAfterPair` / `outputsAfterSingle` 中返回下发给下游的列表；或  
- 使用 `NextStageSpec.of(outputClass, joiner, transformer, pairOutput)` 的 **第四个参数** `BiFunction<T,T,List<R>> pairOutput`，在一次配对后显式产出列表。

实现 `PipelineEmitter` 的 Joiner（如 `AggregationJoiner`）仍通过 `setDownstream` 自行下发，不经过上述 `transformer` 自动转发路径。

### 6. 终端 Sink

内置 `SinkJoiner` 对每条到达数据使用 **单调递增的唯一 `joinKey`**，避免多条数据因同一固定 key 在存储层被合并/配对而导致 `onSink` 调用次数少于实际到达条数。

**与数据库、连接池、全局限流**：`SinkJoiner` 按管道阶段各自实例化，**不要**指望通过「多条流水线共用一个 `SinkJoiner`」来做资源控制。真正访问数据库或外部系统的是 **`onSink` 回调**——应注入或引用**应用级单例**（如 `DataSource`、Repository、封装好的写入服务）。连接池（例如 HikariCP）在应用内单例，天然在多管道、多终端之间共享连接与最大连接数等配置。若还需要跨所有 sink 的写入并发或速率上限，在**共享服务**内使用 `Semaphore`、限流器或有界队列即可，与 `SinkJoiner` 是否每阶段新建无关。

## 快速开始

### 1. 基础管道 (Simple Pipeline)

```java
FlowPipeline<Integer> pipeline = FlowPipeline.builder("simple-job", Integer.class, flowManager)
    .nextStage(new MyIntegerJoiner()) // 使用默认转换 (identity)
    .sink((data, jobId) -> log.info("Sink: {}", data));
```

### 2. 扇出 (Forking/Broadcasting)

```java
import com.lrenyi.template.flow.api.NextStageSpec;

FlowPipeline<Integer> pipeline = FlowPipeline.builder("fork-job", Integer.class, flowManager)
    .nextStage(NextStageSpec.of(Integer.class, new FilterJoiner(), i -> i > 0 ? List.of(i) : List.of()))
    .fork(
        b -> b.nextStage(new BranchAJoiner()).sink((d, id) -> saveA(d)),
        b -> b.nextStage(new BranchBJoiner()).sink((d, id) -> saveB(d))
    );
```

### 3. 聚合与转换 (Aggregation & Transformation)

```java
import com.lrenyi.template.flow.api.NextStageSpec;

FlowPipeline<AuditLog> pipeline = FlowPipeline.builder("audit-pipeline", AuditLog.class, flowManager)
    // 转换为 List<AuditLog>
    .aggregate(100, 5, TimeUnit.SECONDS)
    // 处理聚合后的数据
    .nextStage(NextStageSpec.of((Class<List<AuditLog>>) (Class<?>) List.class, new BatchSaveJoiner(), batch -> List.of(batch)))
    .sink((batch, jobId) -> log.info("Saved batch of size {}", batch.size()));
```

## 资源与监控

### 1. 资源控制
每个阶段（Stage）在运行时都会被注册为独立的子任务（例如 `jobId:0`, `jobId:1`）。你可以通过配置为不同阶段指定不同的存储类型（如 `LOCAL_BOUNDED` 或 `Caffeine`）。

### 2. 统一监控
使用 `PipelineProgressTracker` 可以获得整个管道的全局快照；生产/消费等细粒度生命周期信号仍由各子阶段 `ProgressTracker` 接收，管道级追踪器以聚合快照与完成判定为主（见类型 Javadoc）。
*   `terminated`: 反映最终 Sink 阶段的完成数。
*   `inStorage`: 所有各阶段缓存中的数据总和。

**仪表盘 `jobId` 标签（管道）**：若不希望出现整段 UUID，请在**首次 `startPush` 之前**对 `FlowPipeline#getProgressTracker()` 调用 `setMetricJobId("可读名称")`；各阶段指标标签为该名称 + 后缀（如 `:0`、`:2:fork:0`）。若在 `startPush` 之后才设置，子阶段 `ProgressTracker` 会尽量同步，但背压等已在 Launcher 创建时绑定的指标可能仍带旧前缀。

## 最佳实践

1.  **无副作用转换**: `transformer` 尽量只做纯函数式的类型转换或过滤。
2.  **合理设置分叉**: 过多的并行分叉会增加虚拟线程负载，请根据 `concurrencyLimit` 合理规划。
3.  **背压感知**: 当管道中任一阶段处理缓慢，背压会自动传播至源头，业务层无需手动处理。
