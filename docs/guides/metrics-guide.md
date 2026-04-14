# 监控指标使用指南

本文档聚焦当前仓库中已经落地的指标能力，重点说明 `template-flow` 与 `template-flow-sources` 的接入、指标名和排障入口。

## 1. 最小接入

如果应用已经引入 Spring Boot Actuator 与 Prometheus registry，只需要继续引入 Flow 相关模块即可：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

推荐配置：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
  endpoint:
    health:
      show-details: when-authorized
  prometheus:
    metrics:
      export:
        enabled: true
```

启动后建议先验证：

- `GET /actuator/prometheus`
- `GET /actuator/metrics`
- `GET /actuator/health`

## 2. Flow 指标

Flow 指标前缀是 `app.template.flow.`，Prometheus 导出时 `.` 会变成 `_`。

### 2.1 Counter

| 指标名 | 标签 | 说明 |
|---|---|---|
| `app.template.flow.production_acquired` | `jobId` | 成功获取生产许可、进入管道的条数 |
| `app.template.flow.production_released` | `jobId` | 完成存储或离开生产阶段的条数 |
| `app.template.flow.terminated` | `jobId` | 物理终结条数，可用于估算 TPS |
| `app.template.flow.errors` | `errorType`, `phase` | 统一错误计数，不带 `jobId`，避免高基数 |

`errors.phase` 当前值域是：

- `PRODUCTION`
- `STORAGE`
- `CONSUMPTION`
- `FINALIZATION`

### 2.2 Timer

| 指标名 | 标签 | 说明 |
|---|---|---|
| `app.template.flow.deposit.duration` | `jobId` | 单条数据进入存储的耗时 |
| `app.template.flow.match.duration` | `jobId` | 配对处理端到端耗时 |
| `app.template.flow.finalize.duration` | `jobId` | 单条离场处理端到端耗时 |

### 2.3 Gauge

全局资源指标：

| 指标名 | 标签 | 说明 |
|---|---|---|
| `app.template.flow.resources.in_flight_production.used` | 无 | 全局生产在途已占用量 |
| `app.template.flow.resources.in_flight_production.limit` | 无 | 全局生产在途上限 |
| `app.template.flow.resources.producer_threads.used` | 无 | 全局生产线程已占用量 |
| `app.template.flow.resources.producer_threads.limit` | 无 | 全局生产线程上限 |
| `app.template.flow.resources.storage.used` | 无 | 全局存储已占用量 |
| `app.template.flow.resources.storage.limit` | 无 | 全局存储上限 |
| `app.template.flow.resources.in_flight_consumer.used` | 无 | 全局已离库未终结数量 |
| `app.template.flow.resources.in_flight_consumer.limit` | 无 | 全局已离库未终结上限 |
| `app.template.flow.resources.consumer_threads.used` | 无 | 全局消费线程已占用量 |
| `app.template.flow.resources.consumer_threads.limit` | 无 | 全局消费线程上限 |

按 job 资源指标：

| 指标名 | 标签 | 说明 |
|---|---|---|
| `app.template.flow.resources.per_job.in_flight_production.used` | `jobId` | 每个 job 的生产在途已占用量 |
| `app.template.flow.resources.per_job.in_flight_production.limit` | `jobId` | 每个 job 的生产在途上限 |
| `app.template.flow.resources.per_job.producer_threads.used` | `jobId` | 每个 job 的生产线程已占用量 |
| `app.template.flow.resources.per_job.producer_threads.limit` | `jobId` | 每个 job 的生产线程上限 |
| `app.template.flow.resources.per_job.storage.used` | `jobId` | 每个 job 的存储已占用量 |
| `app.template.flow.resources.per_job.storage.limit` | `jobId` | 每个 job 的存储上限 |
| `app.template.flow.resources.per_job.in_flight_consumer.used` | `jobId` | 每个 job 的已离库未终结数量 |
| `app.template.flow.resources.per_job.in_flight_consumer.limit` | `jobId` | 每个 job 的已离库未终结上限 |
| `app.template.flow.resources.per_job.consumer_threads.used` | `jobId` | 每个 job 的消费线程已占用量 |
| `app.template.flow.resources.per_job.consumer_threads.limit` | `jobId` | 每个 job 的消费线程上限 |

完成判定相关指标：

| 指标名 | 标签 | 说明 |
|---|---|---|
| `app.template.flow.completion.source_finished` | `jobId` | source 是否已读完，`0/1` |
| `app.template.flow.completion.in_flight_push` | `jobId` | 推送模式下尚未完成的 push 数量 |
| `app.template.flow.completion.active_consumers` | `jobId` | 正在执行消费回调的活跃消费者数量 |
| `app.template.flow.completion.pending_consumers` | `jobId` | 已离库但尚未终结的待消费未清数量 |

## 3. Flow 出口原因怎么看

Flow 当前没有单独暴露 `egress.active` / `egress.passive` 这组旧口径指标。  
排障时应优先看：

- 业务侧 `onSingleConsumed(item, jobId, reason)` 收到的 `EgressReason`
- `FlowProgressSnapshot.passiveEgressByReason()`
- `terminated`、`production_acquired`、`production_released` 之间的数量关系

当前 `EgressReason` 主要包括：

- `PAIR_MATCHED`
- `SINGLE_CONSUMED`
- `TIMEOUT`
- `EVICTION`
- `REPLACE`
- `OVERFLOW_DROP_OLDEST`
- `OVERFLOW_DROP_NEWEST`
- `MISMATCH`
- `REJECT`
- `BACKPRESSURE_TIMEOUT`
- `SHUTDOWN`
- `CLEARED_AFTER_PAIR_SUCCESS`

## 4. Flow Health

当 classpath 中存在 Actuator 时，`template-flow` 会自动注册 `FlowActuatorHealthIndicator`，将内部健康状态桥接到 `/actuator/health`。

状态映射：

- `HEALTHY` -> `UP`
- `DEGRADED` -> `DEGRADED`
- `UNHEALTHY` -> `DOWN`

文档层不要依赖某个固定组件名去取 JSON 路径；不同 Spring Boot 版本或 bean 命名方式下，健康详情节点名可能不同。更稳妥的做法是直接查看 `/actuator/health` 返回中与 Flow 相关的组件详情。

## 5. Flow Sources 指标

`KafkaFlowSource` 与 `NatsFlowSource` 支持可选的 `MeterRegistry` 构造参数。传入后会暴露这些指标：

| 指标名 | 标签 | 说明 |
|---|---|---|
| `app.template.source.poll.duration` | `sourceType` | 单次 poll 耗时 |
| `app.template.source.received` | `sourceType` | 成功接收到的数据条数 |
| `app.template.source.errors` | `sourceType`, `errorType` | source 层错误数 |
| `app.template.source.nats.pending.messages` | 无 | NATS 订阅待处理消息数，仅 NATS |

`sourceType` 当前值域是：

- `kafka`
- `nats`

示例：

```java
KafkaFlowSource<String> kafkaSource =
        new KafkaFlowSource<>(consumer, mapper, Duration.ofSeconds(1), meterRegistry);

