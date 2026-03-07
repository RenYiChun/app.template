# 监控指标系统使用教程

本文档介绍 app.template 框架的监控指标体系，基于 **Micrometer + Prometheus** 构建，涵盖快速接入、内置指标一览、自定义业务指标扩展、Prometheus
集成及常用 PromQL 查询。

---

## 目录

1. [快速开始](#1-快速开始)
2. [内置指标一览](#2-内置指标一览)
3. [自定义业务指标](#3-自定义业务指标)
4. [Prometheus 集成](#4-prometheus-集成)
5. [常用 PromQL 查询](#5-常用-promql-查询)
6. [安全配置](#6-安全配置)
7. [高基数标签防护](#7-高基数标签防护)
8. [FAQ](#8-faq)

---

## 1. 快速开始

### 1.1 依赖引入

框架已在 `template-api` 中内置了 Actuator 和 Prometheus 依赖，引用 `template-api` 的应用**无需额外添加依赖**即可自动获得指标能力。

如果你的模块不依赖 `template-api`，需手动添加：

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

### 1.2 application.yml 配置

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus, metrics
  endpoint:
    health:
      show-details: when-authorized
  prometheus:
    metrics:
      export:
        enabled: true
  metrics:
    distribution:
      # 启用 HTTP 请求直方图，才能暴露 _bucket 指标供 histogram_quantile 计算 P99/P95/P50
      percentiles-histogram:
        http.server.requests: true
```

### 1.3 验证端点

启动应用后访问以下端点：

| 端点                             | 说明                 |
|--------------------------------|--------------------|
| `GET /actuator/prometheus`     | Prometheus 格式的全量指标 |
| `GET /actuator/metrics`        | JSON 格式的指标名列表      |
| `GET /actuator/metrics/{name}` | 查看单个指标的详情          |
| `GET /actuator/health`         | 健康检查（含 Flow 引擎状态）  |

示例：

```bash
# 查看所有 Prometheus 格式指标
curl http://localhost:8080/actuator/prometheus

# 查看 Flow 引擎启动任务数
curl http://localhost:8080/actuator/metrics/app.template.flow.job.started

# 过滤包含 "flow" 的指标
curl http://localhost:8080/actuator/prometheus | grep "app_template_flow"
```

---

## 2. 内置指标一览

### 2.1 Flow 引擎指标

所有 Flow 引擎指标以 `app.template.flow.` 为前缀，定义在 `FlowMetricNames` 常量类中。

#### Counters（计数器）

| 指标名                                     | 标签                   | 含义                  | 关注场景                                    |
|-----------------------------------------|----------------------|---------------------|-----------------------------------------|
| `app.template.flow.job.started`         | `jobId`              | Job 启动次数            | 高：任务触发活跃                                |
| `app.template.flow.job.completed`       | `jobId`              | Job 正常完成次数          | completed/started = 任务成功率               |
| `app.template.flow.job.stopped`         | `jobId`              | Job 被手动停止次数         | 正常应接近 0，高则频繁人工干预                        |
| `app.template.flow.production.acquired` | `jobId`              | 已获取生产许可条数（进场量）      | 高：数据源产出快                                |
| `app.template.flow.production.released` | `jobId`              | 已存入 Storage 条数（入库量） | acquired - released = 在途生产中             |
| `app.template.flow.egress.active`       | `jobId`              | 主动出口累计数（业务达成量）      | 配对成功或 Finalizer 消费                      |
| `app.template.flow.egress.passive`      | `jobId`, `reason`    | 被动出口累计数（损耗量）        | passive/(active+passive) = 损耗率，>10% 需关注 |
| `app.template.flow.terminated`          | `jobId`              | 物理终结累计数             | rate(terminated[1m]) = TPS              |
| `app.template.flow.errors`              | `errorType`, `phase` | 统一错误计数              | 高：系统异常频繁，按 errorType 和 phase 下钻定位       |

**`egress.passive` 的 reason 值域：**

| reason     | 含义                         |
|------------|----------------------------|
| `TIMEOUT`  | TTL 过期，数据未等到配对即超时          |
| `EVICTION` | 容量淘汰，maxSize 满 LRU 策略踢出    |
| `REPLACE`  | 覆盖模式下同 Key 新数据顶替旧数据        |
| `MISMATCH` | 配对模式下 isMatched() 返回 false |
| `REJECT`   | Queue 满，新数据被拒绝入队           |
| `SHUTDOWN` | 系统关闭时缓存中残留的未处理数据           |

**`errors` 的 phase 值域：**

| phase          | 含义   |
|----------------|------|
| `PRODUCTION`   | 生产阶段 |
| `STORAGE`      | 存储阶段 |
| `CONSUMPTION`  | 消费阶段 |
| `FINALIZATION` | 终结阶段 |

#### Timers（计时器）

| 指标名                                       | 标签      | 含义                 | 关注场景                    |
|-------------------------------------------|---------|--------------------|-------------------------|
| `app.template.flow.deposit.duration`      | `jobId` | 单条数据存入 Storage 耗时  | 高：Storage 写入瓶颈（锁争用/队列满） |
| `app.template.flow.match.duration`        | `jobId` | Caffeine 配对处理端到端耗时 | 高：消费端饱和或 onSuccess 回调慢  |
| `app.template.flow.acquire.duration`      | `jobId` | 获取全局消费信号量耗时        | 高：消费端资源争用严重             |
| `app.template.flow.finalize.duration`     | `jobId` | 终结处理端到端耗时          | 高：消费执行器积压或回调慢           |
| `app.template.flow.backpressure.duration` | `jobId` | 背压等待总耗时            | 高：生产远超消费，系统过载           |

#### Gauges（仪表盘）

| 指标名                                                     | 标签                     | 含义               | 关注场景                    |
|---------------------------------------------------------|------------------------|------------------|-------------------------|
| `app.template.flow.limits.consumer-concurrency.used`    | —                      | 全主机消费许可已占用数      | 接近 limit 时消费端饱和         |
| `app.template.flow.limits.consumer-concurrency.limit`  | —                      | 全主机消费许可上限        | 利用率 = used / limit      |
| `app.template.flow.limits.producer-threads.used`        | `jobId`                | 每 Job 已占用生产线程数    | 接近 limit 时生产端饱和         |
| `app.template.flow.limits.producer-threads.limit`       | `jobId`                | 每 Job 生产线程上限      | —                        |
| `app.template.flow.limits.in-flight.used`               | `jobId`                | 每 Job 在途数据条数       | 背压触发前兆                   |
| `app.template.flow.limits.in-flight.limit`             | `jobId`                | 每 Job 在途数据上限      | —                        |
| `app.template.flow.limits.pending-consumer.count`       | `jobId`                | 每 Job 已离库未终结条数    | 背压触发前兆                   |
| `app.template.flow.limits.pending-consumer.limit`      | `jobId`                | 每 Job 背压阈值         | —                        |
| `app.template.flow.limits.storage.used`                 | `jobId`, `storageType` | 每 Job 缓存当前条数       | 接近 limit 则即将触发驱逐或背压     |
| `app.template.flow.limits.storage.limit`                | `jobId`, `storageType` | 每 Job 缓存容量上限      | —                        |
| `app.template.flow.launchers.active`                   | —                      | 当前运行中的 Job 数     | 为 0 时系统空闲               |
| `app.template.flow.completion.rate`                     | `jobId`                | 任务完成率            | 仅 totalExpected > 0 时注册 |

当启用全局限制时，还会暴露 `limits.producer-threads.global.*`、`limits.in-flight.global.*`、`limits.pending-consumer.global.*`、`limits.storage.global.*` 等指标。

**许可获取等待耗时**：`limits.acquire-wait-duration`（Timer，标签 `jobId`、`dimension`：producer-threads / in-flight / storage），用于观测各维度许可争用与饥饿。详见 [Flow 配置优化与全局化设计](../design/flow-limits-globalization.md) 第六章。

### 2.2 HTTP 认证指标 (template-api)

| 指标名                              | 标签       | 含义        |
|----------------------------------|----------|-----------|
| `app.template.http.auth.failure` | `reason` | 认证/授权失败次数 |

**reason 值域：**

| reason          | 含义        |
|-----------------|-----------|
| `MISSING_TOKEN` | 请求未携带认证令牌 |
| `EXPIRED_TOKEN` | 令牌已过期     |
| `INVALID_TOKEN` | 令牌无效      |
| `ACCESS_DENIED` | 已认证但无权限   |

### 2.3 OAuth2 指标 (template-oauth2-service)

| 指标名                                | 标签                       | 含义           |
|------------------------------------|--------------------------|--------------|
| `app.template.oauth2.token.issued` | `grantType`              | Token 签发次数   |
| `app.template.oauth2.token.failed` | `grantType`, `errorType` | Token 签发失败次数 |
| `app.template.oauth2.logout`       | —                        | 登出次数         |

### 2.4 Feign 调用指标 (template-cloud)

引入 `feign-micrometer` 后自动注册，无需手动埋点：

| 指标名            | 标签                         | 含义                 |
|----------------|----------------------------|--------------------|
| `feign.Client` | `method`, `host`, `status` | Feign HTTP 请求耗时和计数 |

### 2.5 数据源指标 (template-flow-sources)

需在构造 `KafkaFlowSource` / `NatsFlowSource` 时传入 `MeterRegistry`（第 4 个构造参数，可选）：

| 指标名                                         | 标签                        | 含义                    |
|---------------------------------------------|---------------------------|-----------------------|
| `app.template.source.received`              | `sourceType`              | 数据源接收数据条数             |
| `app.template.source.poll.duration`         | `sourceType`              | 单次 poll 耗时            |
| `app.template.source.errors`                | `sourceType`, `errorType` | 数据源错误次数               |
| `app.template.source.nats.pending.messages` | —                         | NATS 订阅待处理消息数（仅 NATS） |

**sourceType 值域：** `kafka`、`nats`

**启用方式：**

```java
// 无指标（默认，向后兼容）
new KafkaFlowSource<>(consumer,mapper,Duration.

ofSeconds(1));
        
        // 开启指标采集
        new KafkaFlowSource<>(consumer,mapper,Duration.

ofSeconds(1),meterRegistry);
        new NatsFlowSource<>(subscription,mapper,Duration.

ofSeconds(1),meterRegistry);
```

### 2.6 Dataforge 指标 (template-dataforge)

| 指标名                                       | 标签                    | 含义                                |
|-------------------------------------------|-----------------------|-----------------------------------|
| `app.template.dataforge.request.duration` | `method`              | GenericEntityController CRUD 操作耗时 |
| `app.template.dataforge.request.errors`   | `method`, `errorType` | CRUD 操作错误次数                       |

---

## 3. 自定义业务指标

### 3.1 使用 AppMetrics 工具类

`AppMetrics` 是框架提供的业务指标扩展工具类，注入即可使用，**无需直接操作 Micrometer API**。

```java

@Service
public class OrderService {
    
    private final AppMetrics appMetrics;
    
    public OrderService(AppMetrics appMetrics) {
        this.appMetrics = appMetrics;
    }
    
    public void createOrder(Order order) {
        // 方式一：计数器 +1
        appMetrics.count("app.template.order.created", "channel", order.getChannel());
        
        // 方式二：计数器 +N
        appMetrics.count("app.template.order.items", order.getItemCount(), "channel", order.getChannel());
        
        // 方式三：手动记录耗时（毫秒）
        long start = System.currentTimeMillis();
        doBusinessLogic(order);
        long elapsed = System.currentTimeMillis() - start;
        appMetrics.recordTime("app.template.order.process.duration", elapsed, "channel", order.getChannel());
    }
    
    public void processPayment(Payment payment) {
        // 方式四：自动计时（推荐）
        Timer.Sample sample = appMetrics.startTimer();
        try {
            doPayment(payment);
        } finally {
            appMetrics.stopTimer(sample, "app.template.payment.duration", "method", payment.getMethod());
        }
    }
    
    // 方式五：注册 Gauge（瞬时值），通常在初始化时调用一次
    @PostConstruct
    public void registerGauges() {
        appMetrics.gauge("app.template.order.pending", this, svc -> svc.getPendingOrderCount());
    }
}
```

### 3.2 命名规范

| 规则                                      | 示例                                    |
|-----------------------------------------|---------------------------------------|
| 统一前缀 `app.template.{module}.`           | `app.template.order.created`          |
| 使用小写、点号分隔                               | `app.template.payment.duration`       |
| Counter 不加 `_total` 后缀（Prometheus 自动追加） | `app.template.order.created`          |
| Timer 以 `.duration` 结尾                  | `app.template.order.process.duration` |

### 3.3 标签规范

| 规则             | 说明                                   |
|----------------|--------------------------------------|
| 标签值有限且可枚举      | `channel: ["web", "app", "api"]`     |
| **禁止高基数标签**    | 不用 userId、orderId、requestId、IP 等     |
| key-value 成对传入 | `"key1", "value1", "key2", "value2"` |
| 标签数量 <= 5 个    | 防止时间序列爆炸                             |

---

## 4. Prometheus 集成

### 4.1 Prometheus scrape 配置

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'app-template'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
    static_configs:
      - targets: [ 'your-app-host:8080' ]
    # 如果端点启用了认证
    # basic_auth:
    #   username: 'prometheus'
    #   password: 'your-password'
```

### 4.2 多实例 / Kubernetes

```yaml
scrape_configs:
  - job_name: 'app-template-k8s'
    kubernetes_sd_configs:
      - role: pod
    relabel_configs:
      - source_labels: [ __meta_kubernetes_pod_annotation_prometheus_io_scrape ]
        action: keep
        regex: true
      - source_labels: [ __meta_kubernetes_pod_annotation_prometheus_io_path ]
        action: replace
        target_label: __metrics_path__
        regex: (.+)
```

对应 Pod 注解：

```yaml
annotations:
  prometheus.io/scrape: "true"
  prometheus.io/path: "/actuator/prometheus"
  prometheus.io/port: "8080"
```

---

## 5. 常用 PromQL 查询

### 5.1 Flow 引擎

```promql
# 每秒处理量 (TPS)
rate(app_template_flow_terminated_total[1m])

# Job 成功率
app_template_flow_job_completed_total / app_template_flow_job_started_total

# 数据损耗率
sum(rate(app_template_flow_egress_passive_total[5m]))
  /
(sum(rate(app_template_flow_egress_active_total[5m])) + sum(rate(app_template_flow_egress_passive_total[5m])))

# 按 reason 分组的被动出口
sum by (reason) (rate(app_template_flow_egress_passive_total[5m]))

# 消费信号量利用率
app_template_flow_limits_consumer_concurrency_used / app_template_flow_limits_consumer_concurrency_limit

# P99 存储写入延迟
histogram_quantile(0.99, sum(rate(app_template_flow_deposit_duration_seconds_bucket[5m])) by (le))

# P99 配对处理延迟
histogram_quantile(0.99, sum(rate(app_template_flow_match_duration_seconds_bucket[5m])) by (le))

# 背压等待时间（平均）
rate(app_template_flow_backpressure_duration_seconds_sum[5m])
  /
rate(app_template_flow_backpressure_duration_seconds_count[5m])

# 按 phase 分组的错误率
sum by (phase) (rate(app_template_flow_errors_total[5m]))

# 存储堆积量（按 jobId）
app_template_flow_limits_storage_used
```

### 5.2 HTTP 认证

```promql
# 认证失败率（每分钟）
sum by (reason) (rate(app_template_http_auth_failure_total[5m]))

# 过期 Token 告警（5 分钟超 100 次）
sum(rate(app_template_http_auth_failure_total{reason="EXPIRED_TOKEN"}[5m])) > 100
```

### 5.3 OAuth2

```promql
# Token 签发速率
sum by (grantType) (rate(app_template_oauth2_token_issued_total[5m]))

# Token 签发失败率
sum(rate(app_template_oauth2_token_failed_total[5m]))
  /
(sum(rate(app_template_oauth2_token_issued_total[5m])) + sum(rate(app_template_oauth2_token_failed_total[5m])))

# 登出速率
rate(app_template_oauth2_logout_total[5m])
```

### 5.4 Dataforge

```promql
# CRUD 操作 P99 延迟
histogram_quantile(0.99,
  sum by (le, method) (rate(app_template_dataforge_request_duration_seconds_bucket[5m])))

# 按方法分组的错误率
sum by (method) (rate(app_template_dataforge_request_errors_total[5m]))
```

### 5.5 数据源

```promql
# Kafka/NATS 消息接收速率
sum by (sourceType) (rate(app_template_source_received_total[5m]))

# 数据源错误
sum by (sourceType, errorType) (rate(app_template_source_errors_total[5m]))

# NATS 待处理消息堆积
app_template_source_nats_pending_messages
```

---

## 6. 安全配置

Actuator 端点默认受 Spring Security 保护。需要在安全配置中放行 `/actuator/prometheus`。

### 6.1 通过 permitUrls 配置放行

在 `application.yml` 中添加：

```yaml
app:
  template:
    security:
      permit-urls:
        your-app-name:
          - /actuator/prometheus
          - /actuator/health
```

### 6.2 仅内网访问

建议通过网络策略限制 `/actuator/**` 仅允许 Prometheus 服务器 IP 访问，或配合 Spring Security 做 IP 白名单：

```yaml
management:
  server:
    port: 9090  # 使用独立端口暴露 Actuator
```

---

## 7. 高基数标签防护

高基数标签会导致 Prometheus 时间序列爆炸，严重影响性能和存储。

### 禁止用作标签的字段

| 字段                  | 原因       |
|---------------------|----------|
| userId              | 用户量可达百万级 |
| orderId / requestId | 每个请求唯一   |
| IP 地址               | 可变且数量不可控 |
| 完整 URL / 查询参数       | 变化无穷     |
| 时间戳                 | 每秒不同     |

### 安全的标签示例

| 标签           | 值域                                | 基数  |
|--------------|-----------------------------------|-----|
| `reason`     | TIMEOUT, EVICTION, REPLACE, ...   | ~6  |
| `phase`      | PRODUCTION, STORAGE, ...          | ~4  |
| `grantType`  | password, client_credentials, ... | ~5  |
| `sourceType` | kafka, nats                       | 2   |
| `method`     | create, update, delete, list, ... | ~10 |
| `jobId`      | 仅在任务数可控（< 50）时使用                  | 可控  |

---

## 8. FAQ

### Q: 切换到某 Instance（如 10.10.40.136:59306）后所有面板显示 "No data"？

**说明**：若切换到 10.10.20.62:59306 等实例有数据，则仪表板与查询逻辑正常，问题为**该实例本身**（如 10.10.40.136:59306）无数据或未正确抓取。

按以下顺序排查：

1. **目标 DOWN（最常见）**：`up{instance="..."}=0` 表示 Prometheus 抓取该实例失败，无法获取任何应用指标。需检查：实例是否存活、Prometheus 到实例的网络是否可达、`/actuator/prometheus` 是否可访问、防火墙/安全组是否放行。在 Prometheus Targets 页面可查看该 instance 的抓取状态和错误信息。

2. **Job 标签不匹配**：仪表板默认使用 `job="gopr-audit"`。若 Prometheus 抓取该实例时使用了不同的 job 名，查询会无结果。在 Prometheus 中执行 `up{instance="10.10.40.136:59306"}` 查看返回的 `job` 标签值，并在仪表板 Job 下拉框中选择对应 job。

3. **时间范围**：确认当前时间范围内该实例有数据（实例可能刚启动或已下线）。

4. **实例差异**：若其他 instance（如 10.10.20.62:59306）有数据，说明问题限于该实例，需检查该实例的部署、网络或抓取配置。

### Q: Grafana 中「数据出缓存原因分布」显示 "No data"？

可能原因：

1. **无被动出口**：该指标依赖 `app_template_flow_egress_passive_total`，仅在发生被动出口（TIMEOUT、EVICTION、REPLACE、MISMATCH、REJECT、SHUTDOWN）时才有数据。若数据全部正常消费完成，则无此指标。

2. **jobId 变量格式**：多选 jobId 时，需使用 `${jobId:pipe}` 才能正确展开为正则（如 `id1|id2`）。若使用 `$jobId`，多选会展开为 `id1,id2`，正则无法匹配。可在面板查询中改为：
   ```promql
   sum by (reason, jobId) (rate(app_template_flow_egress_passive_total{jobId=~"${jobId:pipe}"}[5m]))
   ```

3. **时间窗口**：`rate(...[5m])` 需要过去 5 分钟内有增量。若被动出口很少，rate 可能接近 0 或为空。

### Q: Grafana 中 HTTP 响应时间 (P99/P95/P50) 显示 "No data"？

P99/P95/P50 依赖 `http_server_requests_seconds_bucket` 直方图指标。Spring Boot 默认只暴露 `_count` 和 `_sum`（用于计算平均值），不暴露 `_bucket`。

在 `application.yml` 中启用直方图：

```yaml
management:
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
```

重启应用后，Prometheus 抓取新数据，等待约 5 分钟（与 Grafana 查询的 `[5m]` 窗口一致）即可看到 P99/P95/P50 曲线。

### Q: 引入框架后没有看到 `/actuator/prometheus` 端点？

确认以下几点：

1. `application.yml` 中 `management.endpoints.web.exposure.include` 包含 `prometheus`
2. classpath 中有 `micrometer-registry-prometheus` 依赖
3. 端点未被 Spring Security 拦截（参考第 6 节）

### Q: 指标名称中的 `.` 在 Prometheus 中变成了 `_`？

这是 Micrometer 的标准行为。代码中使用 `app.template.flow.job.started`，在 Prometheus 中显示为
`app_template_flow_job_started_total`（Counter 自动追加 `_total`）。

### Q: 如何查看 Flow 引擎的健康状态？

访问 `/actuator/health`，响应中包含 `flowHealth` 组件，状态分为 UP（健康）、DEGRADED（降级）、DOWN（异常）。

### Q: KafkaFlowSource / NatsFlowSource 如何开启指标？

使用四参数构造器，传入 `MeterRegistry` 实例：

```java

@Autowired
MeterRegistry meterRegistry;

KafkaFlowSource<String> source = new KafkaFlowSource<>(consumer, mapper, Duration.ofSeconds(1), meterRegistry);
```

三参数构造器（不传 MeterRegistry）保持向后兼容，不采集指标。

### Q: 自定义指标应该用什么前缀？

统一使用 `app.template.{你的模块名}.` 作为前缀。例如：

- 订单模块：`app.template.order.created`
- 支付模块：`app.template.payment.duration`
- 通知模块：`app.template.notification.sent`
