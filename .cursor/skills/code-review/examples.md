# 审查示例

## 示例 1：空 catch 块

**代码：**

```java
try {
    config.load(inputStream);
} catch (IOException e) {}
```

**反馈：**
🔴 **Critical**：吞掉异常，无法排查问题

**建议：**

```java
try {
    config.load(inputStream);
} catch (IOException e) {
    throw new ConfigLoadException("Failed to load config", e);
}
```

## 示例 2：方法过长

**反馈：**
🟡 **Suggestion**：`processOrder` 方法超过 80 行，建议拆分为 `validateOrder`、`calculateTotal`、`persistOrder` 等小方法。

## 示例 3：重复代码

**反馈：**
🟡 **Suggestion**：`UserService` 和 `AdminService` 中均有相似的权限校验逻辑，建议抽到 `PermissionChecker` 复用。
