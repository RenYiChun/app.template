# Flow 同 Key 多 Value 功能使用指南

## 1. 功能概述

Flow 引擎的 **同 Key 多 Value** 功能允许在 Caffeine 存储模式下，**同一个 `joinKey` 下缓存多条等待数据**，而不再局限于「一 key 一 value」的单值语义。

- **默认行为**：`multiValueEnabled=false`，保持原有单值语义，升级零影响。
- **开启后**：同一 key 可容纳多条数据（由 `multiValueMaxPerKey` 控制），支持**多候选配对**（incoming 依次尝试 slot 内所有候选直到匹配或全部试完）、超限策略等。
- **适用范围**：仅作用于 `FlowStorageType.CAFFEINE` 的存储路径；`QUEUE` 存储不受影响。

---

## 2. 适用场景与需求

### 2.1 可满足的需求

| 需求 | 说明 |
|------|------|
| **多候选配对** | 同一 key 下有多条等待数据，新数据到达时**依次尝试** slot 内所有候选（A、B、C…），直到匹配或全部试完；不匹配的 partner 放回队尾，继续尝试下一个 |
| **配对失败回写** | `isMatched(partner, incoming)=false` 时，两条数据会写回槽位继续等待，而非立即走 `MISMATCH` 失败出口 |
| **覆盖模式多值** | 非配对模式下，同 key 可暂存多条，超限时按策略淘汰（DROP_OLDEST / DROP_NEWEST） |
| **驱逐全量配对** | 槽位被 TTL/容量驱逐时，槽内每条 entry 与其余所有 entry 依次尝试 `isMatched`，最大化配对机会 |
| **preRetry 多值** | 配对重入时，多候选尝试：依次 poll 槽位内候选，直到匹配或全部试完；不匹配的 partner 放回队尾 |

### 2.2 典型业务场景

- **双流对齐**：订单流与物流流按订单号聚合，同一订单可能有多条物流记录先到达，需与后续订单记录配对。
- **多源汇聚**：同一业务主键下，多个子流陆续产出数据，需在缓存中排队等待配对。
- **高并发同 key**：高并发下同一 key 短时间内有多条数据到达，单值模式会频繁触发 REPLACE，多值模式可暂存并 FIFO 配对。

### 2.3 不适用场景

- **QUEUE 存储**：多值功能仅对 Caffeine 存储生效，Queue 存储无 key 概念，不涉及多值。
- **分布式多节点**：当前为单机 Caffeine 缓存，不提供跨节点的多值语义。

---

## 3. 配置说明

### 3.1 配置项

在 `app.template.flow.limits.per-job` 下新增以下配置：

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `multi-value-enabled` | boolean | false | 是否开启同 key 多 value |
| `multi-value-max-per-key` | int | 1 | 单 key 最大 value 数；开启后建议 16 |
| `multi-value-overflow-policy` | enum | DROP_OLDEST | 超限策略：`DROP_OLDEST`（淘汰最老项）或 `DROP_NEWEST`（丢弃新入项） |
| `pairing-multi-match-enabled` | boolean | false | 是否启用多对匹配；false 时配对成功后清空槽位内剩余条目并立即驱逐（`CLEARED_AFTER_PAIR_SUCCESS`）；true 时不做处理 |

### 3.2 校验规则

- `multi-value-max-per-key` 必须 > 0（开启多值时）。
- `multi-value-enabled=false` 时，忽略上述细项，等效 `multiValueMaxPerKey=1`，保持单值语义。

### 3.3 YAML 配置示例

```yaml
app:
  template:
    flow:
      limits:
        per-job:
          # 同 key 多 value 配置
          multi-value-enabled: true
          multi-value-max-per-key: 16
          multi-value-overflow-policy: DROP_OLDEST  # 或 DROP_NEWEST
          pairing-multi-match-enabled: false  # 配对成功后是否保留槽位内剩余条目
          # 其他 per-job 配置
          storage: 40000
          cache-ttl-mill: 10000
          consumer-concurrency: 1000
```

### 3.4 代码配置示例

```java
TemplateConfigProperties.Flow flowConfig = new TemplateConfigProperties.Flow();
flowConfig.getLimits().getPerJob().setMultiValueEnabled(true);
flowConfig.getLimits().getPerJob().setMultiValueMaxPerKey(16);
flowConfig.getLimits().getPerJob().setMultiValueOverflowPolicy(
    TemplateConfigProperties.Flow.MultiValueOverflowPolicy.DROP_OLDEST);
flowConfig.getLimits().getPerJob().setPairingMultiMatchEnabled(false);  // 配对成功后是否保留槽位内剩余条目
```

### 3.5 启动日志

开启多值后，启动时会输出配置摘要，例如：

```
[配置摘要] flow.multiValueEnabled=true, multiValueMaxPerKey=16, 预估最大 entry 数=640000
```

其中「预估最大 entry 数」= `storage × multiValueMaxPerKey`，可用于评估内存占用。

---

## 4. 行为语义

### 4.1 覆盖模式（needMatched=false）

