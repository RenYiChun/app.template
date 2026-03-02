# 计划：使用 JDK 21 特性现代化代码库

本计划旨在通过利用目前尚未充分使用的 JDK 21 特性（重点关注序列集合、记录类和模式匹配）来现代化代码库。

## 1. `RbacDataInitializer` 中的序列集合 (Sequenced Collections)

**文件：** `template-dataforge-sample-backend/src/main/java/com/lrenyi/template/dataforge/backend/init/RbacDataInitializer.java`

* **现状：** 使用 `list.get(0)` 访问第一个元素。

* **现代化改进：** 替换为 `list.getFirst()`，利用 Java 21 提供的 `SequencedCollection` 接口，提高代码可读性和意图表达。

## 2. `AuditLogService` 中的记录类 (Records)

**文件：** `template-dataforge/src/main/java/com/lrenyi/template/dataforge/audit/service/AuditLogService.java`

* **现状：** `RecordParams` 是一个静态内部类，包含 final 字段、私有构造函数和 Builder。这是一种典型的“数据载体”模式。

* **现代化改进：** 将 `RecordParams` 转换为 Java `record`。

  * 减少样板代码（无需手动声明 final 字段和编写构造函数赋值）。

  * 在 record 内部保留 Builder 作为静态内部类，以维持调用方现有的流畅 API 风格。

  * `record` 语法将自动处理数据存储和访问器（例如 `name()` 而非 `getName()`，虽然原始类字段是私有的，但 record 公开了访问器，且作为静态内部类 `AuditLogService` 可直接访问其成员，这完全可行）。

## 3. `GenericEntityController` 中的模式匹配与流 (Pattern Matching & Streams)

**文件：** `template-dataforge/src/main/java/com/lrenyi/template/dataforge/controller/GenericEntityController.java`

* **现状（强制转换）：** 在获取 mapper 时使用了显式强制转换并带有 `@SuppressWarnings("unchecked")`。

  * `BaseMapper<Object, ?, ?, ?, Object> mapper = (BaseMapper<Object, ?, ?, ?, Object>) info.mapper();`

* **现代化改进：** 使用 `instanceof` 模式匹配一步完成安全转换和变量赋值，减少代码噪声和未检查警告。

  * `if (info.mapper() instanceof BaseMapper<?, ?, ?, ?, ?> mapper)`

* **现状（循环）：** `buildPageable` 使用 `foreach` 循环构建 `Sort.Order` 列表。

* **现代化改进：** 重构 `buildPageable`，使用 `Stream.map` 和 `toList()`，采用更函数式、更简洁的方法。

## 4. 验证

* 编译项目以确保无语法错误。

* 验证更改不会破坏现有功能（静态分析）。

## 待办事项列表

* [ ] 更新 `RbacDataInitializer.java` 使用 `getFirst()`。

* [ ] 重构 `AuditLogService.RecordParams` 为 `record`。

* [ ] 重构 `GenericEntityController.java` 使用 `instanceof` 模式匹配和流式处理 `buildPageable`。

