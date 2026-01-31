---
name: Source 抽象设计
overview: 在 flow 包中引入 FlowSource（单子流）与 FlowSourceProvider（多子流提供者）抽象，引擎只依赖 Source 接口驱动 Launcher；业务实现 Source 即可，可来自 Iterator、分页 API、Kafka 等。不考虑对旧 API 的兼容。
todos: []
isProject: false
---

# Source 抽象设计

## 一、目标

- **目标**：将「数据从哪来」抽象为可替换的 Source 接口，业务实现 `FlowSourceProvider<T>` 即可（可包装 Iterator、分页 API、Kafka 等），引擎只依赖 Source 驱动 `launcher.launch(item)`，便于后续支持 request(n) 等背压形态。
- **不考虑兼容**：框架才起步，不保留 `Stream<Stream<T>> sources()`，FlowJoiner 直接采用 `FlowSourceProvider<T> sourceProvider()` 作为唯一数据源入口。

---

## 二、当前契约（需保留的语义）

- **两层结构**：外层为**并发单元**（如多个目录、多个分区），每个单元对应一个子流；内层为**子流**，按顺序产出 `T`，每条交给 `launcher.launch(item)`。
- **并发控制**：同时活跃的子流数由 `jobProducerLimit`（Semaphore）限制；每个子流在独立虚拟线程中拉取并 launch。
- **背压**：子流侧只负责「拉一条、launch 一条」；背压由 `FlowLauncher.launch()` 内部的 `awaitBackpressure()` 负责，Source 层无需关心。

因此 Source 抽象需要表达两层：**子流**（产出 T）、**子流提供者**（产出多个子流）。

---

## 三、接口设计

### 3.1 子流：`FlowSource<T>`

表示**单个数据流**，按顺序产出 `T`。采用「拉取 + 可关闭」语义，与 Iterator 类似，便于包装 JDK/第三方 API。

**建议位置**：`com.lrenyi.template.core.flow.source.FlowSource`

```java
/**
 * 单子流数据源：按顺序产出 T，由引擎拉取并交给 Launcher。
 * 可包装 Stream、Iterator、分页 API、Kafka 等。
 */
public interface FlowSource<T> extends AutoCloseable {

    /**
     * 是否还有下一条数据。
     * 若底层阻塞（如 Kafka poll），可抛 InterruptedException。
     */
    boolean hasNext() throws InterruptedException;

    /**
     * 取下一条数据；若没有则抛 NoSuchElementException。
     * 调用前应保证 hasNext() 为 true。
     */
    T next();

    @Override
    void close();
}
```

- **为何不用 Iterator&lt;T&gt; 直接作为类型**：Iterator 没有 `close()`，且 `hasNext()` 不声明 `InterruptedException`；很多 I/O 源（Kafka、HTTP 分页）会阻塞，需要可中断。用 FlowSource 统一语义，适配器里再包一层 Iterator/Stream。
- **可选**：若希望「无异常声明」的 API，可提供 `default boolean hasNextSafe()` 内部 catch InterruptedException 并转为 boolean；引擎仍用 `hasNext()` 以支持中断。建议首版保留 `throws InterruptedException`。

### 3.2 子流提供者：`FlowSourceProvider<T>`

表示**多个子流**的提供者（对应原来的外层 `Stream<Stream<T>>`），每个子流由引擎在独立虚拟线程中消费。

**建议位置**：`com.lrenyi.template.core.flow.source.FlowSourceProvider`

```java
/**
 * 多子流提供者：产出多个 FlowSource&lt;T&gt;，每个对应一个并发单元。
 * 由引擎按 jobProducerLimit 并发消费。
 */
public interface FlowSourceProvider<T> extends AutoCloseable {

    /**
     * 是否还有下一个子流。
     */
    boolean hasNextSubSource() throws InterruptedException;

    /**
     * 取下一个人流；调用前应保证 hasNextSubSource() 为 true。
     */
    FlowSource<T> nextSubSource();

    @Override
    void close();
}
```

- 引擎逻辑：`while (provider.hasNextSubSource()) { acquire(); startVirtualThread(() -> { try (FlowSource<T> sub = provider.nextSubSource()) { while (sub.hasNext()) launcher.launch(sub.next()); } finally { release(); } }); }`，最后 `provider.close()`。
- **为何不用 `Iterator<FlowSource<T>>**`：同样需要 `close()` 与可中断的 `hasNext`；Provider 可能持有连接池、客户端等资源，统一用 AutoCloseable 更清晰。

---

## 四、FlowJoiner 的改动

在 [FlowJoiner](template-core/src/main/java/com/lrenyi/template/core/flow/FlowJoiner.java) 中：

- **删除** `Stream<Stream<T>> sources()`。
- **新增**唯一数据源方法：`FlowSourceProvider<T> sourceProvider()`（无默认实现，业务必须实现）。

引擎只调用 `joiner.sourceProvider()`，不再出现 `Stream`。

---

## 五、适配器与实现类（可选工具）

