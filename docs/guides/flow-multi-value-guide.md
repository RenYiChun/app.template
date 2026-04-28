# Flow 同 Key 多 Value 使用指南

本文档只说明当前仓库中已经落地的同 key 多 value 能力，范围限定在 `template-flow` 的 keyed cache 路径。

## 1. 适用范围

- 仅对 `FlowStorageType.LOCAL_BOUNDED` 的 keyed cache 路径生效。
- `QUEUE` 或无 key 存储不提供同 key 多 value 语义。
- 当前能力是单机内缓存语义，不提供跨节点一致性。

## 2. 能解决什么问题

- 同一个 `joinKey` 下，允许保留多条等待数据，而不是后到数据直接覆盖先到数据。
- 在 `needMatched() == true` 的配对模式下，新数据到达时会依次尝试当前 key 下的多个候选。
- 在 `needMatched() == false` 的覆盖模式下，同 key 数据按槽位容量排队，超限时按配置裁剪。

典型场景：

- 订单流与物流流按订单号配对，但同一订单号短时间内可能先到多条物流记录。
- 多个上游子流汇聚到同一业务主键，需要短时间暂存多个候选。
- 高并发同 key 写入下，不希望单值语义频繁触发 `REPLACE`。

## 3. 配置位置

配置位于 `app.template.flow.limits.per-job.keyed-cache.*`。

```yaml
app:
  template:
    flow:
      limits:
        per-job:
          storage-capacity: 40000
          consumer-threads: 1000
          keyed-cache:
            cache-ttl-mill: 10000
            multi-value-enabled: true
            multi-value-max-per-key: 16
            multi-value-overflow-policy: DROP_OLDEST
            pairing-multi-match-enabled: false
```

关键项：

- `multi-value-enabled`
  - 是否开启同 key 多 value，默认 `false`。
- `multi-value-max-per-key`
  - 单个 `joinKey` 下允许保留的最大 entry 数，开启多值时必须 `> 0`。
- `multi-value-overflow-policy`
  - 超限策略，当前支持 `DROP_OLDEST`、`DROP_NEWEST`。
- `pairing-multi-match-enabled`
  - 配对成功后是否保留该 key 槽位中的剩余条目。默认 `false`，即成功配对后清理剩余项。

对应代码配置：

```java
TemplateConfigProperties.Flow flowConfig = new TemplateConfigProperties.Flow();
TemplateConfigProperties.Flow.KeyedCache keyedCache =
        flowConfig.getLimits().getPerJob().getKeyedCache();

keyedCache.setMultiValueEnabled(true);
keyedCache.setMultiValueMaxPerKey(16);
keyedCache.setMultiValueOverflowPolicy(
        TemplateConfigProperties.Flow.MultiValueOverflowPolicy.DROP_OLDEST);
keyedCache.setPairingMultiMatchEnabled(false);
```

## 4. 运行语义

### 4.1 覆盖模式

当 `needMatched() == false`：

- 单值模式：同 key 新值覆盖旧值，旧值以 `EgressReason.REPLACE` 离场。
- 多值模式：同 key 新值追加到槽位；超限时按 `multi-value-overflow-policy` 裁剪。

### 4.2 配对模式

当 `needMatched() == true`：

- 新数据到达时，会按顺序尝试当前 key 槽位中的候选数据。
- `isMatched(existing, incoming)` 返回 `true` 时，触发 `onPairConsumed(existing, incoming, jobId)`。
- 全部候选都不匹配时，数据继续留在槽位中等待后续配对，不会因为单次不匹配立即丢弃。

### 4.3 配对成功后的剩余条目

- `pairing-multi-match-enabled=false`
  - 当前 key 下配对成功后，槽位中剩余未匹配条目会被清理，并以 `EgressReason.CLEARED_AFTER_PAIR_SUCCESS` 进入 `onSingleConsumed`。
- `pairing-multi-match-enabled=true`
  - 配对成功后不主动清理剩余条目，它们继续留在槽位等待后续数据。

### 4.4 超限与超时

- `multi-value-max-per-key` 达到上限后：
  - `DROP_OLDEST` 会淘汰最老条目，对应 `EgressReason.OVERFLOW_DROP_OLDEST`
  - `DROP_NEWEST` 会丢弃新入条目，对应 `EgressReason.OVERFLOW_DROP_NEWEST`
- TTL 到期时，未配对条目会通过单条消费路径离场；当前实现使用 `EgressReason.SINGLE_CONSUMED`。

## 5. 接入代码示例

```java
class OrderJoiner implements FlowJoiner<OrderEvent> {
    @Override
    public Class<OrderEvent> getDataType() {
        return OrderEvent.class;
    }

    @Override
    public FlowSourceProvider<OrderEvent> sourceProvider() {
        return provider;
    }

    @Override
    public String joinKey(OrderEvent item) {
        return item.orderId();
    }

    @Override
    public boolean needMatched() {
        return true;
    }

    @Override
    public boolean isMatched(OrderEvent existing, OrderEvent incoming) {
        return existing.orderId().equals(incoming.orderId())
                && existing.kind() != incoming.kind();
    }

    @Override
    public void onPairConsumed(OrderEvent existing, OrderEvent incoming, String jobId) {
        // 处理成功配对
    }

    @Override
    public void onSingleConsumed(OrderEvent item, String jobId, EgressReason reason) {
        // 处理单条正常离场或被动离场
    }
}
```

## 6. 重点出口原因

多值场景下最常见的 `EgressReason`：

- `REPLACE`
- `OVERFLOW_DROP_OLDEST`
- `OVERFLOW_DROP_NEWEST`
- `CLEARED_AFTER_PAIR_SUCCESS`

主动成功路径仍然是：

- `PAIR_MATCHED`
- `SINGLE_CONSUMED`

## 7. 监控与排障建议

- 先看业务侧 `onSingleConsumed(item, jobId, reason)` 收到的 `EgressReason`，确认是否集中在 overflow、replace 或配对成功后的清理。
- 若 `OVERFLOW_DROP_*` 明显升高，优先检查：
  - `multi-value-max-per-key` 是否过小
  - `joinKey` 是否过于集中
  - 下游 `onPairConsumed` / `onSingleConsumed` 是否过慢
- 若未配对单条离场明显升高，优先检查：
  - `keyed-cache.cache-ttl-mill` 是否过短
  - `isMatched` 规则是否过严

## 8. 使用边界

- 多 value 不是无限缓存；总容量仍受 `storage-capacity` 限制。
- 开启多值后，单 key 内存占用会放大，建议先从较小的 `multi-value-max-per-key` 开始验证。
- 如果业务本质是严格单值覆盖，不要开启该能力。

## 9. 参考

- [Flow 流聚合使用指导](flow-usage-guide.md)
- [Flow 配置参考](../getting-started/config-reference.md)
- [EgressReason](../../template-flow/src/main/java/com/lrenyi/template/flow/model/EgressReason.java)
- [TemplateConfigProperties](../../template-core/src/main/java/com/lrenyi/template/core/TemplateConfigProperties.java)