| 模式 | 行为 |
|------|------|
| **单值** | 新值替换旧值，旧值走 `onFailed(..., REPLACE)` |
| **多值** | 新值追加队尾；超限按 `multi-value-overflow-policy` 淘汰，淘汰项走 `OVERFLOW_DROP_OLDEST` 或 `OVERFLOW_DROP_NEWEST` |

### 4.2 配对模式（needMatched=true）

| 模式 | 行为 |
|------|------|
| **单值** | 一进一出，与原有逻辑一致 |
| **多值** | 新数据到达时，**多候选尝试**：依次从槽位 poll 候选（A、B、C…），与 incoming 尝试 `isMatched`，直到匹配或全部试完；不匹配的 partner 放回队尾；若无等待项则入队等待 |
| **配对失败** | `isMatched(partner, incoming)=false` 时，将 partner 放回队尾，继续尝试下一个候选；全部不匹配则将 partner 与 incoming 写回槽位，再执行超限裁剪；不立即走 `MISMATCH` |
| **配对成功后** | 根据 `pairing-multi-match-enabled`：**false** 时清空槽位内剩余条目（`retryRemaining` 置为 -1），逐个走 `onFailed(..., CLEARED_AFTER_PAIR_SUCCESS)`；**true** 时不做处理，剩余条目继续留在槽位 |

### 4.3 驱逐（TTL 过期 / 容量满）

槽位被驱逐时，对槽内**每条 entry** 与**其余所有 entry** 依次尝试 `isMatched`：

- 若找到匹配：两条成对出库，走成功路径
- 若与所有其他 entry 均不匹配：该 entry 走 `handlePassiveFailure`（如 `onFailed(..., TIMEOUT)` 或 `EVICTION`）
- 每条出库 entry 正确释放 global storage 引用计数
- **重入标志重置**：若槽位内**至少有一对**配对成功，则所有未匹配条目的重入标志（`retryRemaining`）会被重置为 -1，使其不再走重入流程，直接进入失败出口，防止重入漏极

### 4.4 preRetry（配对重入）

- **多候选尝试**：与入缓存一致，依次从槽位 poll 候选（A、B、C…），与重入 entry 尝试 `isMatched`，直到匹配或全部试完；不匹配的 partner 放回队尾
- 若匹配成功：处理配对，并根据 `pairing-multi-match-enabled` 决定是否清空槽位内剩余条目
- 若全部不匹配：返回 `PROCEED_TO_REQUEUE`，重入 entry 继续入队

---

## 5. 失败原因（FailureReason）

多值模式新增细粒度原因：

| 原因 | 含义 |
|------|------|
| `OVERFLOW_DROP_OLDEST` | 多值模式超限时，淘汰最老项（对应 `multi-value-overflow-policy: DROP_OLDEST`） |
| `OVERFLOW_DROP_NEWEST` | 多值模式超限时，丢弃新入项（对应 `multi-value-overflow-policy: DROP_NEWEST`） |
| `CLEARED_AFTER_PAIR_SUCCESS` | 配对成功后清空剩余（`pairing-multi-match-enabled=false` 时，槽位内未匹配条目被主动驱逐） |

### 5.1 onFailed 处理示例

```java
@Override
public void onFailed(T item, String jobId, FailureReason reason) {
    switch (reason) {
        case OVERFLOW_DROP_OLDEST:
            // 被淘汰的最老项，可记录或重试
            log.warn("Item dropped (oldest): jobId={}, key={}", jobId, joinKey(item));
            break;
        case OVERFLOW_DROP_NEWEST:
            // 新入项被拒绝，可记录或重试
            log.warn("Item dropped (newest): jobId={}, key={}", jobId, joinKey(item));
            break;
        case CLEARED_AFTER_PAIR_SUCCESS:
            // 配对成功后槽位内剩余条目被清空
            log.warn("Item cleared after pair success: jobId={}, key={}", jobId, joinKey(item));
            break;
        case TIMEOUT:
        case EVICTION:
            // 其他原因...
            break;
    }
}
```

---

## 6. 监控指标

### 6.1 多值丢弃计数

| 指标名 | 类型 | 标签 | 说明 |
|--------|------|------|------|
| `app.template.flow.storage.multi-value.discard.total` | Counter | `jobId`, `reason` | 多值模式超限时丢弃的条数；`reason` 为 `overflow_drop_oldest` 或 `overflow_drop_newest` |

`EGRESS_PASSIVE` 指标在 `CLEARED_AFTER_PAIR_SUCCESS` 场景下也会累加，`reason` 标签为 `CLEARED_AFTER_PAIR_SUCCESS`。

### 6.2 进度快照

`FlowProgressSnapshot.passiveEgressByReason` 中可获取 `OVERFLOW_DROP_OLDEST`、`OVERFLOW_DROP_NEWEST`、`CLEARED_AFTER_PAIR_SUCCESS` 的计数，与指标一致。

---

## 7. 使用步骤

### 7.1 启用多值

