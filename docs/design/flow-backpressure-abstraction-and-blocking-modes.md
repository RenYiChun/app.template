## Flow 引擎多维背压抽象与阻塞策略设计

### 1. 背景与目标

当前 Flow 引擎已经支持 5 个维度的背压控制：

- 在途生产数据数量（in-flight production）
- 生产线程并发数量（producer concurrency）
- 缓存容量（storage capacity）
- 在途消费数据数量（in-flight / pending consumer）
- 消费线程数量（consumer concurrency）

这些逻辑目前分布在：

- 生产侧：`FlowLauncher`
- 消费侧：`Orchestrator`
- 存储侧：`FlowStorage` 实现（如 `BoundedTimedFlowStorage`）
- 下游压力评估：`DownstreamPressureEvaluator` 与受控超时逻辑

本设计的目标：

- 抽象出统一的背压控制入口，使生产/消费代码不感知底层细节；
- 通过配置控制“**一直阻塞**”和“**阻塞一段时间即超时**”两种模式；
- 为后续扩展更多维度背压策略预留清晰扩展点。

---

### 2. 配置设计：阻塞模式与超时

在 `TemplateConfigProperties.Flow` 下新增以下配置（全局 Flow 级别，对所有 Job 生效）：

```java
public static class Flow {
    // ...

    public enum BackpressureBlockingMode {
        /** 一直阻塞直到条件满足，仅能被 Job 停止打断。 */
        BLOCK_FOREVER,
        /** 背压等待超过配置的超时时间后抛出超时异常。 */
        BLOCK_WITH_TIMEOUT
    }

    /** 生产端发生背压时的阻塞模式。 */
    private BackpressureBlockingMode producerBackpressureBlockingMode =
            BackpressureBlockingMode.BLOCK_WITH_TIMEOUT;

    /** 生产端背压时允许的最长等待时间（毫秒），仅在 BLOCK_WITH_TIMEOUT 模式下生效。 */
    private long producerBackpressureTimeoutMill = 30_000L;

    /** 消费端获取消费许可时的阻塞模式。 */
    private BackpressureBlockingMode consumerAcquireBlockingMode =
            BackpressureBlockingMode.BLOCK_WITH_TIMEOUT;

    /** 消费端获取消费许可时允许的最长等待时间（毫秒），仅在 BLOCK_WITH_TIMEOUT 模式下生效。 */
    private long consumerAcquireTimeoutMill = 30_000L;
}
```

在配置校验中增加约束：

- 当模式为 `BLOCK_WITH_TIMEOUT` 时，对应超时时长必须 `> 0`。

即：

- 批处理类 Job 可以设置为 `BLOCK_FOREVER`，在高压下长期阻塞但不轻易失败；
- 在线请求类 Job 则设置为 `BLOCK_WITH_TIMEOUT`，配合较短的超时时间实现“失败快”。

---

### 3. BackpressureController：统一生产侧背压入口

`BackpressureController` 是生产端在写入存储前的统一背压控制器，其职责：

- 判断存储是否已满（包括受控超时存储）；
- 在满载时按照配置阻塞当前生产线程；
- 记录背压持续时间指标。

核心改动：

```java
public class BackpressureController {
    private final FlowStorage<?> flowStorage;
    private final MeterRegistry meterRegistry;
    private final String jobId;
    private final TemplateConfigProperties.Flow flowConfig;

    public BackpressureController(FlowStorage<?> flowStorage,
            MeterRegistry meterRegistry,
            String jobId,
            TemplateConfigProperties.Flow flowConfig) {
        this.flowStorage = flowStorage;
        this.meterRegistry = meterRegistry;
        this.jobId = jobId;
        this.flowConfig = flowConfig;
    }

    /** 生产者调用：缓存满或消费许可耗尽时阻塞 */
    public void awaitSpace(BooleanSupplier stopCheck) throws InterruptedException, TimeoutException {
        long maxWaitMs =
                flowConfig.getProducerBackpressureBlockingMode()
                      == TemplateConfigProperties.Flow.BackpressureBlockingMode.BLOCK_WITH_TIMEOUT
                        ? flowConfig.getProducerBackpressureTimeoutMill()
                        : 0L; // 0 表示不限制
        awaitSpace(stopCheck, maxWaitMs);
    }

    // awaitSpace(stopCheck, maxWaitMs) 内部逻辑保持不变：
    // - 当 maxWaitMs > 0 时，累计等待超过该值抛出 TimeoutException；
    // - 当 maxWaitMs == 0 时，不做总等待超时判断，仅依赖 stopCheck 打断。
}
```