### 5.1 `FlowSourceAdapters`（可选）

**建议位置**：`com.lrenyi.template.core.flow.source.FlowSourceAdapters`

- **fromStreams(Stream&lt;Stream&lt;T&gt;&gt;)**：返回 `FlowSourceProvider<T>`。业务若已有 `Stream<Stream<T>>` 形态的数据，可直接用此方法得到 Provider 并在 `sourceProvider()` 里返回。
- **fromIterator(Iterator&lt;T&gt;, Runnable onClose)**：返回 `FlowSource<T>`。单流场景下业务可构造「只有一个子流」的 Provider，其 `nextSubSource()` 返回 `fromIterator(...)`。

### 5.2 典型 FlowSource 实现（适配器内部或同包）

| 实现类 | 用途 | 要点 |

|--------|------|------|

| **StreamFlowSource&lt;T&gt;** | 包装 `Stream&lt;T&gt;` | iterator() + close 关闭 Stream；hasNext 可响应中断。 |

| **IteratorFlowSource&lt;T&gt;** | 包装 `Iterator&lt;T&gt;` | hasNext/next 委托；close 空或回调。 |

| Provider 实现（如内部类） | 包装 `Stream&lt;Stream&lt;T&gt;&gt;` | 外层迭代器 + 每子 Stream 包装为 StreamFlowSource；close 关闭外层 Stream。 |

首版实现：**FlowSource**、**FlowSourceProvider** 两个接口；**FlowSourceAdapters.fromStreams**（及内部 StreamFlowSource、Provider 实现）按需提供，便于业务从 Stream 迁移。

---

## 六、引擎侧改动

[FlowJoinerEngine](template-core/src/main/java/com/lrenyi/template/core/flow/FlowJoinerEngine.java) 的 `run` 方法：

- 当前：`try (Stream<Stream<T>> parentStream = joiner.sources()) { parentStream.forEach(subStream -> { ... }); }`
- 改为：`try (FlowSourceProvider<T> provider = joiner.sourceProvider()) { while (provider.hasNextSubSource()) { streamConcurrencySemaphore.acquire(); FlowSource<T> sub = provider.nextSubSource(); Thread.ofVirtual().name("prod-" + jobId).start(() -> { try (sub) { while (sub.hasNext()) launcher.launch(sub.next()); } finally { streamConcurrencySemaphore.release(); } }); } }`  
- 需处理 `InterruptedException`：`hasNextSubSource()` / `sub.hasNext()` 若抛出，应释放 semaphore、恢复中断状态、并结束该子流或任务。

这样引擎只依赖 `FlowSourceProvider` / `FlowSource`，不再依赖 `Stream`。

---

## 七、目录与依赖

```
flow/
  source/
    FlowSource.java           # 单子流
    FlowSourceProvider.java   # 多子流提供者
    FlowSourceAdapters.java   # 静态工厂：fromStreams、可选 fromIterator
    impl/                      # 可选，若希望实现类单独放
      StreamFlowSource.java
      StreamFlowSourceProvider.java  # 或内置于 Adapters 为私有静态类
```

- `FlowJoiner` 仅依赖 `FlowSourceProvider`，不依赖 `Stream`。
- `FlowJoinerEngine` 依赖 `FlowSourceProvider`、`FlowSource`，不再使用 `Stream`。

---

## 八、后续可扩展点（不纳入本次实现）

- **request(n) 背压**：在 `FlowSource` 上增加 `long request(long n)` 或引擎按「已 launch 未消费」数量限制拉取节奏。  
- **单流 run 重载**：`run(jobId, joiner, singleSource, ...)`，内部构造「仅包含一个 subSource 的 Provider」，方便单流业务。  
- **Kafka / Reactor 等**：业务实现 `FlowSourceProvider` 和 `FlowSource`，从 Kafka consumer 的 poll 或 Flux 的 blockFirst 驱动 hasNext/next 即可。

---

## 九、实现清单（待你确认后落地）

1. 新增 `flow.source.FlowSource<T>`（hasNext/next/close）。
2. 新增 `flow.source.FlowSourceProvider<T>`（hasNextSubSource/nextSubSource/close）。
3. 新增 `FlowSourceAdapters.fromStreams(Stream<Stream<T>>)`，返回 Provider；内部实现 `StreamFlowSource`、`StreamFlowSourceProvider`（或私有内部类）。
4. `FlowJoiner` 增加 `default FlowSourceProvider<T> sourceProvider()`，默认 `FlowSourceAdapters.fromStreams(sources())`。
5. `FlowJoinerEngine.run` 改为使用 `joiner.sourceProvider()`，按上述 while + 虚拟线程 + semaphore 逻辑驱动，并正确处理 InterruptedException 与 close。

若你希望「业务可只实现 sourceProvider 而不再实现 sources()」，可再增加：将 `sources()` 改为 default 并实现为 `throw new UnsupportedOperationException();`，这样新业务只需实现 `sourceProvider()`。