1. 在 `application.yml` 或代码中设置 `multi-value-enabled: true`
2. 设置 `multi-value-max-per-key`（建议 16，可根据业务调整）
3. 选择 `multi-value-overflow-policy`：`DROP_OLDEST` 或 `DROP_NEWEST`

### 7.2 配对模式（needMatched=true）

确保 `FlowJoiner.needMatched()` 返回 `true`，并实现 `isMatched(existing, incoming)` 做业务校验。多值模式下，配对失败时两条数据会回写槽位，不会立即走 `MISMATCH`。

### 7.3 覆盖模式（needMatched=false）

多值模式下，同 key 数据会追加到队尾；超限时按 `overflow-policy` 淘汰，淘汰项走 `OVERFLOW_DROP_OLDEST` 或 `OVERFLOW_DROP_NEWEST`。

### 7.4 完整示例

```java
// 1. 配置
TemplateConfigProperties.Flow flowConfig = new TemplateConfigProperties.Flow();
flowConfig.getLimits().getPerJob().setMultiValueEnabled(true);
flowConfig.getLimits().getPerJob().setMultiValueMaxPerKey(16);
flowConfig.getLimits().getPerJob().setMultiValueOverflowPolicy(
    TemplateConfigProperties.Flow.MultiValueOverflowPolicy.DROP_OLDEST);

// 2. Joiner：配对模式
FlowJoiner<OrderLogisticsPair> joiner = new FlowJoiner<OrderLogisticsPair>() {
    @Override
    public FlowStorageType getStorageType() { return FlowStorageType.CAFFEINE; }
    
    @Override
    public Class<OrderLogisticsPair> getDataType() { return OrderLogisticsPair.class; }
    
    @Override
    public FlowSourceProvider<OrderLogisticsPair> sourceProvider() { /* ... */ }
    
    @Override
    public String joinKey(OrderLogisticsPair item) { return item.getOrderId(); }
    
    @Override
    public boolean needMatched() { return true; }
    
    @Override
    public boolean isMatched(OrderLogisticsPair existing, OrderLogisticsPair incoming) {
        // 业务校验：例如订单状态、物流类型等
        return existing.getOrderId().equals(incoming.getOrderId())
            && existing.getType() != incoming.getType();  // 订单与物流配对
    }
    
    @Override
    public void onSuccess(OrderLogisticsPair existing, OrderLogisticsPair incoming, String jobId) {
        // 配对成功处理
    }
    
    @Override
    public void onFailed(OrderLogisticsPair item, String jobId, FailureReason reason) {
        switch (reason) {
            case OVERFLOW_DROP_OLDEST:
            case OVERFLOW_DROP_NEWEST:
                log.warn("Overflow: jobId={}, reason={}", jobId, reason);
                break;
            case CLEARED_AFTER_PAIR_SUCCESS:
                log.warn("Cleared after pair success: jobId={}, key={}", jobId, joinKey(item));
                break;
            default:
                // 其他失败处理
                break;
        }
    }
    
    @Override
    public void onFailed(OrderLogisticsPair item, String jobId) {}
};

// 3. 运行
FlowManager manager = FlowManager.getInstance(flowConfig);
FlowJoinerEngine engine = new FlowJoinerEngine(manager);
engine.run("order-logistics-job", joiner, tracker, flowConfig);
```

---

## 8. 风险与建议

### 8.1 内存

- **风险**：多值下单 key 占用上升，总 entry 数可达 `storage × multiValueMaxPerKey`。
- **建议**：合理设置 `multiValueMaxPerKey`（如 16），关注启动日志中的「预估最大 entry 数」。

### 8.2 超限策略选择

| 策略 | 适用场景 |
|------|----------|
| `DROP_OLDEST` | 优先保留最新数据，适合「新数据更有价值」的场景 |
| `DROP_NEWEST` | 优先保留先到数据，适合「先到先配对」的严格 FIFO 场景 |

### 8.3 配对失败回写

配对失败时两条数据会回写槽位，可能触发超限裁剪。若业务上 `isMatched` 经常为 false，建议：

- 优化 `isMatched` 逻辑，减少无效配对尝试
- 适当增大 `multiValueMaxPerKey`，避免频繁 overflow

### 8.4 pairing-multi-match-enabled 选择

| 配置值 | 适用场景 |
|--------|----------|
| `false`（默认） | 同一 key 下只需一对配对成功即可，剩余条目应尽快清空，避免重入漏极；适合订单-物流等 1:1 配对 |
| `true` | 同一 key 下允许多对配对，剩余条目继续留在槽位等待后续 incoming；适合同一 key 下多组数据需分别配对的场景 |

### 8.5 兼容性

- 默认 `multiValueEnabled=false`，升级后行为零变化
- 老配置无需变更即可启动

---

## 9. 参考

- [Flow 流聚合使用指导](flow-usage-guide.md)
- [Flow 配置参考](../getting-started/config-reference.md#flow-配置)
- [FailureReason](template-flow/src/main/java/com/lrenyi/template/flow/model/FailureReason.java)
- [FlowMetricNames](template-flow/src/main/java/com/lrenyi/template/flow/metrics/FlowMetricNames.java)
