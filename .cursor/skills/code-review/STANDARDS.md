# 编码标准

本项目遵循 Google Java Style（见 `checker/checkStyle.xml`）。

## 错误处理

```java
// ❌ BAD - 吞掉异常
try {
    doSomething();
} catch (Exception e) {}

// ✅ GOOD - 记录并考虑重新抛出
try {
    doSomething();
} catch (Exception e) {
    logger.error("Failed to do something", e);
    throw new BusinessException("Description", e);
}
```

## 命名

- 类名：大驼峰 `UserService`
- 方法/变量：小驼峰 `getUserById`
- 常量：全大写下划线 `MAX_RETRY_COUNT`
- 包名：小写 `com.lrenyi.template`

## 类与方法

- 单行不超过 140 字符（Checkstyle 配置）
- 方法建议不超过 50 行
- 类职责单一，避免「上帝类」
- 避免 `AvoidStarImport`，使用明确 import

## 设计原则

- **单一职责**：一个类只做一类事
- **开闭原则**：对扩展开放，对修改关闭
- **KISS**：保持简单，避免过度抽象
- **复用**：优先使用现有工具类/服务，避免重复实现
