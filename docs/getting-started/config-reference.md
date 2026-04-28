# Flow 配置参考

本文档只覆盖 `app.template.flow` 及 Flow 接入时最常用的外围配置，不展开其他模块。

真实配置模型以 `template-core/src/main/java/com/lrenyi/template/core/TemplateConfigProperties.java` 为准。

## 1. 最小可运行配置

```yaml
app:
  template:
    enabled: true
    flow:
      limits:
        global:
          consumer-threads: 32
        per-job:
          producer-threads: 4
          consumer-threads: 8
          storage-capacity: 2048
          queue-poll-interval-mill: 1000
          keyed-cache:
            cache-ttl-mill: 30000
```

## 2. 配置结构

```yaml
app:
  template:
    enabled: true
    flow:
      producer-backpressure-blocking-mode: BLOCK_WITH_TIMEOUT
      producer-backpressure-timeout-mill: 30000
      consumer-acquire-blocking-mode: BLOCK_WITH_TIMEOUT
      consumer-acquire-timeout-mill: 30000
      show-status: false
      limits:
        global:
          fair-scheduling: true
          producer-threads: 0
          storage-capacity: 0
          consumer-threads: 0
          sink-consumer-threads: 64
          eviction-coordinator-threads: 1
          eviction-scan-interval-mill: 0
        per-job:
          producer-threads: 40
          consumer-threads: 1000
          storage-capacity: 40000
          queue-poll-interval-mill: 10000
          eviction-coordinator-threads: 0
          eviction-scan-interval-mill: 0
          keyed-cache:
            multi-value-enabled: false
            multi-value-max-per-key: 1
            multi-value-overflow-policy: DROP_OLDEST
            cache-ttl-mill: 10000
            must-match-retry-enabled: false
            must-match-retry-max-times: 3
            must-match-retry-backoff-mill: 0
            pairing-multi-match-enabled: false
```

## 3. 顶层开关

### `app.template.enabled`

- 类型：`boolean`
- 默认值：`true`
- 含义：框架总开关。关闭后，Flow 自动配置也不会生效。

### `app.template.flow.show-status`

- 类型：`boolean`
- 默认值：`false`
- 含义：完成判定和部分状态输出时是否打印更多状态日志。

## 4. 背压等待策略

### `app.template.flow.producer-backpressure-blocking-mode`

- 可选值：`BLOCK_FOREVER`、`BLOCK_WITH_TIMEOUT`
- 默认值：`BLOCK_WITH_TIMEOUT`
- 含义：生产侧触发背压时，是一直阻塞还是超时失败。

### `app.template.flow.producer-backpressure-timeout-mill`

- 类型：`long`
- 默认值：`30000`
- 生效条件：`producer-backpressure-blocking-mode=BLOCK_WITH_TIMEOUT`

### `app.template.flow.consumer-acquire-blocking-mode`

- 可选值：`BLOCK_FOREVER`、`BLOCK_WITH_TIMEOUT`
- 默认值：`BLOCK_WITH_TIMEOUT`
- 含义：消费侧申请许可时，是一直阻塞还是超时失败。

### `app.template.flow.consumer-acquire-timeout-mill`

- 类型：`long`
- 默认值：`30000`
- 生效条件：`consumer-acquire-blocking-mode=BLOCK_WITH_TIMEOUT`

## 5. `limits.global`

全局限制作用于整台应用实例上的所有 Flow Job。

### `fair-scheduling`

- 默认值：`true`
- 含义：全局信号量是否采用公平调度。

### `producer-threads`

- 默认值：`0`
- 含义：全局生产线程上限，`<= 0` 表示不限制。

### `storage-capacity`

- 默认值：`0`
- 含义：全局存储容量上限，`<= 0` 表示不限制。

### `consumer-threads`

- 默认值：`0`
- 含义：全局消费线程上限，`<= 0` 表示不限制。

### `sink-consumer-threads`

- 默认值：`64`
- 含义：全主机 **管道终端 Sink**（`.sink(...)` 用户回调）并发上限，`<= 0` 表示不限制。与 `consumer-threads` 独立：数据仍先经过既有消费背压（全局/每 Job 消费线程、在途消费槽位等），进入 Sink 回调前再占用本信号量。
- 阻塞与超时：与顶层 `consumer-acquire-blocking-mode`、`consumer-acquire-timeout-mill` 一致（与消费许可 acquire 相同语义）。