NatsFlowSource<String> natsSource =
        new NatsFlowSource<>(subscription, mapper, Duration.ofSeconds(1), meterRegistry);
```

`PagedFlowSource` 当前不暴露 Micrometer 指标；它主要提供最小分页拉取能力。

## 6. PromQL 示例

```promql
# Flow TPS
rate(app_template_flow_terminated_total[1m])

# 生产进入速率
rate(app_template_flow_production_acquired_total[1m])

# 每个 job 的存储占用
app_template_flow_resources_per_job_storage_used

# 每个 job 的活跃消费者数
app_template_flow_completion_active_consumers

# Flow 统一错误，按阶段分组
sum by (phase) (rate(app_template_flow_errors_total[5m]))

# Kafka / NATS source 接收速率
sum by (sourceType) (rate(app_template_source_received_total[5m]))
```

## 7. 排障顺序

第一次看板建议按这个顺序：

1. `/actuator/health` 看 Flow 是否 `UP / DEGRADED / DOWN`
2. `terminated` 与 `production_acquired` 看是否在稳定前进
3. `resources.per_job.*` 看是否卡在生产、存储或消费资源上限
4. source 指标看上游是否还有稳定输入
5. 业务侧 `EgressReason` 和日志看被动离场原因

## 8. 参考

- [Flow 流聚合使用指导](flow-usage-guide.md)
- [Flow 同 Key 多 Value 使用指南](flow-multi-value-guide.md)
- [Flow 配置参考](../getting-started/config-reference.md)
- [FlowMetricNames](../../template-flow/src/main/java/com/lrenyi/template/flow/metrics/FlowMetricNames.java)
- [FlowActuatorHealthIndicator](../../template-flow/src/main/java/com/lrenyi/template/flow/health/FlowActuatorHealthIndicator.java)
