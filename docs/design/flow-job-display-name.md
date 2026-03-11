# Flow 引擎 Job 显示名映射方案

## 背景

当前 Flow 引擎的 `jobId` 是业务传入的字符串（如 UUID、业务主键等），在 Grafana 等监控中直接作为 `jobId` 标签展示，对运维人员不够友好。需要支持将业务 jobId 映射为易读的显示名（如「订单匹配」「对账任务-20250110」），用于监控指标展示。

## 设计目标

1. **业务 jobId 不变**：内部逻辑、日志、存储仍使用原始 jobId
2. **监控友好**：指标标签使用可读的显示名
3. **显式注册**：启动时通过 `createLauncher(jobId, displayName, ...)` 传入显示名
4. **零侵入可选**：未配置时行为与现有一致

## 方案概述

采用 **仅显式注册** 方式，在创建 Launcher 时通过 `displayName` 参数注册，指标注册时使用显示名作为 `jobId` 标签值。

### 核心组件

```
┌─────────────────────────────────────────────────────────────────┐
│  FlowManager                                                     │
│  └── jobIdToDisplayName Map (显式注册，createLauncher 时写入)       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  createLauncher(jobId) / createLauncher(jobId, displayName)      │
│  → metricJobId = displayName ?? jobId                            │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  DimensionContext / FlowLauncher / BackpressureManager           │
│  → 指标注册时使用 metricJobId 作为 TAG_JOB_ID 的值                 │
└─────────────────────────────────────────────────────────────────┘
```

## 详细设计

### 1. 注册方式

| 方式 | 适用场景 | 示例 |
|------|----------|------|
| **显式 displayName** | 启动时已知展示名 | `createLauncher("uuid-xxx", "订单匹配", ...)` |
| **原样 jobId** | 未传 displayName | `createLauncher("uuid-xxx", ...)` |

### 2. API 变更

#### 2.1 FlowManager

```java
// 新增：创建 Launcher 时显式传入显示名
<T> FlowLauncher<T> createLauncher(String jobId,
                                    String displayName,  // 新增，可 null
                                    FlowJoiner<T> flowJoiner,
                                    ProgressTracker tracker,
                                    TemplateConfigProperties.Flow flowConfig);

// 保留：原 createLauncher(jobId, ...) 重载，displayName 传 null
```

#### 2.2 使用示例

```java
// 显式注册：启动时传入展示名
flowManager.createLauncher("order-match-20250110-abc", "订单匹配", joiner, tracker, flow);

// 不配置：使用 jobId 原样
flowManager.createLauncher("uuid-xxx", joiner, tracker, flow);
```

### 3. 内部实现要点

#### 3.1 解析逻辑（FlowManager）

```java
String resolveMetricJobId(String jobId) {
    String explicit = jobIdToDisplayName.get(jobId);
    return explicit != null ? explicit : jobId;
}
```

#### 3.2 传递链路

- `FlowManager.createLauncher` 解析得到 `metricJobId`
- `FlowLauncherFactory.create` 接收 `(jobId, metricJobId)`
- `DimensionContext.builder().jobId(jobId).metricJobId(metricJobId)` 
- `BackpressureManager`、`FlowLauncher`、`FlowFinalizer`、`DefaultProgressTracker` 在注册指标时使用 `metricJobId`

#### 3.3 指标注册处修改

所有 `.tag(TAG_JOB_ID, xxx)` 改为使用 `getMetricJobId()`：

- `DimensionContext.getMetricJobId()`：返回 metricJobId，null 时用 jobId
- `FlowLauncher`：持有 metricJobId，供 deposit timer 等使用
- `FlowFinalizer`：从 launcher 取 metricJobId
- `DefaultProgressTracker`：构造时传入 metricJobId

### 4. 生命周期

- **注册**：`createLauncher(jobId, displayName)` 时写入 `jobIdToDisplayName`
- **注销**：`unregister(jobId)` 时从 map 移除，避免内存泄漏

### 5. 兼容性

- 不传 displayName：行为与当前完全一致
- 现有 `createLauncher(jobId, ...)` 调用方无需修改
- 新增重载 `createLauncher(jobId, displayName, ...)` 为可选参数

### 6. 注意事项

1. **标签基数**：displayName 应控制枚举数量，避免高基数（如不要用「订单-{orderId}」）
2. **日志关联**：日志仍用原始 jobId，便于与业务排查；监控用 displayName 便于看板
3. **Grafana**：legend 使用 `{{jobId}}` 即可展示友好名

## 实现清单

- [x] `FlowManager`：`jobIdToDisplayName` Map、`resolveMetricJobId`
- [x] `FlowManager`：`createLauncher(jobId, displayName, ...)` 重载
- [x] `DimensionContext`：新增 `metricJobId` 字段
- [x] `FlowLauncherFactory`：接收并传递 metricJobId
- [x] `BackpressureManager`：使用 metricJobId 注册指标
- [x] 各 `ResourceBackpressureDimension`：`ctx.getMetricJobIdForTags()`
- [x] `FlowLauncher`、`FlowFinalizer`、`DefaultProgressTracker`：使用 metricJobId
- [x] `unregister` 时清理 `jobIdToDisplayName`
- [x] 单元测试与集成测试