行为语义：

- `BLOCK_FOREVER`：`maxWaitMs = 0`，生产者在存储满时会一直阻塞，直到：
  - 存储释放空间，或
  - Job 被停止（`stopCheck` 返回 true）。
- `BLOCK_WITH_TIMEOUT`：`maxWaitMs = producerBackpressureTimeoutMill`，超过该时长仍无空间则抛 `TimeoutException`。

---

### 4. Orchestrator：消费端并发获取策略

`Orchestrator.acquire()` 负责消费端获取消费许可，其超时行为也切换为配置驱动：

```java
public void acquire() throws InterruptedException, TimeoutException {
    long acquireStartTime = System.currentTimeMillis();
    long acquireStartNanos = System.nanoTime();
    var flowConfig = registration.getFlow();
    var mode = flowConfig.getConsumerAcquireBlockingMode();
    long acquireTimeoutMs =
            mode == TemplateConfigProperties.Flow.BackpressureBlockingMode.BLOCK_WITH_TIMEOUT
                    ? flowConfig.getConsumerAcquireTimeoutMill()
                    : 0L;
    long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(acquireTimeoutMs);
    // 后续公平等待与 PermitPair.tryAcquireBoth 的逻辑保持不变，只是使用 acquireTimeoutMs
}
```

语义：

- `BLOCK_FOREVER`：`acquireTimeoutMs = 0`，不再做整体超时控制，只要 PermitPair 支持阻塞获取，就一直等待；
- `BLOCK_WITH_TIMEOUT`：`acquireTimeoutMs > 0`，整体等待超时则抛 `TimeoutException`。

---

### 5. In-flight 生产许可的阻塞策略

在 `FlowLauncher.launch` 中，in-flight 生产许可获取同样使用 per-job 配置：

```java
PermitPair inFlightPermitPair = resourceContext.getInFlightPermitPair();
if (inFlightPermitPair != null) {
    try {
        Timer.Sample inFlightSample = Timer.start(registry());
        TemplateConfigProperties.Flow.BackpressureBlockingMode mode =
                flow.getProducerBackpressureBlockingMode();
        long acquireTimeoutMs =
                mode == TemplateConfigProperties.Flow.BackpressureBlockingMode.BLOCK_WITH_TIMEOUT
                        ? flow.getProducerBackpressureTimeoutMill()
                        : 0L;
        boolean acquired = inFlightPermitPair.tryAcquireBoth(1,
                acquireTimeoutMs,
                TimeUnit.MILLISECONDS
        );
        inFlightSample.stop(...);
        if (!acquired) {
            // 记录日志与指标，视为生产端 in-flight 背压超时
            // ...
        }
    } catch (InterruptedException e) {
        // 中断处理逻辑保持不变
    }
}
```

这样，**在途生产数量**这一维度的阻塞/超时语义与存储容量维度保持一致，统一由 `producerBackpressureBlockingMode` 和 `producerBackpressureTimeoutMill` 控制。

---

### 6. 多维背压抽象与扩展点

虽然当前实现主要通过存储占用和 in-flight / consumer semaphore 来体现 5 维背压，但在结构上已经实现解耦：

- **生产侧背压入口**：集中在 `BackpressureController.awaitSpace`；
- **消费侧并发控制**：集中在 `Orchestrator.acquire`；
- **在途生产/消费限制**：通过 `FlowResourceContext` 中的 `PermitPair` 管理。

下一步可选增强（设计层面已预留）：

1. 引入 `BackpressureSnapshot`，聚合 5 维度实时状态；
2. 引入 `BackpressurePolicy`，根据快照决策 `PROCEED/BLOCK/TIMEOUT/DROP` 等；
3. 让 `BackpressureController` / `Orchestrator` 依赖策略而非直接判断 semaphore 与存储大小。

通过以上结构，背压逻辑已经从“散落在各处的 if/while 判断”收敛到少量集中位置，**阻塞或超时行为完全由配置驱动**，便于后续扩展和维护。
