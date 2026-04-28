# Flow 快速开始

本文档只回答一件事：第一次接入 `template-flow` 时，怎样尽快跑通一个 Flow Job。

## 1. 环境

- Java 21+
- Maven 3.6+

## 2. 引入依赖

推荐在新项目中继承 `template-dependencies`，然后按需引入 `template-flow`。

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.lrenyi</groupId>
        <artifactId>template-dependencies</artifactId>
        <version>2.5.2-SNAPSHOT</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>flow-demo</artifactId>
    <version>1.0.0</version>

    <dependencies>
        <dependency>
            <groupId>com.lrenyi</groupId>
            <artifactId>template-core</artifactId>
        </dependency>
        <dependency>
            <groupId>com.lrenyi</groupId>
            <artifactId>template-flow</artifactId>
        </dependency>

        <!-- 需要 Kafka / NATS / 分页 API Source 时再按需引入 -->
        <!--
        <dependency>
            <groupId>com.lrenyi</groupId>
            <artifactId>template-flow-sources-kafka</artifactId>
        </dependency>
        -->
    </dependencies>
</project>
```

## 3. 最小配置

Flow 的默认值已经能运行。第一次接入建议只保留总开关和一组易于理解的 limit。

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

完整配置项见 [Flow 配置参考](config-reference.md)。

## 4. 最小 Pull 示例

下面的示例展示单流拉取。核心只有 4 个对象：

- `FlowJoiner<T>`：定义 joinKey、消费回调和 sourceProvider
- `FlowManager`：管理 Job 生命周期
- `FlowJoinerEngine`：启动 Flow Job
- `FlowSource<T>`：实际数据流

```java
import java.util.List;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowSource;
import com.lrenyi.template.flow.api.FlowSourceAdapters;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.engine.FlowJoinerEngine;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.model.EgressReason;

record DemoItem(String id, String value) {}

class DemoJoiner implements FlowJoiner<DemoItem> {
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
        // 单流覆盖场景可留空
    }

    @Override
    public void onSingleConsumed(DemoItem item, String jobId, EgressReason reason) {
        System.out.println("jobId=" + jobId + ", item=" + item + ", reason=" + reason);
    }
}

TemplateConfigProperties.Flow flowConfig = new TemplateConfigProperties.Flow();
FlowManager manager = FlowManager.getInstance(flowConfig);
FlowJoinerEngine engine = new FlowJoinerEngine(manager);

List<DemoItem> items = List.of(
        new DemoItem("k1", "v1"),
        new DemoItem("k2", "v2")
);

FlowSource<DemoItem> source = FlowSourceAdapters.fromIterator(items.iterator(), null);
engine.run("demo-pull", new DemoJoiner(), source, items.size(), flowConfig);

ProgressTracker tracker = engine.getProgressTracker("demo-pull");
while (!tracker.isCompleted(true)) {
    Thread.sleep(50);
}
System.out.println(tracker.getSnapshot());
```

## 5. 最小 Push 示例

Push 模式适合“数据已经在业务侧被消费到手里，再交给 Flow”的场景。

```java
import java.util.List;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowInlet;
import com.lrenyi.template.flow.engine.FlowJoinerEngine;
import com.lrenyi.template.flow.manager.FlowManager;

TemplateConfigProperties.Flow flowConfig = new TemplateConfigProperties.Flow();
FlowManager manager = FlowManager.getInstance(flowConfig);
FlowJoinerEngine engine = new FlowJoinerEngine(manager);

FlowInlet<DemoItem> inlet = engine.startPush("demo-push", new DemoJoiner(), flowConfig);
for (DemoItem item : List.of(new DemoItem("k1", "v1"), new DemoItem("k2", "v2"))) {
    inlet.push(item);
}
inlet.markSourceFinished();

while (!inlet.isCompleted()) {
    Thread.sleep(50);
}
System.out.println(inlet.getProgressTracker().getSnapshot());
```

## 6. 如何判断已经跑通

满足下面 3 点，就可以认为最小接入成功：

1. `onSingleConsumed(...)` 或 `onPairConsumed(...)` 被实际触发。
2. `ProgressTracker.isCompleted(true)` 或 `FlowInlet.isCompleted()` 最终返回 `true`。
3. `FlowProgressSnapshot` 中 `terminated` 大于 0，且 `inStorage`、`activeConsumers` 最终回到 0。

## 7. 常见接入错误

### 7.1 用错配置键

当前真实配置键是：

- `producer-threads`
- `consumer-threads`
- `storage-capacity`
- `queue-poll-interval-mill`
- `keyed-cache.cache-ttl-mill`

不要照抄历史旧文档里的已移除配置键。

### 7.2 只启动了 Push，但没调用 `markSourceFinished()`

Push 模式下如果没有声明输入结束，任务通常不会进入完成态。

### 7.3 误以为 `FlowInlet` 或 `ProgressTracker` 提供完成 Future

当前公开 API 没有 `getCompletionFuture()`。完成判定用：

- `FlowInlet.isCompleted()`
- `ProgressTracker.isCompleted(boolean showStatus)`

## 8. 下一步

- 需要理解运行模型和边界行为：看 [Flow 使用指导](../guides/flow-usage-guide.md)
- 需要调整背压和 TTL：看 [Flow 配置参考](config-reference.md)
- 需要接 Kafka / NATS / 分页 Source：看 `template-flow-sources` 对应模块