### `eviction-coordinator-threads`

- 默认值：`1`
- 含义：驱逐协调线程数。

### `eviction-scan-interval-mill`

- 默认值：`0`
- 含义：驱逐扫描间隔。`0` 表示阻塞等待，`> 0` 表示定期轮询。

## 6. `limits.per-job`

per-job 限制作用于单个 Flow Job。

### `producer-threads`

- 默认值：`40`
- 约束：必须 `> 0`

### `consumer-threads`

- 默认值：`1000`
- 约束：当全局 `consumer-threads <= 0` 时，per-job 至少要有一个有效值

### `storage-capacity`

- 默认值：`40000`
- 约束：必须 `> 0`
- **Pipeline**：使用 `FlowPipeline` 多阶段时，可在代码构建阶段按段覆盖本上限（见 `NextStageSpec` / `NextMapSpec` / `aggregate` / `sink` 的重载）；未覆盖的段仍使用此处或运行时传入的基底配置。

### `queue-poll-interval-mill`

- 默认值：`10000`
- 约束：必须 `> 0`
- 含义：`QUEUE` 存储模式的轮询间隔

### `eviction-coordinator-threads`

- 默认值：`0`
- 含义：`0` 时继承 `limits.global.eviction-coordinator-threads`

### `eviction-scan-interval-mill`

- 默认值：`0`
- 含义：`0` 时继承 `limits.global.eviction-scan-interval-mill`

## 7. `limits.per-job.keyed-cache`

这一组配置只对带 key 的缓存存储起作用，主要对应 `LOCAL_BOUNDED`。

### `multi-value-enabled`

- 默认值：`false`
- 含义：是否允许同一个 key 下同时保留多个 value

### `multi-value-max-per-key`

- 默认值：`1`
- 约束：开启多值模式时必须 `> 0`

### `multi-value-overflow-policy`

- 可选值：`DROP_OLDEST`、`DROP_NEWEST`
- 默认值：`DROP_OLDEST`

### `cache-ttl-mill`

- 默认值：`10000`
- 约束：必须 `> 0`
- 含义：缓存中单条数据的 TTL

### `must-match-retry-enabled`

- 默认值：`false`
- 含义：匹配失败时是否允许重入重试

### `must-match-retry-max-times`

- 默认值：`3`
- 约束：启用重试时必须 `>= 1`

### `must-match-retry-backoff-mill`

- 默认值：`0`
- 约束：必须 `>= 0`

### `pairing-multi-match-enabled`

- 默认值：`false`
- 含义：配对成功后，是否允许槽位内剩余数据继续参与后续配对

## 8. 配置校验规则

启动时会做基础合法性校验。最常见的失败原因是：

- `per-job.producer-threads <= 0`
- `per-job.storage-capacity <= 0`
- `per-job.queue-poll-interval-mill <= 0`
- `keyed-cache.cache-ttl-mill <= 0`
- 全局和 per-job 的 `consumer-threads` 同时无效

## 9. 推荐起步值

### 本地开发

```yaml
app:
  template:
    flow:
      limits:
        global:
          consumer-threads: 16
        per-job:
          producer-threads: 2
          consumer-threads: 4
          storage-capacity: 512
          queue-poll-interval-mill: 500
          keyed-cache:
            cache-ttl-mill: 10000
```

### 首次上线前压测

```yaml
app:
  template:
    flow:
      limits:
        global:
          consumer-threads: 128
          producer-threads: 64
          storage-capacity: 100000
        per-job:
          producer-threads: 8
          consumer-threads: 32
          storage-capacity: 10000
          queue-poll-interval-mill: 1000
          keyed-cache:
            cache-ttl-mill: 30000
```

第一次上线不要直接把所有全局 limit 设为 0，否则很难通过指标判断系统何时接近极限。

## 10. 相关文档

- [Flow 快速开始](quick-start.md)
- [Flow 使用指导](../guides/flow-usage-guide.md)
- [Flow 同 Key 多 Value 使用指南](../guides/flow-multi-value-guide.md)
