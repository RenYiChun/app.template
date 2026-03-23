# Template Flow Sources

`template-flow-sources` 是 `template-flow` 的数据源适配器集合，不做自动装配，不引入新抽象，只负责把外部数据源包装成 `FlowSource` / `FlowSourceProvider`。

## 模块一览

- `template-flow-sources-kafka`：Kafka `Consumer` 适配。
- `template-flow-sources-nats`：NATS `Subscription` 适配。
- `template-flow-sources-paged`：分页 API / RPC 拉取适配。

## 接入原则

1. 先准备好外部资源，再交给 Flow Sources。
1. `FlowSourceProvider` 负责把多个子流交给 Flow 引擎并发消费。
1. `close()` 都是幂等的，且以 best-effort 方式释放资源。
1. 外部资源的订阅、连接、线程模型仍由调用方负责。

## Kafka

Kafka 适合按分区或按 consumer 拆成多个子流。

```java
List<KafkaConsumer<?, ?>> consumers = List.of(consumerA, consumerB);
KafkaFlowSourceProvider<OrderEvent> provider =
        new KafkaFlowSourceProvider<>(consumers, record -> map(record), Duration.ofSeconds(1));
```

- 每个 `KafkaConsumer` 对应一个子流。
- 调用方必须先完成 `subscribe()` / `assign()`。
- `provider.close()` 会关闭它创建的子流，并最终关闭对应 consumer。

## NATS

NATS 适合多个 subscription 产出同一种业务模型。

```java
List<Subscription> subscriptions = List.of(subA, subB);
NatsFlowSourceProvider<OrderEvent> provider =
        new NatsFlowSourceProvider<>(subscriptions, msg -> map(msg), Duration.ofSeconds(1));
```

- 每个 `Subscription` 对应一个子流。
- 调用方负责创建和绑定订阅。
- `provider.close()` 会幂等地取消订阅并释放子流。

## Paged

分页源适合 HTTP/RPC 分页接口。

```java
PagedFlowSource<OrderEvent> source = new PagedFlowSource<>(pageToken -> {
    PageResult<OrderEvent> page = fetchPage(pageToken);
    return page;
}, () -> closeClient());
```

- 首次 `fetch(null)`。
- 后续使用上一页返回的 `nextPageToken`。
- `PageResult.of(null)` 会被视为 `emptyList()`。
- `close()` 会先切断后续拉取，再执行 `onClose` 回调。

## 生命周期

- `hasNext()` 负责推进读取，不负责关闭外部连接。
- `next()` 只在 `hasNext()` 返回 `true` 后调用。
- `close()` 是终止信号，调用后不应再继续拉取。
- 对于带回调的分页源，回调应视为 best-effort 清理逻辑，不要把业务主逻辑放进去。

## 适用边界

- Kafka 和 NATS 适合“外部系统天然支持流式消费”的场景。
- Paged 适合“可分页遍历，且能接受轮询拉取”的场景。
- 如果不同来源的消息模型不一致，先统一成一个业务 DTO，再交给 Flow 使用。
